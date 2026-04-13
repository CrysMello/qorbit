package com.qorbit.engine.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de auditoria de todas as ações de autenticação.
 *
 * Imutável por design: não há setters após criação.
 * Use o construtor ou o builder via AuthLogBuilder.
 */
@Entity
@Table(name = "auth_audit_logs", indexes = {
    @Index(name = "idx_aal_user",    columnList = "user_id"),
    @Index(name = "idx_aal_email",   columnList = "email"),
    @Index(name = "idx_aal_created", columnList = "created_at"),
    @Index(name = "idx_aal_action",  columnList = "action")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pode ser null para ações pré-autenticação (ex: tentativa com e-mail inexistente). */
    private Long userId;

    @Column(length = 255)
    private String email;

    /** Ex: LOGIN_SUCCESS, LOGIN_FAILED, MFA_ENABLED, PASSWORD_RESET, LOGOUT */
    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    private boolean success;

    @Column(length = 1024)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AuditLog() {}

    public AuditLog(Long userId, String email, String action,
                    String ipAddress, String userAgent,
                    boolean success, String details, RiskLevel riskLevel) {
        this.userId    = userId;
        this.email     = email;
        this.action    = action;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success   = success;
        this.details   = details;
        this.riskLevel = riskLevel;
    }

    public Long getId()            { return id; }
    public Long getUserId()        { return userId; }
    public String getEmail()       { return email; }
    public String getAction()      { return action; }
    public String getIpAddress()   { return ipAddress; }
    public String getUserAgent()   { return userAgent; }
    public boolean isSuccess()     { return success; }
    public String getDetails()     { return details; }
    public RiskLevel getRiskLevel(){ return riskLevel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
