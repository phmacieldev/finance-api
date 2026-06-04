package com.financeiro_api.SaldoAnterior.service;

import com.financeiro_api.SaldoAnterior.domain.SaldoAnterior;
import com.financeiro_api.SaldoAnterior.dto.SaldoAnteriorDTO;
import com.financeiro_api.SaldoAnterior.dto.SaldoAnteriorResponseDTO;
import com.financeiro_api.SaldoAnterior.repository.SaldoAnteriorRepository;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaldoAnteriorService {

    private final SaldoAnteriorRepository repository;

    public SaldoAnteriorService(SaldoAnteriorRepository repository) {
        this.repository = repository;
    }

    public SaldoAnteriorResponseDTO buscar(int mes, int ano) {
        return repository
                .findByEnterpriseIdAndMesAndAno(TenantContext.get(), mes, ano)
                .map(SaldoAnteriorResponseDTO::from)
                .orElse(SaldoAnteriorResponseDTO.zero(mes, ano));
    }

    @Transactional
    public SaldoAnteriorResponseDTO salvar(SaldoAnteriorDTO dto) {
        SaldoAnterior registro = repository
                .findByEnterpriseIdAndMesAndAno(TenantContext.get(), dto.mes(), dto.ano())
                .orElse(SaldoAnterior.builder()
                        .enterpriseId(TenantContext.get())
                        .mes(dto.mes())
                        .ano(dto.ano())
                        .build());

        registro.setValor(dto.valor());
        return SaldoAnteriorResponseDTO.from(repository.save(registro));
    }
}
