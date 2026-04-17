package com.qorbit.engine.repository;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.model.Elemento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Métodos para filtrar por usuário
    List<Elemento> findByUsuarioOrderByPaginaAscNomeLogicoAsc(QorbitUser usuario);
    List<Elemento> findByUsuarioAndPaginaIgnoreCase(QorbitUser usuario, String pagina);
    List<Elemento> findByUsuarioAndStatus(QorbitUser usuario, String status);

    // Query corrigida: garante que o filtro de usuário se aplica a AMBAS as condições (nome e seletor)
    @Query("SELECT e FROM Elemento e WHERE e.usuario = :usuario AND " +
           "(LOWER(e.nomeLogico) LIKE LOWER(CONCAT('%', :busca, '%')) OR " +
           "LOWER(e.seletorTecnico) LIKE LOWER(CONCAT('%', :busca, '%'))) " +
           "ORDER BY e.pagina ASC, e.nomeLogico ASC")
    List<Elemento> buscarPorUsuarioETexto(@Param("usuario") QorbitUser usuario, @Param("busca") String busca);

    long countByUsuario(QorbitUser usuario);
}
