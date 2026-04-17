package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Executado pelo Spring Security após autenticação bem-sucedida.
 *
 * Se o usuário tem MFA ativado:
 *   - Remove o contexto autenticado da sessão (previne bypass do MFA)
 *   - Limpa o SecurityContextHolder
 *   - Armazena o userId na sessão como "MFA_PENDING"
 *   - Redireciona para /auth/mfa
 *
 * Se não tem MFA: delega ao SavedRequestAwareAuthenticationSuccessHandler
 * (respeita a URL salva antes do login ou redireciona para /).
 *
 * Nota: deliberadamente sem @Transactional — manter uma transação aberta
 * durante o filter chain segura a única conexão do pool (pool-size=1)
 * enquanto o redirect já foi enviado ao browser. O save via Spring Data
 * usa sua própria transação implícita.
 */
@Component
public class QorbitAuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final QorbitUserRepository userRepo;

    public QorbitAuthSuccessHandler(QorbitUserRepository userRepo) {
        this.userRepo = userRepo;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();
        QorbitUser user = userRepo.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            response.sendRedirect("/auth/login?error");
            return;
        }

        // Atualiza último login e reseta contadores.
        // Spring Data aplica sua própria @Transactional, liberando a conexão
        // imediatamente após o save — sem segurar o pool até o fim do handler.
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        userRepo.save(user);

        if (user.isMfaEnabled()) {
            // Remove o contexto autenticado da SESSÃO antes de limpar o holder.
            // Sem isso o contexto permanece no atributo SPRING_SECURITY_CONTEXT
            // e o usuário poderia navegar direto para "/" ignorando o MFA.
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                existing.removeAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
            SecurityContextHolder.clearContext();

            HttpSession session = request.getSession(true);
            session.setAttribute(AuthService.MFA_PENDING_USER_ID, user.getId());
            response.sendRedirect(request.getContextPath() + "/auth/mfa");
        } else {
            // Redireciona para a URL salva antes do login (se houver) ou para "/".
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
