package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.AuditLog;
import com.qorbit.engine.auth.model.RiskLevel;
import com.qorbit.engine.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste eventos de autenticação para rastreabilidade.
 * Usa REQUIRES_NEW para garantir que logs sejam salvos mesmo se a transação
 * principal fizer rollback (ex: login falhou, mas o log deve persistir).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String email, String action,
                    String ip, String userAgent,
                    boolean success, String details, RiskLevel risk) {
        try {
            repo.save(new AuditLog(userId, email, action, ip, userAgent, success, details, risk));
        } catch (Exception e) {
            // Auditoria nunca deve quebrar o fluxo principal
            log.error("[Audit] Falha ao salvar log de auditoria: {}", e.getMessage());
        }
    }

    /** Extrai IP real considerando proxies reversos (X-Forwarded-For). */
    public static String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
