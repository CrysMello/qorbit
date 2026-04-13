package com.qorbit.engine.auth.repository;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface QorbitUserRepository extends JpaRepository<QorbitUser, Long> {

    Optional<QorbitUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<QorbitUser> findByEmailVerificationToken(String token);

    @Modifying
    @Query("UPDATE QorbitUser u SET u.failedLoginAttempts = 0, u.accountLocked = false, u.lockedUntil = null, u.lastLoginAt = :now WHERE u.id = :id")
    void resetLoginAttempts(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE QorbitUser u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    void incrementFailedAttempts(@Param("id") Long id);

    @Modifying
    @Query("UPDATE QorbitUser u SET u.accountLocked = true, u.lockedUntil = :until WHERE u.id = :id")
    void lockAccount(@Param("id") Long id, @Param("until") LocalDateTime until);

    long countByRole(UserRole role);
}
