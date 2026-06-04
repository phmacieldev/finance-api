package com.financeiro_api.shared;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "Liveness check com verificação de conectividade com o banco")
@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "Status da API e do banco de dados")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            result.put("status", "UP");
            result.put("database", "UP");
            log.debug("Health check OK");
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("Health check falhou — banco inacessível: {}", ex.getMessage());
            result.put("status", "DOWN");
            result.put("database", "DOWN");
            return ResponseEntity.status(503).body(result);
        }
    }
}
