package com.qorbit.engine.exception;

/**
 * Lançada quando o dia alvo não é encontrado no calendário visível,
 * mesmo após a navegação para o mês e ano corretos.
 */
public class DatePickerDateNotFoundException extends RuntimeException {

    public DatePickerDateNotFoundException(String message) {
        super(message);
    }

    public DatePickerDateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
