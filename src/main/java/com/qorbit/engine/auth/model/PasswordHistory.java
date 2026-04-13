package com.qorbit.engine.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Histórico de senhas — impede reutilização das últimas 24 senhas.
 * Aplicado apenas para contas administrativas.
 */
@Entity
@Table(name = "password_history", indexes = {
    @Index(name = "idx_ph_user", columnList = "user_id")
})
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private QorbitUser user;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId()                     { return id; }
    public QorbitUser getUser()             { return user; }
    public void setUser(QorbitUser v)       { this.user = v; }
    public String getPasswordHash()         { return passwordHash; }
    public void setPasswordHash(String v)   { this.passwordHash = v; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
}
