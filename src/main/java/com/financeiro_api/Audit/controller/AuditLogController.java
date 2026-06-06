package com.financeiro_api.Audit.controller;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.dto.AuditLogDTO;
import com.financeiro_api.Audit.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AuditLogDTO> listar(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID enterpriseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;

        return service.listar(action, userId, enterpriseId, fromDt, toDt,
                PageRequest.of(page, size));
    }
}
