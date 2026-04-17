package com.qorbit.engine.auth.repository;

import com.qorbit.engine.auth.model.MfaBackupCode;
import com.qorbit.engine.auth.model.QorbitUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, Long> {

    List<MfaBackupCode> findByUserAndUsedFalse(QorbitUser user);

    /** Remove todos os backup codes ao desativar MFA ou regenerar. */
    @Modifying
    @Query("DELETE FROM MfaBackupCode c WHERE c.user = :user")
    void deleteAllByUser(@Param("user") QorbitUser user);

    long countByUserAndUsedFalse(QorbitUser user);
}
