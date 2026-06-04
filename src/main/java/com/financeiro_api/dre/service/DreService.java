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

        // Agrupa soma de valor absoluto por DreCategoria
        Map<DreCategoria, BigDecimal> somaPorDre = new EnumMap<>(DreCategoria.class);

        for (Extrato e : extratos) {
            if (e.getCategoriaId() == null) continue;
            Categoria cat = categorias.get(e.getCategoriaId());
            if (cat == null || cat.getDreCategoria() == null) continue;

            BigDecimal valorAbs = e.getValor().abs();
            somaPorDre.merge(cat.getDreCategoria(), valorAbs, BigDecimal::add);
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
                new DreLinhaDTO("(+) Receita Bruta",            receitaBruta,       false),
                new DreLinhaDTO("(-) Deduções da Receita",       deducoesReceita,    false),
                new DreLinhaDTO("(=) Receita Líquida",           receitaLiquida,     true),
                new DreLinhaDTO("(-) Custo dos Produtos/Serv.",  cpv,                false),
                new DreLinhaDTO("(=) Lucro Bruto",               lucroBruto,         true),
                new DreLinhaDTO("(-) Despesas de Vendas",        despVendas,         false),
                new DreLinhaDTO("(-) Despesas Administrativas",  despAdmin,          false),
                new DreLinhaDTO("(=) EBITDA",                    ebitda,             true),
                new DreLinhaDTO("(-) Despesas Financeiras",      despFinanceiras,    false),
                new DreLinhaDTO("(+) Receitas Financeiras",      recFinanceiras,     false),
                new DreLinhaDTO("(=) LAIR",                      lair,               true),
                new DreLinhaDTO("(-) Impostos (IR/CSLL)",        impostos,           false),
                new DreLinhaDTO("(=) Lucro Líquido",             lucroLiquido,       true)
        );

        return new DreResponseDTO(mes, ano, linhas,
                receitaBruta, deducoesReceita, receitaLiquida,
                cpv, lucroBruto, despesasOperac, ebitda,
                despFinanceiras, recFinanceiras, lair, impostos, lucroLiquido);
    }

    private BigDecimal get(Map<DreCategoria, BigDecimal> mapa, DreCategoria chave) {
        return mapa.getOrDefault(chave, BigDecimal.ZERO);
    }
}
