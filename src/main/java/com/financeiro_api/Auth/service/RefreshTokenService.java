package com.financeiro_api.Auth.service;

import com.financeiro_api.Auth.domain.RefreshToken;
import com.financeiro_api.Auth.repository.RefreshTokenRepository;
import com.financeiro_api.Users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final long refreshExpirationDays;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.jwt.refresh-expiration-days:30}") long refreshExpirationDays) {
        this.repository = repository;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    @Transactional
    public RefreshToken criar(User user, UUID enterpriseId) {
        // Revoga apenas o token da empresa alvo, preservando sessões em outras empresas
        if (enterpriseId != null) {
            repository.revokeByUserIdAndEnterpriseId(user.getId(), enterpriseId);
        } else {
            repository.revokeByUserIdAndNoEnterprise(user.getId());
        }

        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return repository.save(RefreshToken.builder()
                .token(token)
                .user(user)
                .enterpriseId(enterpriseId)
                .expiresAt(LocalDateTime.now().plusDays(refreshExpirationDays))
                .build());
    }

    @Transactional
    public RefreshToken validarEObter(String token) {
        RefreshToken rt = repository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

        if (rt.isRevoked()) {
            throw new BadCredentialsException("Refresh token revogado");
        }
        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token expirado");
        }

        rt.setRevoked(true);
        repository.save(rt);
        return rt;
    }

    @Transactional
    public void revogarPorToken(String token) {
        repository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            repository.save(rt);
        });
    }

    @Transactional
    public void revogarTodos(UUID userId) {
        repository.revokeAllByUserId(userId);
    }

    /**
     * Limpeza diária de tokens revogados e expirados — evita acúmulo infinito na tabela.
     * Roda todo dia à meia-noite (horário UTC).
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void limparTokensExpirados() {
        repository.deleteExpiredAndRevoked(LocalDateTime.now());
        log.debug("RefreshToken: limpeza de tokens expirados/revogados concluída");
    }
}
