package com.financeiro_api.Conciliacao.service;

import com.financeiro_api.Conciliacao.dto.ConciliacaoItemDTO;
import com.financeiro_api.Conciliacao.dto.ConciliacaoPeriodoDTO;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.Previsao.domain.Frequencia;
import com.financeiro_api.Previsao.domain.Previsao;
import com.financeiro_api.Previsao.domain.TipoPrevisao;
import com.financeiro_api.Previsao.service.PrevisaoService;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConciliacaoService {

    private final ExtratoRepository extratoRepository;
    private final PrevisaoService previsaoService;

    public ConciliacaoService(ExtratoRepository extratoRepository, PrevisaoService previsaoService) {
        this.extratoRepository = extratoRepository;
        this.previsaoService = previsaoService;
    }

    public ConciliacaoPeriodoDTO conciliarMes(int mes, int ano) {
        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = YearMonth.of(ano, mes).atEndOfMonth();
        return conciliarPeriodo(inicio, fim);
    }

    public ConciliacaoPeriodoDTO conciliarPeriodo(LocalDate inicio, LocalDate fim) {
        UUID tenantId = TenantContext.get();

        List<Extrato> extratos = extratoRepository
                .findAllByEnterpriseIdAndDataBetweenOrderByDataAsc(tenantId, inicio, fim);

        List<Previsao> previsoes = previsaoService.buscarAtivasNoPeriodo(inicio, fim);

        // Agrupa extratos por dia
        Map<LocalDate, List<Extrato>> porDia = extratos.stream()
                .collect(Collectors.groupingBy(Extrato::getData));

        // Calcula previsões esperadas por dia
        Map<LocalDate, BigDecimal> entradasPrevistasPorDia = calcularPrevisoesExpandidas(previsoes, inicio, fim, TipoPrevisao.RECEITA);
        Map<LocalDate, BigDecimal> saidasPrevistasPorDia = calcularPrevisoesExpandidas(previsoes, inicio, fim, TipoPrevisao.DESPESA);

        List<ConciliacaoItemDTO> itens = new ArrayList<>();
        BigDecimal saldoAcumulado = BigDecimal.ZERO;

        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            List<Extrato> extratosDia = porDia.getOrDefault(dia, List.of());

            BigDecimal entradas = extratosDia.stream()
                    .filter(e -> e.getValor().compareTo(BigDecimal.ZERO) > 0)
                    .map(Extrato::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saidas = extratosDia.stream()
                    .filter(e -> e.getValor().compareTo(BigDecimal.ZERO) < 0)
                    .map(e -> e.getValor().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saldoDia = entradas.subtract(saidas);
            saldoAcumulado = saldoAcumulado.add(saldoDia);

            BigDecimal entradasPrevistas = entradasPrevistasPorDia.getOrDefault(dia, BigDecimal.ZERO);
            BigDecimal saidasPrevistas = saidasPrevistasPorDia.getOrDefault(dia, BigDecimal.ZERO);

            itens.add(new ConciliacaoItemDTO(
                    dia, entradas, saidas, saldoDia, saldoAcumulado,
                    entradasPrevistas, saidasPrevistas,
                    entradas.subtract(entradasPrevistas),
                    saidas.subtract(saidasPrevistas)
            ));
        }

        BigDecimal totalEntradas = itens.stream().map(ConciliacaoItemDTO::entradas).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSaidas = itens.stream().map(ConciliacaoItemDTO::saidas).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEntradasPrev = entradasPrevistasPorDia.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSaidasPrev = saidasPrevistasPorDia.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ConciliacaoPeriodoDTO(
                inicio, fim, totalEntradas, totalSaidas,
                totalEntradas.subtract(totalSaidas),
                totalEntradasPrev, totalSaidasPrev,
                totalEntradas.subtract(totalEntradasPrev),
                totalSaidas.subtract(totalSaidasPrev),
                itens
        );
    }

    // Expande previsões recorrentes em datas concretas dentro do período
    private Map<LocalDate, BigDecimal> calcularPrevisoesExpandidas(
            List<Previsao> previsoes, LocalDate inicio, LocalDate fim, TipoPrevisao tipo) {

        Map<LocalDate, BigDecimal> resultado = new HashMap<>();

        for (Previsao p : previsoes) {
            if (p.getTipo() != tipo) continue;

            List<LocalDate> datas = expandirDatas(p, inicio, fim);
            for (LocalDate data : datas) {
                resultado.merge(data, p.getValor(), BigDecimal::add);
            }
        }
        return resultado;
    }

    private List<LocalDate> expandirDatas(Previsao p, LocalDate inicio, LocalDate fim) {
        List<LocalDate> datas = new ArrayList<>();
        if (p.getFrequencia() == Frequencia.UNICA) {
            if (!p.getDataInicio().isBefore(inicio) && !p.getDataInicio().isAfter(fim)) {
                datas.add(p.getDataInicio());
            }
            return datas;
        }

        LocalDate cursor = p.getDataInicio();
        while (!cursor.isAfter(fim)) {
            if (!cursor.isBefore(inicio)) datas.add(cursor);
            cursor = proximaData(cursor, p.getFrequencia());
        }
        return datas;
    }

    private LocalDate proximaData(LocalDate atual, Frequencia freq) {
        return switch (freq) {
            case SEMANAL -> atual.plusWeeks(1);
            case QUINZENAL -> atual.plusWeeks(2);
            case MENSAL -> atual.plusMonths(1);
            case BIMESTRAL -> atual.plusMonths(2);
            case TRIMESTRAL -> atual.plusMonths(3);
            case SEMESTRAL -> atual.plusMonths(6);
            case ANUAL -> atual.plusYears(1);
            default -> atual.plusYears(100); // UNICA - não repete
        };
    }
}
