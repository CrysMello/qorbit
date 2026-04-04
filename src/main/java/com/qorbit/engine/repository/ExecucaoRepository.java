package com.qorbit.engine.repository;

import com.qorbit.engine.model.Execucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExecucaoRepository extends JpaRepository<Execucao, Long> {

    // Ordena por ID decrescente (mais recente primeiro) — evita problema com String date
    List<Execucao> findAllByOrderByIdDesc();

    // Compatibilidade com código existente
    default List<Execucao> findAllByOrderByIniciadoEmDesc() {
        return findAllByOrderByIdDesc();
    }
}
