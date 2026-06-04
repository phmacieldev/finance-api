package com.financeiro_api.Dre.service;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.DreCategoria;
import com.financeiro_api.Categorias.repository.CategoriaRepository;
import com.financeiro_api.Dre.dto.DreLinhaDTO;
import com.financeiro_api.Dre.dto.DreResponseDTO;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DreService {

    private final ExtratoRepository extratoRepository;
    private final CategoriaRepository categoriaRepository;

    public DreService(ExtratoRepository extratoRepository, CategoriaRepository categoriaRepository) {
        this.extratoRepository = extratoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public DreResponseDTO calcular(int mes, int ano) {
        UUID tenantId = TenantContext.get();

        List<Extrato> extratos = extratoRepository
                .findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(tenantId, mes, ano);

        Map<UUID, Categoria> categorias = categoriaRepository.findAllByEnterpriseId(tenantId)
                .stream().collect(Collectors.toMap(Categoria::getId, c -> c));

        Map<DreCategoria, BigDecimal> somaPorDre = new EnumMap<>(DreCategoria.class);
        Map<DreCategoria, Set<String>> nomesPorDre = new EnumMap<>(DreCategoria.class);

        for (Extrato e : extratos) {
            if (e.getCategoriaId() == null) continue;
            Categoria cat = categorias.get(e.getCategoriaId());
            if (cat == null || cat.getDreCategoria() == null) continue;

            BigDecimal valorAbs = e.getValor().abs();
            somaPorDre.merge(cat.getDreCategoria(), valorAbs, BigDecimal::add);
            nomesPorDre.computeIfAbsent(cat.getDreCategoria(), k -> new LinkedHashSet<>()).add(cat.getName());
        }

        BigDecimal receitaBruta       = get(somaPorDre, DreCategoria.RECEITA_BRUTA);
        BigDecimal deducoesReceita    = get(somaPorDre, DreCategoria.DEDUCAO_RECEITA);
        BigDecimal receitaLiquida     = receitaBruta.subtract(deducoesReceita);
        BigDecimal cpv                = get(somaPorDre, DreCategoria.CPV);
        BigDecimal lucroBruto         = receitaLiquida.subtract(cpv);
        BigDecimal despVendas         = get(somaPorDre, DreCategoria.DESPESA_VENDAS);
        BigDecimal despAdmin          = get(somaPorDre, DreCategoria.DESPESA_ADMINISTRATIVA);
        BigDecimal despesasOperac     = despVendas.add(despAdmin);
        BigDecimal ebitda             = lucroBruto.subtract(despesasOperac);
        BigDecimal despFinanceiras    = get(somaPorDre, DreCategoria.DESPESA_FINANCEIRA);
        BigDecimal recFinanceiras     = get(somaPorDre, DreCategoria.RECEITA_FINANCEIRA);
        BigDecimal lair               = ebitda.subtract(despFinanceiras).add(recFinanceiras);
        BigDecimal impostos           = get(somaPorDre, DreCategoria.IMPOSTO);
        BigDecimal lucroLiquido       = lair.subtract(impostos);

        List<DreLinhaDTO> linhas = List.of(
                linha("(+) Receita Bruta",            receitaBruta,    false, nomesPorDre, DreCategoria.RECEITA_BRUTA),
                linha("(-) Deduções da Receita",      deducoesReceita, false, nomesPorDre, DreCategoria.DEDUCAO_RECEITA),
                subtotal("(=) Receita Líquida",       receitaLiquida),
                linha("(-) Custo dos Produtos/Serv.", cpv,             false, nomesPorDre, DreCategoria.CPV),
                subtotal("(=) Lucro Bruto",           lucroBruto),
                linha("(-) Despesas de Vendas",       despVendas,      false, nomesPorDre, DreCategoria.DESPESA_VENDAS),
                linha("(-) Despesas Administrativas", despAdmin,       false, nomesPorDre, DreCategoria.DESPESA_ADMINISTRATIVA),
                subtotal("(=) EBITDA",                ebitda),
                linha("(-) Despesas Financeiras",     despFinanceiras, false, nomesPorDre, DreCategoria.DESPESA_FINANCEIRA),
                linha("(+) Receitas Financeiras",     recFinanceiras,  false, nomesPorDre, DreCategoria.RECEITA_FINANCEIRA),
                subtotal("(=) LAIR",                  lair),
                linha("(-) Impostos (IR/CSLL)",       impostos,        false, nomesPorDre, DreCategoria.IMPOSTO),
                subtotal("(=) Lucro Líquido",         lucroLiquido)
        );

        return new DreResponseDTO(mes, ano, linhas,
                receitaBruta, deducoesReceita, receitaLiquida,
                cpv, lucroBruto, despesasOperac, ebitda,
                despFinanceiras, recFinanceiras, lair, impostos, lucroLiquido);
    }

    private DreLinhaDTO linha(String label, BigDecimal valor, boolean ehSubtotal,
                               Map<DreCategoria, Set<String>> nomesPorDre, DreCategoria chave) {
        List<String> cats = nomesPorDre.containsKey(chave)
                ? new ArrayList<>(nomesPorDre.get(chave))
                : List.of();
        return new DreLinhaDTO(label, valor, ehSubtotal, cats);
    }

    private DreLinhaDTO subtotal(String label, BigDecimal valor) {
        return new DreLinhaDTO(label, valor, true, List.of());
    }

    private BigDecimal get(Map<DreCategoria, BigDecimal> mapa, DreCategoria chave) {
        return mapa.getOrDefault(chave, BigDecimal.ZERO);
    }
}
