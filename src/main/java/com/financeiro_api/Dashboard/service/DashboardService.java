package com.financeiro_api.Dashboard.service;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.repository.CategoriaRepository;
import com.financeiro_api.Dashboard.dto.DashboardDTO;
import com.financeiro_api.Dashboard.dto.FluxoDiarioDTO;
import com.financeiro_api.Dashboard.dto.TopCategoriaDTO;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.SaldoAnterior.repository.SaldoAnteriorRepository;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ExtratoRepository extratoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SaldoAnteriorRepository saldoAnteriorRepository;

    public DashboardService(ExtratoRepository extratoRepository,
                            CategoriaRepository categoriaRepository,
                            SaldoAnteriorRepository saldoAnteriorRepository) {
        this.extratoRepository = extratoRepository;
        this.categoriaRepository = categoriaRepository;
        this.saldoAnteriorRepository = saldoAnteriorRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDTO calcular(int mes, int ano) {
        UUID tenantId = TenantContext.get();

        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = YearMonth.of(ano, mes).atEndOfMonth();

        List<Extrato> extratosMes = extratoRepository
                .findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(tenantId, mes, ano);

        // Mês anterior
        YearMonth mesAnteriorYM = YearMonth.of(ano, mes).minusMonths(1);
        List<Extrato> extratosMesAnterior = extratoRepository
                .findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(
                        tenantId, mesAnteriorYM.getMonthValue(), mesAnteriorYM.getYear());

        BigDecimal totalEntradas = somarPositivos(extratosMes);
        BigDecimal totalSaidas = somarNegativos(extratosMes);
        BigDecimal totalEntradasAnt = somarPositivos(extratosMesAnterior);
        BigDecimal totalSaidasAnt = somarNegativos(extratosMesAnterior);

        BigDecimal saldoAnterior = saldoAnteriorRepository
                .findByEnterpriseIdAndMesAndAno(tenantId, mes, ano)
                .map(s -> s.getValor())
                .orElse(BigDecimal.ZERO);

        BigDecimal saldoAtual = saldoAnterior.add(totalEntradas).subtract(totalSaidas);

        int semCategoria = (int) extratosMes.stream().filter(e -> e.getCategoriaId() == null).count();

        List<FluxoDiarioDTO> fluxoDiario = calcularFluxoDiario(extratosMes, inicio, fim);

        Map<UUID, Categoria> categorias = categoriaRepository.findAllByEnterpriseId(tenantId)
                .stream().collect(Collectors.toMap(Categoria::getId, c -> c));

        List<TopCategoriaDTO> topDespesas = calcularTopCategorias(extratosMes, categorias, false);
        List<TopCategoriaDTO> topReceitas = calcularTopCategorias(extratosMes, categorias, true);

        BigDecimal variacao = (totalEntradasAnt.compareTo(BigDecimal.ZERO) > 0)
                ? totalEntradas.subtract(totalEntradasAnt)
                        .divide(totalEntradasAnt, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal variacaoSaidas = (totalSaidasAnt.compareTo(BigDecimal.ZERO) > 0)
                ? totalSaidas.subtract(totalSaidasAnt)
                        .divide(totalSaidasAnt, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new DashboardDTO(
                mes, ano, totalEntradas, totalSaidas,
                totalEntradas.subtract(totalSaidas), saldoAtual,
                totalEntradasAnt, totalSaidasAnt, variacao, variacaoSaidas,
                semCategoria, fluxoDiario, topDespesas, topReceitas
        );
    }

    private List<FluxoDiarioDTO> calcularFluxoDiario(List<Extrato> extratos, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, List<Extrato>> porDia = extratos.stream()
                .collect(Collectors.groupingBy(Extrato::getData));

        List<FluxoDiarioDTO> resultado = new ArrayList<>();
        BigDecimal saldoAcum = BigDecimal.ZERO;

        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            List<Extrato> doDia = porDia.getOrDefault(dia, List.of());
            BigDecimal entradas = somarPositivos(doDia);
            BigDecimal saidas = somarNegativos(doDia);
            BigDecimal saldoDia = entradas.subtract(saidas);
            saldoAcum = saldoAcum.add(saldoDia);
            resultado.add(new FluxoDiarioDTO(dia, entradas, saidas, saldoDia, saldoAcum));
        }
        return resultado;
    }

    private List<TopCategoriaDTO> calcularTopCategorias(
            List<Extrato> extratos, Map<UUID, Categoria> categorias, boolean receitas) {

        return extratos.stream()
                .filter(e -> e.getCategoriaId() != null)
                .filter(e -> receitas
                        ? e.getValor().compareTo(BigDecimal.ZERO) > 0
                        : e.getValor().compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.groupingBy(
                        Extrato::getCategoriaId,
                        Collectors.reducing(BigDecimal.ZERO, e -> e.getValor().abs(), BigDecimal::add)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Categoria cat = categorias.get(entry.getKey());
                    String nome = cat != null ? cat.getName() : "Sem nome";
                    return new TopCategoriaDTO(entry.getKey(), nome, entry.getValue());
                })
                .toList();
    }

    private BigDecimal somarPositivos(List<Extrato> lista) {
        return lista.stream()
                .filter(e -> e.getValor().compareTo(BigDecimal.ZERO) > 0)
                .map(Extrato::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somarNegativos(List<Extrato> lista) {
        return lista.stream()
                .filter(e -> e.getValor().compareTo(BigDecimal.ZERO) < 0)
                .map(e -> e.getValor().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
