package com.financeiro_api.Audit.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.domain.AuditLog;
import com.financeiro_api.Audit.dto.AuditLogDTO;
import com.financeiro_api.Audit.repository.AuditLogRepository;
import com.financeiro_api.shared.TenantContext;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, String entityId) {
        try {
            repository.save(AuditLog.builder()
                    .userId(TenantContext.getUserId())
                    .userEmail(TenantContext.getEmail())
                    .enterpriseId(TenantContext.get())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .build());
        } catch (Exception e) {
            log.error("Falha ao salvar audit log: action={} entityType={} entityId={}", action, entityType, entityId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action) {
        log(action, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> listar(AuditAction action, UUID userId, UUID enterpriseId,
                                    LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null)       predicates.add(cb.equal(root.get("action"), action));
            if (userId != null)       predicates.add(cb.equal(root.get("userId"), userId));
            if (enterpriseId != null) predicates.add(cb.equal(root.get("enterpriseId"), enterpriseId));
            if (from != null)         predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null)           predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            assert query != null;
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable).map(AuditLogDTO::from);
    }
}
