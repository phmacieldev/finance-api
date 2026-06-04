package com.financeiro_api.ContaBancaria.service;

import com.financeiro_api.ContaBancaria.domain.ContaBancaria;
import com.financeiro_api.ContaBancaria.dto.ContaBancariaCreateDTO;
import com.financeiro_api.ContaBancaria.dto.ContaBancariaResponseDTO;
import com.financeiro_api.ContaBancaria.repository.ContaBancariaRepository;
import com.financeiro_api.shared.TenantContext;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ContaBancariaService {

    private final ContaBancariaRepository repository;

    public ContaBancariaService(ContaBancariaRepository repository) {
        this.repository = repository;
    }

    public List<ContaBancariaResponseDTO> listar() {
        return repository.findAllByEnterpriseIdAndAtivaTrue(TenantContext.get())
                .stream().map(ContaBancariaResponseDTO::from).toList();
    }

    @Transactional
    public ContaBancariaResponseDTO criar(ContaBancariaCreateDTO dto) {
        ContaBancaria conta = ContaBancaria.builder()
                .enterpriseId(TenantContext.get())
                .nome(dto.nome().trim())
                .banco(dto.banco().trim())
                .tipo(dto.tipo() != null ? dto.tipo() : com.financeiro_api.ContaBancaria.domain.TipoConta.CORRENTE)
                .build();
        return ContaBancariaResponseDTO.from(repository.save(conta));
    }

    @Transactional
    public void desativar(UUID id) {
        ContaBancaria conta = repository.findByIdAndEnterpriseId(id, TenantContext.get())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta bancária não encontrada: " + id));
        conta.setAtiva(false);
        repository.save(conta);
    }
}
