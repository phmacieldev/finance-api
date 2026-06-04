package com.financeiro_api.Previsao.repository;

import com.financeiro_api.Previsao.domain.Previsao;
import com.financeiro_api.Previsao.domain.TipoPrevisao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrevisaoRepository extends JpaRepository<Previsao, UUID> {

    List<Previsao> findAllByEnterpriseIdAndAtivaTrue(UUID enterpriseId);

    Page<Previsao> findAllByEnterpriseIdAndAtivaTrue(UUID enterpriseId, Pageable pageable);

    List<Previsao> findAllByEnterpriseIdAndTipoAndAtivaTrue(UUID enterpriseId, TipoPrevisao tipo);

    Page<Previsao> findAllByEnterpriseIdAndTipoAndAtivaTrue(UUID enterpriseId, TipoPrevisao tipo, Pageable pageable);

    Optional<Previsao> findByEnterpriseIdAndId(UUID enterpriseId, UUID id);

    @Query("""
            SELECT p FROM Previsao p
            WHERE p.enterpriseId = :tenantId
              AND p.ativa = true
              AND p.dataInicio <= :fim
              AND (p.dataFim IS NULL OR p.dataFim >= :inicio)
            """)
    List<Previsao> findAtivasNoPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
