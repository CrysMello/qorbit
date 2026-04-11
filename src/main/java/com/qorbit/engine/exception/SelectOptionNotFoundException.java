package com.qorbit.engine.exception;

/**
 * Lançada quando a opção específica não é encontrada no <select> ou combobox.
 */
public class SelectOptionNotFoundException extends RuntimeException {

    public SelectOptionNotFoundException(String message) {
        super(message);
    }

    public SelectOptionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
