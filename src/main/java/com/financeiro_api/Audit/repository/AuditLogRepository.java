package com.financeiro_api.Audit.repository;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:enterpriseId IS NULL OR a.enterpriseId = :enterpriseId) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> buscarComFiltros(
            @Param("action") AuditAction action,
            @Param("userId") UUID userId,
            @Param("enterpriseId") UUID enterpriseId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
