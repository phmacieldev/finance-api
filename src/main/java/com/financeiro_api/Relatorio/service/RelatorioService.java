package com.financeiro_api.Relatorio.service;

import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.Relatorio.dto.RelatorioMensalDTO;
import com.financeiro_api.Relatorio.dto.RelatorioMensalItemDTO;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    private final ExtratoRepository extratoRepository;

    public RelatorioService(ExtratoRepository extratoRepository) {
        this.extratoRepository = extratoRepository;
    }

    public RelatorioMensalDTO gerarRelatorioMensal(int meses) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusMonths(meses - 1L).withDayOfMonth(1);

        List<Extrato> extratos = extratoRepository
                .findAllByEnterpriseIdAndDataBetweenOrderByDataAsc(TenantContext.get(), inicio, hoje);

        Map<String, List<Extrato>> porMesAno = extratos.stream()
                .collect(Collectors.groupingBy(e -> e.getAno() + "-" + e.getMes()));

        List<RelatorioMensalItemDTO> itens = new ArrayList<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(hoje)) {
            int mes = cursor.getMonthValue();
            int ano = cursor.getYear();
            List<Extrato> doMes = porMesAno.getOrDefault(ano + "-" + mes, List.of());

            BigDecimal entradas = doMes.stream()
                    .filter(e -> e.getValor() != null && e.getValor().compareTo(BigDecimal.ZERO) > 0)
                    .map(Extrato::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saidas = doMes.stream()
                    .filter(e -> e.getValor() != null && e.getValor().compareTo(BigDecimal.ZERO) < 0)
                    .map(Extrato::getValor)
                    .map(BigDecimal::abs)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            itens.add(new RelatorioMensalItemDTO(mes, ano, entradas, saidas, entradas.subtract(saidas)));
            cursor = cursor.plusMonths(1);
        }

        return new RelatorioMensalDTO(itens);
    }
}
