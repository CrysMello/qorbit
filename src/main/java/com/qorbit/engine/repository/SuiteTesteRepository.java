package com.qorbit.engine.repository;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.model.SuiteTeste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuiteTesteRepository extends JpaRepository<SuiteTeste, Long> {
    List<SuiteTeste> findAllByOrderByNomeAsc();
    List<SuiteTeste> findByUsuarioOrderByNomeAsc(QorbitUser usuario);
    boolean existsByNome(String nome);
}
