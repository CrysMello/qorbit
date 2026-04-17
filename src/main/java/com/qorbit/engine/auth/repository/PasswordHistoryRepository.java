package com.qorbit.engine.auth.repository;

import com.qorbit.engine.auth.model.PasswordHistory;
import com.qorbit.engine.auth.model.QorbitUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /** Retorna últimas N senhas do usuário (para verificar reutilização). */
    List<PasswordHistory> findTop24ByUserOrderByCreatedAtDesc(QorbitUser user);
}
