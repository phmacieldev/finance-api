package com.financeiro_api.Previsao.service;

import com.financeiro_api.Previsao.domain.Previsao;
import com.financeiro_api.Previsao.domain.TipoPrevisao;
import com.financeiro_api.Previsao.dto.PrevisaoCreateDTO;
import com.financeiro_api.Previsao.dto.PrevisaoResponseDTO;
import com.financeiro_api.Previsao.repository.PrevisaoRepository;
import com.financeiro_api.shared.TenantContext;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PrevisaoService {

    private final PrevisaoRepository repository;

    public PrevisaoService(PrevisaoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PrevisaoResponseDTO> listar() {
        return repository.findAllByEnterpriseIdAndAtivaTrue(TenantContext.get())
                .stream().map(PrevisaoResponseDTO::from).toList();
    }

    public Page<PrevisaoResponseDTO> listarPaginado(TipoPrevisao tipo, Pageable pageable) {
        if (tipo != null) {
            return repository.findAllByEnterpriseIdAndTipoAndAtivaTrue(TenantContext.get(), tipo, pageable)
                    .map(PrevisaoResponseDTO::from);
        }
        return repository.findAllByEnterpriseIdAndAtivaTrue(TenantContext.get(), pageable)
                .map(PrevisaoResponseDTO::from);
    }

    public List<PrevisaoResponseDTO> listarPorTipo(TipoPrevisao tipo) {
        return repository.findAllByEnterpriseIdAndTipoAndAtivaTrue(TenantContext.get(), tipo)
                .stream().map(PrevisaoResponseDTO::from).toList();
    }

    public List<Previsao> buscarAtivasNoPeriodo(LocalDate inicio, LocalDate fim) {
        return repository.findAtivasNoPeriodo(TenantContext.get(), inicio, fim);
    }

    public PrevisaoResponseDTO criar(PrevisaoCreateDTO dto) {
        Previsao previsao = Previsao.builder()
                .enterpriseId(TenantContext.get())
                .descricao(dto.descricao())
                .tipo(dto.tipo())
                .valor(dto.valor())
                .frequencia(dto.frequencia())
                .dataInicio(dto.dataInicio())
                .dataFim(dto.dataFim())
                .diaRecorrencia(dto.diaRecorrencia())
                .categoriaId(dto.categoriaId())
                .build();
        return PrevisaoResponseDTO.from(repository.save(previsao));
    }

    @Transactional
    public void desativar(UUID id) {
        Previsao p = repository.findByEnterpriseIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Previsão não encontrada: " + id));
        p.setAtiva(false);
        repository.save(p);
    }
}
