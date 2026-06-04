package com.financeiro_api.SaldoAnterior.repository;

import com.financeiro_api.SaldoAnterior.domain.SaldoAnterior;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SaldoAnteriorRepository extends JpaRepository<SaldoAnterior, UUID> {
    Optional<SaldoAnterior> findByEnterpriseIdAndMesAndAno(UUID enterpriseId, int mes, int ano);
}
