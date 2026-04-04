package com.qorbit.engine.repository;

import com.qorbit.engine.model.Elemento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElementoRepository extends JpaRepository<Elemento, Long> {
    List<Elemento> findByPaginaIgnoreCase(String pagina);
    List<Elemento> findByStatus(String status);
    List<Elemento> findByNomeLogicoContainingIgnoreCaseOrSeletorTecnicoContainingIgnoreCase(String nome, String seletor);
    Optional<Elemento> findByNomeLogicoAndPagina(String nomeLogico, String pagina);
    boolean existsByNomeLogicoAndPaginaAndIdNot(String nomeLogico, String pagina, Long id);
    List<Elemento> findAllByOrderByPaginaAscNomeLogicoAsc();
    List<Elemento> findByNomeLogico(String nomeLogico);
    Optional<Elemento> findFirstByNomeLogicoIgnoreCase(String nomeLogico);
}
