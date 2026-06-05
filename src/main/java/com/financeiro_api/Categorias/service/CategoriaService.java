package com.financeiro_api.Categorias.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.TipoCategoria;
import com.financeiro_api.Categorias.dto.CategoriaCreateDTO;
import com.financeiro_api.Categorias.dto.CategoriaResponseDTO;
import com.financeiro_api.Categorias.repository.CategoriaRepository;
import com.financeiro_api.shared.TenantContext;
import com.financeiro_api.shared.exception.ConflitoException;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final AuditLogService auditLogService;

    public CategoriaService(CategoriaRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar() {
        return repository.findAllByEnterpriseId(TenantContext.get())
                .stream().map(CategoriaResponseDTO::from).toList();
    }

    public List<CategoriaResponseDTO> listarPorTipo(TipoCategoria tipo) {
        return repository.findAllByEnterpriseIdAndTipo(TenantContext.get(), tipo)
                .stream().map(CategoriaResponseDTO::from).toList();
    }

    public CategoriaResponseDTO criar(CategoriaCreateDTO dto) {
        UUID tenantId = TenantContext.get();
        if (repository.existsByEnterpriseIdAndName(tenantId, dto.name())) {
            throw new ConflitoException("Categoria já existe: " + dto.name());
        }
        Categoria categoria = Categoria.builder()
                .enterpriseId(tenantId)
                .name(dto.name())
                .tipo(dto.tipo())
                .dreCategoria(dto.dreCategoria())
                .build();
        Categoria saved = repository.save(categoria);
        auditLogService.log(AuditAction.CATEGORIA_CREATED, "Categoria", saved.getId().toString());
        return CategoriaResponseDTO.from(saved);
    }

    public void deletar(UUID id) {
        Categoria c = repository.findByEnterpriseIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada: " + id));
        repository.delete(c);
        auditLogService.log(AuditAction.CATEGORIA_DELETED, "Categoria", id.toString());
    }

    @Transactional(readOnly = true)
    public Categoria buscarPorId(UUID id) {
        return repository.findByEnterpriseIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada: " + id));
    }
}
