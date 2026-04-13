package com.qorbit.engine.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Código deve ter 6 dígitos")
    String code,

    /** Se true, tenta usar código de backup (8 chars alfanuméricos) em vez do TOTP. */
    boolean useBackupCode
) {}
