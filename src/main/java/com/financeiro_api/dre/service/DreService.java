package com.financeiro_api.Dre.service;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.DreCategoria;
import com.financeiro_api.Categorias.repository.CategoriaRepository;
import com.financeiro_api.Dre.dto.DreCategoriaTotalDTO;
import com.financeiro_api.Dre.dto.DreLinhaDTO;
import com.financeiro_api.Dre.dto.DreResponseDTO;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public DreResponseDTO calcular(int mes, int ano) {
        UUID tenantId = TenantContext.get();

        List<Extrato> extratos = extratoRepository
                .findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(tenantId, mes, ano);

        Map<UUID, Categoria> categorias = categoriaRepository.findAllByEnterpriseId(tenantId)
                .stream().collect(Collectors.toMap(Categoria::getId, c -> c));

        Map<DreCategoria, BigDecimal> somaPorDre = new EnumMap<>(DreCategoria.class);
        Map<DreCategoria, LinkedHashMap<String, BigDecimal>> valorPorCatDre = new EnumMap<>(DreCategoria.class);

        for (Extrato e : extratos) {
            if (e.getCategoriaId() == null) continue;
            Categoria cat = categorias.get(e.getCategoriaId());
            if (cat == null || cat.getDreCategoria() == null) continue;

            somaPorDre.merge(cat.getDreCategoria(), e.getValor(), BigDecimal::add);
            valorPorCatDre.computeIfAbsent(cat.getDreCategoria(), k -> new LinkedHashMap<>())
                    .merge(cat.getName(), e.getValor(), BigDecimal::add);
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
                linha("(+) Receita Bruta",            receitaBruta,    false, valorPorCatDre, DreCategoria.RECEITA_BRUTA),
                linha("(-) Deduções da Receita",      deducoesReceita, false, valorPorCatDre, DreCategoria.DEDUCAO_RECEITA),
                subtotal("(=) Receita Líquida",       receitaLiquida),
                linha("(-) Custo dos Produtos/Serv.", cpv,             false, valorPorCatDre, DreCategoria.CPV),
                subtotal("(=) Lucro Bruto",           lucroBruto),
                linha("(-) Despesas de Vendas",       despVendas,      false, valorPorCatDre, DreCategoria.DESPESA_VENDAS),
                linha("(-) Despesas Administrativas", despAdmin,       false, valorPorCatDre, DreCategoria.DESPESA_ADMINISTRATIVA),
                subtotal("(=) EBITDA",                ebitda),
                linha("(-) Despesas Financeiras",     despFinanceiras, false, valorPorCatDre, DreCategoria.DESPESA_FINANCEIRA),
                linha("(+) Receitas Financeiras",     recFinanceiras,  false, valorPorCatDre, DreCategoria.RECEITA_FINANCEIRA),
                subtotal("(=) LAIR",                  lair),
                linha("(-) Impostos (IR/CSLL)",       impostos,        false, valorPorCatDre, DreCategoria.IMPOSTO),
                subtotal("(=) Lucro Líquido",         lucroLiquido)
        );

        return new DreResponseDTO(mes, ano, linhas,
                receitaBruta, deducoesReceita, receitaLiquida,
                cpv, lucroBruto, despesasOperac, ebitda,
                despFinanceiras, recFinanceiras, lair, impostos, lucroLiquido);
    }

    private DreLinhaDTO linha(String label, BigDecimal valor, boolean ehSubtotal,
                               Map<DreCategoria, LinkedHashMap<String, BigDecimal>> valorPorCatDre,
                               DreCategoria chave) {
        List<DreCategoriaTotalDTO> cats = valorPorCatDre.containsKey(chave)
                ? valorPorCatDre.get(chave).entrySet().stream()
                        .map(e -> new DreCategoriaTotalDTO(e.getKey(), e.getValue().abs()))
                        .toList()
                : List.of();
        return new DreLinhaDTO(label, valor, ehSubtotal, cats);
    }

    private DreLinhaDTO subtotal(String label, BigDecimal valor) {
        return new DreLinhaDTO(label, valor, true, List.of());
    }

    private BigDecimal get(Map<DreCategoria, BigDecimal> mapa, DreCategoria chave) {
        return mapa.getOrDefault(chave, BigDecimal.ZERO).abs();
    }
}
