package com.financeiro_api.Audit.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.domain.AuditLog;
import com.financeiro_api.Audit.dto.AuditLogDTO;
import com.financeiro_api.Audit.repository.AuditLogRepository;
import com.financeiro_api.shared.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, String entityId) {
        repository.save(AuditLog.builder()
                .userId(TenantContext.getUserId())
                .userEmail(TenantContext.getEmail())
                .enterpriseId(TenantContext.get())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action) {
        log(action, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> listar(AuditAction action, UUID userId, UUID enterpriseId,
                                    LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return repository.buscarComFiltros(action, userId, enterpriseId, from, to, pageable)
                .map(AuditLogDTO::from);
    }
}
