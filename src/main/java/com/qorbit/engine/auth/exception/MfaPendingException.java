package com.qorbit.engine.auth.exception;

public class MfaPendingException extends RuntimeException {
    public MfaPendingException() {
        super("MFA verification required.");
    }
}
