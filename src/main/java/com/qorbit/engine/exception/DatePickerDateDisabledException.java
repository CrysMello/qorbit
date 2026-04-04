package com.qorbit.engine.exception;

/**
 * Lançada quando a data alvo está presente no calendário mas marcada como
 * desabilitada (fora de intervalo permitido, bloqueada pelo sistema, etc.).
 * O sistema NÃO tenta clicar novamente nessa data.
 */
public class DatePickerDateDisabledException extends RuntimeException {

    public DatePickerDateDisabledException(String message) {
        super(message);
    }

    public DatePickerDateDisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}
