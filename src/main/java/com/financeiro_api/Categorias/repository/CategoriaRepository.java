package com.financeiro_api.Categorias.repository;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.TipoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
    List<Categoria> findAllByEnterpriseId(UUID enterpriseId);
    List<Categoria> findAllByEnterpriseIdAndTipo(UUID enterpriseId, TipoCategoria tipo);
    Optional<Categoria> findByEnterpriseIdAndId(UUID enterpriseId, UUID id);
    boolean existsByEnterpriseIdAndName(UUID enterpriseId, String name);
}
