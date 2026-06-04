package com.financeiro_api.Audit.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.domain.AuditLog;
import com.financeiro_api.Audit.repository.AuditLogRepository;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
}
