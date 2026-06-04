package com.financeiro_api.Audit.dto;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.domain.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogDTO(
        UUID id,
        UUID userId,
        String userEmail,
        UUID enterpriseId,
        AuditAction action,
        String entityType,
        String entityId,
        LocalDateTime createdAt
) {
    public static AuditLogDTO from(AuditLog log) {
        return new AuditLogDTO(
                log.getId(),
                log.getUserId(),
                log.getUserEmail(),
                log.getEnterpriseId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getCreatedAt()
        );
    }
}
