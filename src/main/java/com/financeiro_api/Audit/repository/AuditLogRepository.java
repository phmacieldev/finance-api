package com.financeiro_api.Audit.repository;

import com.financeiro_api.Audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {
}
