package com.financeiro_api.Extrato.repository;

import com.financeiro_api.Extrato.domain.Extrato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExtratoRepository extends JpaRepository<Extrato, UUID> {

    List<Extrato> findAllByEnterpriseIdOrderByDataAsc(UUID enterpriseId);

    List<Extrato> findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(UUID enterpriseId, int mes, int ano);

    List<Extrato> findAllByEnterpriseIdAndDataBetweenOrderByDataAsc(UUID enterpriseId, LocalDate inicio, LocalDate fim);

    Optional<Extrato> findByEnterpriseIdAndId(UUID enterpriseId, UUID id);

    boolean existsByEnterpriseIdAndImportHash(UUID enterpriseId, String importHash);

    @Query("SELECT e.importHash FROM Extrato e WHERE e.enterpriseId = :enterpriseId")
    Set<String> findHashesByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    void deleteByEnterpriseIdAndImportBatchId(UUID enterpriseId, UUID importBatchId);

    @Query("""
            SELECT e FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.mes = :mes AND e.ano = :ano
              AND e.categoriaId = :categoriaId
            ORDER BY e.data ASC
            """)
    List<Extrato> findByEnterpriseIdMesAnoCategoria(
            @Param("tenantId") UUID tenantId,
            @Param("mes") int mes,
            @Param("ano") int ano,
            @Param("categoriaId") UUID categoriaId);

    @Query("""
            SELECT e FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.mes = :mes AND e.ano = :ano
              AND e.categoriaId IS NULL
            ORDER BY e.data ASC
            """)
    List<Extrato> findSemCategoria(
            @Param("tenantId") UUID tenantId,
            @Param("mes") int mes,
            @Param("ano") int ano);

    @Query("""
            SELECT e FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.data BETWEEN :inicio AND :fim
              AND (:razaoSocial IS NULL OR LOWER(e.razaoSocial) LIKE LOWER(CONCAT('%', :razaoSocial, '%')))
            ORDER BY e.data ASC
            """)
    List<Extrato> buscarComFiltro(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("razaoSocial") String razaoSocial);

    @Query(value = """
            SELECT e FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.data BETWEEN :inicio AND :fim
              AND (:razaoSocial IS NULL OR LOWER(e.razaoSocial) LIKE LOWER(CONCAT('%', :razaoSocial, '%')))
            ORDER BY e.data ASC
            """,
           countQuery = """
            SELECT COUNT(e) FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.data BETWEEN :inicio AND :fim
              AND (:razaoSocial IS NULL OR LOWER(e.razaoSocial) LIKE LOWER(CONCAT('%', :razaoSocial, '%')))
            """)
    Page<Extrato> buscarPorPeriodoPaginado(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("razaoSocial") String razaoSocial,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.valor), 0) FROM Extrato e WHERE e.enterpriseId = :enterpriseId")
    BigDecimal sumTodosByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    @Query("SELECT e FROM Extrato e WHERE e.enterpriseId = :enterpriseId ORDER BY e.data DESC, e.importadoEm DESC")
    List<Extrato> findTop5ByEnterpriseId(@Param("enterpriseId") UUID enterpriseId, Pageable pageable);

    @Query(
        value = """
            SELECT e FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.mes = :mes AND e.ano = :ano
              AND (:categoriaId IS NULL OR e.categoriaId = :categoriaId)
              AND (:contaBancariaId IS NULL OR e.contaBancariaId = :contaBancariaId)
              AND (:tipo IS NULL
                   OR (:tipo = 'RECEITA' AND e.valor > 0)
                   OR (:tipo = 'DESPESA' AND e.valor < 0))
            ORDER BY e.data ASC
            """,
        countQuery = """
            SELECT COUNT(e) FROM Extrato e
            WHERE e.enterpriseId = :tenantId
              AND e.mes = :mes AND e.ano = :ano
              AND (:categoriaId IS NULL OR e.categoriaId = :categoriaId)
              AND (:contaBancariaId IS NULL OR e.contaBancariaId = :contaBancariaId)
              AND (:tipo IS NULL
                   OR (:tipo = 'RECEITA' AND e.valor > 0)
                   OR (:tipo = 'DESPESA' AND e.valor < 0))
            """
    )
    Page<Extrato> buscarPorMesAnoComFiltros(
            @Param("tenantId") UUID tenantId,
            @Param("mes") int mes,
            @Param("ano") int ano,
            @Param("categoriaId") UUID categoriaId,
            @Param("contaBancariaId") UUID contaBancariaId,
            @Param("tipo") String tipo,
            Pageable pageable);
}
