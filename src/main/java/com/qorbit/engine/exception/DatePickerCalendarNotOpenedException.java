package com.qorbit.engine.exception;

/**
 * Lançada quando o calendário visual de um datepicker não ficou visível
 * no DOM após tentativa de abertura (clique no campo ou ícone associado).
 */
public class DatePickerCalendarNotOpenedException extends RuntimeException {

    public DatePickerCalendarNotOpenedException(String message) {
        super(message);
    }

    public DatePickerCalendarNotOpenedException(String message, Throwable cause) {
        super(message, cause);
    }
}
