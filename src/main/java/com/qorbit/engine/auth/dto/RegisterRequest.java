package com.qorbit.engine.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 120, message = "Nome máximo 120 caracteres")
    String fullName,

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 255)
    String email,

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 128, message = "Senha deve ter no mínimo 8 caracteres")
    String password,

    @NotBlank(message = "Confirmação de senha é obrigatória")
    String passwordConfirm,

    /** Campo honeypot — deve estar vazio. Bots costumam preenchê-lo. */
    String honeypot
) {}
