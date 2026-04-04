package com.qorbit.engine.repository;

import com.qorbit.engine.model.StepTeste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StepTesteRepository extends JpaRepository<StepTeste, Long> {
    List<StepTeste> findByCasoDeTesteIdOrderByNumeroStepAsc(Long casoDeTesteId);
}
