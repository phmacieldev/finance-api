package com.financeiro_api.Enterprises.repository;

import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Enterprises.domain.EnterpriseStatus;
import com.financeiro_api.Users.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnterpriseRepository extends JpaRepository<Enterprise, UUID> {
    Optional<Enterprise> findByCnpj(String cnpj);
    boolean existsByCnpj(String cnpj);
    boolean existsByCpf(String cpf);
    List<Enterprise> findAllByStatusOrderByCreatedAtAsc(EnterpriseStatus status);
    List<Enterprise> findAllByOrderByCreatedAtAsc();

    @Query("SELECT e FROM Enterprise e WHERE NOT EXISTS " +
           "(SELECT u FROM User u WHERE u.enterprise = e AND u.role = :adminRole) " +
           "ORDER BY e.createdAt ASC")
    List<Enterprise> findAllClientEmpresas(@Param("adminRole") Role adminRole);

    @Query("SELECT e FROM Enterprise e WHERE e.status = :status AND NOT EXISTS " +
           "(SELECT u FROM User u WHERE u.enterprise = e AND u.role = :adminRole) " +
           "ORDER BY e.createdAt ASC")
    List<Enterprise> findAllClientEmpresasByStatus(@Param("status") EnterpriseStatus status,
                                                    @Param("adminRole") Role adminRole);
}
