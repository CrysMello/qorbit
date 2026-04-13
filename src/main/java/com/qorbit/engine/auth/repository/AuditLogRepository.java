package com.qorbit.engine.auth.repository;

import com.qorbit.engine.auth.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AuditLog> findByEmailAndActionAndCreatedAtAfter(
            String email, String action, LocalDateTime after);

    List<AuditLog> findTop20ByOrderByCreatedAtDesc();
}
