package com.qorbit.engine.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidade central de usuário do Qorbit.
 *
 * Decisão de design: uma única tabela cobre usuários comuns e administradores,
 * diferenciados pelo campo `role`. Isso simplifica Spring Security (um único
 * UserDetailsService), queries e sessões. Campos de admin (expiração de senha,
 * histórico) ficam nulos para usuários comuns.
 */
@Entity
@Table(name = "qorbit_users", indexes = {
    @Index(name = "idx_user_email",  columnList = "email",  unique = true),
    @Index(name = "idx_user_active", columnList = "active")
})
public class QorbitUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    // ── Verificação de e-mail ─────────────────────────────────────────────────

    private boolean emailVerified = false;

    @Column(length = 255)
    private String emailVerificationToken;

    private LocalDateTime emailVerificationSentAt;

    // ── MFA (TOTP) ────────────────────────────────────────────────────────────

    private boolean mfaEnabled = false;

    /**
     * Secret TOTP em Base32 — gerado uma vez e armazenado para validar códigos.
     * RISCO: se o banco for comprometido, o secret expõe o MFA.
     * MITIGAÇÃO futura: criptografar este campo com AES-256 via @Convert + Jasypt.
     */
    @Column(length = 64)
    private String mfaSecret;

    // ── Bloqueio e tentativas falhas ──────────────────────────────────────────

    private boolean accountLocked = false;
    private LocalDateTime lockedUntil;

    /** Tentativas falhas de senha. Resetado após login bem-sucedido. */
    private int failedLoginAttempts = 0;

    /** Tentativas falhas de MFA (limite mais restritivo para admins). */
    private int failedMfaAttempts = 0;

    // ── Controle de senha e sessão ────────────────────────────────────────────

    private LocalDateTime lastLoginAt;

    /** Obriga troca de senha no próximo login (admins no primeiro acesso). */
    private boolean passwordMustChange = false;

    private LocalDateTime passwordChangedAt;

    /**
     * Expiração de senha. Null para usuários comuns (sem expiração).
     * Admins: 60-90 dias conforme role.
     */
    private LocalDateTime passwordExpiresAt;

    /**
     * Primeiro login — força setup de MFA e troca de senha para admins.
     * Inicia como true; setado false após completar o setup inicial.
     */
    private boolean firstLogin = true;

    // ── Metadados ─────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    private boolean active = true;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public String getEmail()                     { return email; }
    public void setEmail(String v)               { this.email = v; }
    public String getPasswordHash()              { return passwordHash; }
    public void setPasswordHash(String v)        { this.passwordHash = v; }
    public String getFullName()                  { return fullName; }
    public void setFullName(String v)            { this.fullName = v; }
    public UserRole getRole()                    { return role; }
    public void setRole(UserRole v)              { this.role = v; }
    public boolean isEmailVerified()             { return emailVerified; }
    public void setEmailVerified(boolean v)      { this.emailVerified = v; }
    public String getEmailVerificationToken()    { return emailVerificationToken; }
    public void setEmailVerificationToken(String v) { this.emailVerificationToken = v; }
    public LocalDateTime getEmailVerificationSentAt() { return emailVerificationSentAt; }
    public void setEmailVerificationSentAt(LocalDateTime v) { this.emailVerificationSentAt = v; }
    public boolean isMfaEnabled()                { return mfaEnabled; }
    public void setMfaEnabled(boolean v)         { this.mfaEnabled = v; }
    public String getMfaSecret()                 { return mfaSecret; }
    public void setMfaSecret(String v)           { this.mfaSecret = v; }
    public boolean isAccountLocked()             { return accountLocked; }
    public void setAccountLocked(boolean v)      { this.accountLocked = v; }
    public LocalDateTime getLockedUntil()        { return lockedUntil; }
    public void setLockedUntil(LocalDateTime v)  { this.lockedUntil = v; }
    public int getFailedLoginAttempts()          { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int v)    { this.failedLoginAttempts = v; }
    public int getFailedMfaAttempts()            { return failedMfaAttempts; }
    public void setFailedMfaAttempts(int v)      { this.failedMfaAttempts = v; }
    public LocalDateTime getLastLoginAt()        { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime v)  { this.lastLoginAt = v; }
    public boolean isPasswordMustChange()        { return passwordMustChange; }
    public void setPasswordMustChange(boolean v) { this.passwordMustChange = v; }
    public LocalDateTime getPasswordChangedAt()  { return passwordChangedAt; }
    public void setPasswordChangedAt(LocalDateTime v) { this.passwordChangedAt = v; }
    public LocalDateTime getPasswordExpiresAt()  { return passwordExpiresAt; }
    public void setPasswordExpiresAt(LocalDateTime v) { this.passwordExpiresAt = v; }
    public boolean isFirstLogin()                { return firstLogin; }
    public void setFirstLogin(boolean v)         { this.firstLogin = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public boolean isActive()                    { return active; }
    public void setActive(boolean v)             { this.active = v; }
}
