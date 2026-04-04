package com.qorbit.engine.repository;

import com.qorbit.engine.model.Evidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {
    List<Evidencia> findByExecucaoIdOrderByNumeroStepAsc(Long execucaoId);
}
