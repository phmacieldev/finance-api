package com.financeiro_api.Auth.repository;

import com.financeiro_api.Auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    void revokeAllByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiresAt < :now")
    void deleteExpiredAndRevoked(java.time.LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.enterpriseId = :enterpriseId AND rt.revoked = false")
    void revokeByUserIdAndEnterpriseId(UUID userId, UUID enterpriseId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.enterpriseId IS NULL AND rt.revoked = false")
    void revokeByUserIdAndNoEnterprise(UUID userId);
}
