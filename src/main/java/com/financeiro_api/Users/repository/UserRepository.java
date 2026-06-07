package com.financeiro_api.Users.repository;

import com.financeiro_api.Users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.enterprise WHERE u.email = :email")
    Optional<User> findByEmailWithEnterprise(@Param("email") String email);

    List<User> findAllByEnterprise_IdOrderByNameAsc(UUID enterpriseId);

    Optional<User> findByIdAndEnterprise_Id(UUID id, UUID enterpriseId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.enterprise WHERE u.tokenVerificacao = :token")
    Optional<User> findByTokenVerificacao(@Param("token") String token);

    Optional<User> findByTokenResetSenha(String token);

    boolean existsByRole(com.financeiro_api.Users.domain.Role role);

    boolean existsByEnterprise_IdAndRole(UUID enterpriseId, com.financeiro_api.Users.domain.Role role);
}
