package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Executado pelo Spring Security após autenticação bem-sucedida.
 *
 * Se o usuário tem MFA ativado:
 *   - Limpa o SecurityContext (não autentica ainda)
 *   - Armazena o userId na sessão como "MFA_PENDING"
 *   - Redireciona para /auth/mfa
 *
 * Se não tem MFA: redireciona para /.
 */
@Component
public class QorbitAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final QorbitUserRepository userRepo;

    public QorbitAuthSuccessHandler(QorbitUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String email = authentication.getName();
        QorbitUser user = userRepo.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            response.sendRedirect("/auth/login?error");
            return;
        }

        // Atualiza último login e reseta contadores
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        userRepo.save(user);

        if (user.isMfaEnabled()) {
            // Suspende a autenticação — será completada após o código TOTP
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(true);
            session.setAttribute(AuthService.MFA_PENDING_USER_ID, user.getId());
            response.sendRedirect(request.getContextPath() + "/auth/mfa");
        } else {
            response.sendRedirect(request.getContextPath() + "/");
        }
    }
}
