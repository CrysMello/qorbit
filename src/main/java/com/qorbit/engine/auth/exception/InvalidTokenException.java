package com.qorbit.engine.auth.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Token inválido ou expirado.");
    }
}
