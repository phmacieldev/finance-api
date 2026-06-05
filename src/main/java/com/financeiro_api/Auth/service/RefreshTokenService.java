package com.financeiro_api.Auth.service;

import com.financeiro_api.Auth.domain.RefreshToken;
import com.financeiro_api.Auth.repository.RefreshTokenRepository;
import com.financeiro_api.Users.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

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
        repository.revokeAllByUserId(user.getId());

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
}
