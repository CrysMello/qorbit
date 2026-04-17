package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.model.UserRole;
import com.qorbit.engine.auth.repository.PasswordHistoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Valida senhas conforme o perfil do usuário.
 *
 * Usuário comum: mínimo 8 caracteres.
 * Admin:        mínimo 14 caracteres, 3 de 4 tipos (upper/lower/digit/special),
 *               não pode repetir as últimas 24 senhas.
 */
@Service
public class PasswordValidatorService {

    private final PasswordHistoryRepository historyRepo;
    private final PasswordEncoder encoder;

    public PasswordValidatorService(PasswordHistoryRepository historyRepo,
                                    PasswordEncoder encoder) {
        this.historyRepo = historyRepo;
        this.encoder     = encoder;
    }

    public void validate(String password, QorbitUser user) {
        if (user.getRole().isAdmin()) {
            validateAdmin(password, user);
        } else {
            validateUser(password);
        }
    }

    private void validateUser(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Senha deve ter no mínimo 8 caracteres.");
        }
    }

    private void validateAdmin(String password, QorbitUser user) {
        if (password == null || password.length() < 14) {
            throw new IllegalArgumentException("Senha administrativa deve ter no mínimo 14 caracteres.");
        }

        int types = 0;
        if (password.chars().anyMatch(Character::isUpperCase))          types++;
        if (password.chars().anyMatch(Character::isLowerCase))          types++;
        if (password.chars().anyMatch(Character::isDigit))              types++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) types++;

        if (types < 3) {
            throw new IllegalArgumentException(
                "Senha administrativa deve conter pelo menos 3 dos seguintes: " +
                "maiúsculas, minúsculas, números, caracteres especiais.");
        }

        // Verifica histórico (últimas 24 senhas)
        if (user.getId() != null) {
            boolean reused = historyRepo
                .findTop24ByUserOrderByCreatedAtDesc(user)
                .stream()
                .anyMatch(h -> encoder.matches(password, h.getPasswordHash()));
            if (reused) {
                throw new IllegalArgumentException(
                    "A nova senha não pode ser igual a uma das últimas 24 senhas utilizadas.");
            }
        }
    }
}
