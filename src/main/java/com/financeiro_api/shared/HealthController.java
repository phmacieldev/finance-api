package com.financeiro_api.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @GetMapping
    public Map<String, Object> health() {
        log.info("ping recebido — aplicação ativa");
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
