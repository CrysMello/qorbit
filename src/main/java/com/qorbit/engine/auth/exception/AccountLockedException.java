package com.qorbit.engine.auth.exception;

import java.time.LocalDateTime;

public class AccountLockedException extends RuntimeException {
    private final LocalDateTime lockedUntil;

    public AccountLockedException(LocalDateTime lockedUntil) {
        super("Conta bloqueada temporariamente por excesso de tentativas.");
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
}
