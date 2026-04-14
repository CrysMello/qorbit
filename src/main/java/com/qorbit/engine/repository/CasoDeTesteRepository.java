package com.qorbit.engine.repository;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.model.CasoDeTeste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CasoDeTesteRepository extends JpaRepository<CasoDeTeste, Long> {
    List<CasoDeTeste> findByStatus(String status);
    List<CasoDeTeste> findByModuloIgnoreCase(String modulo);
    List<CasoDeTeste> findByNomeContainingIgnoreCase(String nome);
    List<CasoDeTeste> findByUrlAlvoContainingIgnoreCase(String urlAlvo);
    
    // Métodos para filtrar por usuário
    List<CasoDeTeste> findByUsuario(QorbitUser usuario);
    List<CasoDeTeste> findByUsuarioAndStatus(QorbitUser usuario, String status);
    List<CasoDeTeste> findByUsuarioAndModuloIgnoreCase(QorbitUser usuario, String modulo);
    List<CasoDeTeste> findByUsuarioAndNomeContainingIgnoreCase(QorbitUser usuario, String nome);
}
