package com.financeiro_api.Auth.service;

import com.financeiro_api.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEV_SECRET_PREFIX = "financeiro-saas-very-long-secret-key";

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void validarSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 48) {
            throw new IllegalStateException(
                "JWT_SECRET inválido: mínimo 48 caracteres. Configure a variável de ambiente JWT_SECRET.");
        }
        if (secret.startsWith(DEV_SECRET_PREFIX)) {
            log.warn("ATENÇÃO: JWT_SECRET está usando o valor padrão de desenvolvimento. " +
                     "Configure JWT_SECRET com um segredo forte antes de ir para produção.");
        }
    }

    public String gerarToken(String email, UUID userId, UUID enterpriseId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("role", role);
        if (enterpriseId != null) {
            claims.put("enterpriseId", enterpriseId.toString());
        }
        return Jwts.builder()
                .subject(email)
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(chave())
                .compact();
    }

    public Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    public UUID extrairEnterpriseId(String token) {
        String id = extrairClaims(token).get("enterpriseId", String.class);
        return id != null ? UUID.fromString(id) : null;
    }

    public UUID extrairUserId(String token) {
        return UUID.fromString(extrairClaims(token).get("userId", String.class));
    }

    public String extrairRole(String token) {
        return extrairClaims(token).get("role", String.class);
    }

    public boolean tokenValido(String token) {
        try {
            extrairClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey chave() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
