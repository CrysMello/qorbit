package com.qorbit.engine.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Código de backup para MFA.
 *
 * Gerados 10 por ativação de MFA. Mostrados ao usuário UMA ÚNICA VEZ.
 * Armazenados como hash BCrypt (nunca em plaintext).
 * Cada código só pode ser usado uma vez.
 */
@Entity
@Table(name = "mfa_backup_codes", indexes = {
    @Index(name = "idx_mbc_user", columnList = "user_id")
})
public class MfaBackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private QorbitUser user;

    /** Hash BCrypt do código. Nunca armazenar plaintext. */
    @Column(nullable = false, length = 255)
    private String codeHash;

    private boolean used = false;
    private LocalDateTime usedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                      { return id; }
    public QorbitUser getUser()              { return user; }
    public void setUser(QorbitUser v)        { this.user = v; }
    public String getCodeHash()              { return codeHash; }
    public void setCodeHash(String v)        { this.codeHash = v; }
    public boolean isUsed()                  { return used; }
    public void setUsed(boolean v)           { this.used = v; }
    public LocalDateTime getUsedAt()         { return usedAt; }
    public void setUsedAt(LocalDateTime v)   { this.usedAt = v; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
}
