package com.financeiro_api.UserEnterprise.repository;

import com.financeiro_api.UserEnterprise.domain.UserEnterprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserEnterpriseRepository extends JpaRepository<UserEnterprise, UUID> {

    @Query("SELECT ue FROM UserEnterprise ue JOIN FETCH ue.enterprise WHERE ue.user.id = :userId")
    List<UserEnterprise> findAllByUserIdFetchEnterprise(@Param("userId") UUID userId);

    Optional<UserEnterprise> findByUser_IdAndEnterprise_Id(UUID userId, UUID enterpriseId);

    @Query("SELECT ue FROM UserEnterprise ue JOIN FETCH ue.enterprise WHERE ue.user.id = :userId AND ue.enterprise.id = :enterpriseId")
    Optional<UserEnterprise> findByUserIdAndEnterpriseIdFetchEnterprise(@Param("userId") UUID userId, @Param("enterpriseId") UUID enterpriseId);

    boolean existsByUser_IdAndEnterprise_Id(UUID userId, UUID enterpriseId);

    @Query("SELECT ue FROM UserEnterprise ue JOIN FETCH ue.user WHERE ue.enterprise.id = :enterpriseId ORDER BY ue.user.name ASC")
    List<UserEnterprise> findAllByEnterpriseIdFetchUser(@Param("enterpriseId") UUID enterpriseId);
}
