package com.qorbit.engine.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Token de recuperação de senha.
 *
 * Segurança:
 * - Token gerado com SecureRandom (256 bits, Base64 URL-safe) → não previsível
 * - Validade de 15 minutos
 * - Uso único (campo `used`)
 * - Um usuário pode ter no máximo 1 token ativo (invalidados ao gerar novo)
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
    @Index(name = "idx_prt_token", columnList = "token", unique = true),
    @Index(name = "idx_prt_user",  columnList = "user_id")
})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private QorbitUser user;

    @Column(unique = true, nullable = false, length = 512)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** IP de quem solicitou o reset (para auditoria). */
    @Column(length = 45)
    private String requestedFromIp;

    public Long getId()                        { return id; }
    public QorbitUser getUser()                { return user; }
    public void setUser(QorbitUser v)          { this.user = v; }
    public String getToken()                   { return token; }
    public void setToken(String v)             { this.token = v; }
    public LocalDateTime getExpiresAt()        { return expiresAt; }
    public void setExpiresAt(LocalDateTime v)  { this.expiresAt = v; }
    public boolean isUsed()                    { return used; }
    public void setUsed(boolean v)             { this.used = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public String getRequestedFromIp()         { return requestedFromIp; }
    public void setRequestedFromIp(String v)   { this.requestedFromIp = v; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }
}
