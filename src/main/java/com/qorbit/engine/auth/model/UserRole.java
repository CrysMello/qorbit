package com.qorbit.engine.auth.model;

/**
 * Hierarquia de papéis do Qorbit.
 *
 * USER        → usuário comum (acesso às próprias suites/testes)
 * MODERATOR   → visualiza todos os projetos, sem edição destrutiva
 * ADMIN       → gestão de usuários e projetos, MFA obrigatório
 * SUPER_ADMIN → tudo que ADMIN faz + configurações globais
 * OWNER       → acesso total, incluindo gestão de admins e dados de auditoria
 *
 * Regra: qualquer papel >= MODERATOR é considerado "administrativo"
 * e sujeito a requisitos de segurança mais rígidos (MFA obrigatório,
 * senha forte, expiração de senha, histórico de senhas).
 */
public enum UserRole {
    USER,
    MODERATOR,
    ADMIN,
    SUPER_ADMIN,
    OWNER;

    public boolean isAdmin() {
        return this == MODERATOR || this == ADMIN || this == SUPER_ADMIN || this == OWNER;
    }

    /** Número máximo de sessões simultâneas por nível. */
    public int maxConcurrentSessions() {
        return switch (this) {
            case USER       -> 5;
            case MODERATOR  -> 3;
            case ADMIN      -> 2;
            case SUPER_ADMIN -> 2;
            case OWNER      -> 1;
        };
    }

    /** Dias até a senha expirar (null = sem expiração para usuários comuns). */
    public Integer passwordExpiryDays() {
        return switch (this) {
            case USER       -> null;
            case MODERATOR  -> 90;
            case ADMIN      -> 60;
            case SUPER_ADMIN -> 60;
            case OWNER      -> 60;
        };
    }

    /** Tentativas falhas de senha antes do bloqueio. */
    public int maxLoginAttempts() {
        return switch (this) {
            case USER       -> 5;
            default         -> 3;
        };
    }

    /** Minutos de bloqueio após exceder tentativas. */
    public int lockoutMinutes() {
        return switch (this) {
            case USER       -> 15;
            default         -> 30;
        };
    }
}
