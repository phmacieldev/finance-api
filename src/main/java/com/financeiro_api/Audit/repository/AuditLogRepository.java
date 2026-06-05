package com.financeiro_api.Audit.repository;

import com.financeiro_api.Audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query(value = """
            SELECT * FROM audit_logs
            WHERE (:action IS NULL OR action = :action)
              AND (:userId IS NULL OR user_id = :userId::uuid)
              AND (:enterpriseId IS NULL OR enterprise_id = :enterpriseId::uuid)
              AND (:from IS NULL OR created_at >= :from)
              AND (:to IS NULL OR created_at <= :to)
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM audit_logs
            WHERE (:action IS NULL OR action = :action)
              AND (:userId IS NULL OR user_id = :userId::uuid)
              AND (:enterpriseId IS NULL OR enterprise_id = :enterpriseId::uuid)
              AND (:from IS NULL OR created_at >= :from)
              AND (:to IS NULL OR created_at <= :to)
            """,
            nativeQuery = true)
    Page<AuditLog> buscarComFiltros(
            @Param("action") String action,
            @Param("userId") String userId,
            @Param("enterpriseId") String enterpriseId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
