package com.qorbit.engine.exception;

/**
 * Lançada quando o elemento <select> ou combobox não é encontrado.
 */
public class SelectElementNotFoundException extends RuntimeException {

    public SelectElementNotFoundException(String message) {
        super(message);
    }

    public SelectElementNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
