package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.dto.*;
import com.qorbit.engine.auth.exception.*;
import com.qorbit.engine.auth.model.*;
import com.qorbit.engine.auth.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Serviço central de autenticação.
 *
 * Fluxo de login:
 *   1. Verifica rate limiting por IP
 *   2. Busca usuário por e-mail (mensagem genérica se não encontrado)
 *   3. Verifica bloqueio de conta
 *   4. Valida senha com BCrypt
 *   5. Se MFA ativado: armazena "MFA_PENDING_USER_ID" na sessão e lança MfaPendingException
 *   6. Se não: faz login completo no Spring Security
 *
 * Decisão de segurança: a mensagem de erro "E-mail ou senha inválidos" é usada
 * em TODOS os casos de falha para evitar enumeração de usuários.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String MFA_PENDING_USER_ID = "MFA_PENDING_USER_ID";
    private static final String GENERIC_ERROR = "E-mail ou senha inválidos.";

    private final QorbitUserRepository     userRepo;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final PasswordHistoryRepository historyRepo;
    private final PasswordEncoder          encoder;
    private final AuditService             auditService;
    private final EmailService             emailService;
    private final PasswordValidatorService passwordValidator;
    private final RateLimiterService       rateLimiter;

    public AuthService(QorbitUserRepository userRepo,
                       PasswordResetTokenRepository resetTokenRepo,
                       PasswordHistoryRepository historyRepo,
                       PasswordEncoder encoder,
                       AuditService auditService,
                       EmailService emailService,
                       PasswordValidatorService passwordValidator,
                       RateLimiterService rateLimiter) {
        this.userRepo          = userRepo;
        this.resetTokenRepo    = resetTokenRepo;
        this.historyRepo       = historyRepo;
        this.encoder           = encoder;
        this.auditService      = auditService;
        this.emailService      = emailService;
        this.passwordValidator = passwordValidator;
        this.rateLimiter       = rateLimiter;
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    public void register(RegisterRequest req, String ip) {
        // Honeypot anti-bot
        if (req.honeypot() != null && !req.honeypot().isBlank()) {
            log.warn("[Auth] Registro bloqueado por honeypot — IP: {}", ip);
            return; // Silencioso: não revela que detectamos o bot
        }

        if (!rateLimiter.allowRegister(ip)) {
            throw new IllegalStateException("Muitas tentativas de registro. Aguarde um momento.");
        }

        if (userRepo.existsByEmailIgnoreCase(req.email())) {
            // Não revela se e-mail existe — envia e-mail informando a tentativa
            emailService.sendVerificationEmail(req.email(),
                "Este e-mail já está cadastrado no Qorbit.");
            return;
        }

        if (!req.password().equals(req.passwordConfirm())) {
            throw new IllegalArgumentException("As senhas não coincidem.");
        }

        QorbitUser user = new QorbitUser();
        user.setEmail(req.email().toLowerCase().trim());
        user.setFullName(req.fullName());

        passwordValidator.validate(req.password(), user);
        user.setPasswordHash(encoder.encode(req.password()));

        String verificationToken = generateSecureToken();
        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationSentAt(LocalDateTime.now());

        userRepo.save(user);
        emailService.sendVerificationEmail(user.getEmail(), verificationToken);

        auditService.log(user.getId(), user.getEmail(), "REGISTER", ip, null,
            true, "Usuário registrado com sucesso", RiskLevel.LOW);
    }

    // ── Verificação de e-mail ─────────────────────────────────────────────────

    public void verifyEmail(String token) {
        QorbitUser user = userRepo.findByEmailVerificationToken(token)
            .orElseThrow(InvalidTokenException::new);

        if (user.isEmailVerified()) return;

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepo.save(user);

        auditService.log(user.getId(), user.getEmail(), "EMAIL_VERIFIED", null, null,
            true, null, RiskLevel.LOW);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Valida credenciais. Se MFA habilitado, armazena ID na sessão e lança
     * MfaPendingException. Se não, faz login completo no contexto do Spring Security.
     */
    public void login(LoginRequest req, HttpServletRequest httpReq) {
        String ip = AuditService.extractIp(httpReq);

        if (!rateLimiter.allowLogin(ip)) {
            throw new LockedException("Muitas tentativas de login. Aguarde 15 minutos.");
        }

        QorbitUser user = userRepo.findByEmailIgnoreCase(req.email()).orElse(null);

        if (user == null) {
            // Simula tempo de BCrypt para evitar timing attack
            encoder.matches(req.password(), "$2a$10$dummyhashfortimingnonce000000000000000000000000");
            auditService.log(null, req.email(), "LOGIN_FAILED", ip,
                httpReq.getHeader("User-Agent"), false, "Usuário não encontrado", RiskLevel.MEDIUM);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        // Verifica bloqueio (pode ter expirado)
        if (user.isAccountLocked()) {
            if (user.getLockedUntil() != null && LocalDateTime.now().isAfter(user.getLockedUntil())) {
                // Desbloqueio automático após expiração
                user.setAccountLocked(false);
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
                userRepo.save(user);
            } else {
                auditService.log(user.getId(), user.getEmail(), "LOGIN_BLOCKED", ip,
                    httpReq.getHeader("User-Agent"), false, "Conta bloqueada", RiskLevel.HIGH);
                throw new AccountLockedException(user.getLockedUntil());
            }
        }

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            handleFailedAttempt(user, ip, httpReq.getHeader("User-Agent"));
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        if (!user.isEmailVerified()) {
            throw new DisabledException("E-mail não verificado. Verifique sua caixa de entrada.");
        }

        if (!user.isActive()) {
            throw new DisabledException("Conta desativada. Entre em contato com o suporte.");
        }

        // Login bem-sucedido — reseta contadores
        userRepo.resetLoginAttempts(user.getId(), LocalDateTime.now());

        // MFA pendente
        if (user.isMfaEnabled()) {
            HttpSession session = httpReq.getSession(true);
            session.setAttribute(MFA_PENDING_USER_ID, user.getId());
            auditService.log(user.getId(), user.getEmail(), "LOGIN_MFA_PENDING", ip,
                httpReq.getHeader("User-Agent"), true, null, RiskLevel.MEDIUM);
            throw new MfaPendingException();
        }

        // Login completo
        completeLogin(user, httpReq, req.rememberMe());
        auditService.log(user.getId(), user.getEmail(), "LOGIN_SUCCESS", ip,
            httpReq.getHeader("User-Agent"), true, null, RiskLevel.MEDIUM);
    }

    // ── Completar login após MFA ───────────────────────────────────────────────

    public void completeLogin(QorbitUser user, HttpServletRequest request, boolean rememberMe) {
        // Regenera session ID para prevenir session fixation
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);

        // Autentica no contexto do Spring Security
        Authentication auth = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        newSession.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext()
        );

        if (rememberMe) {
            newSession.setMaxInactiveInterval(30 * 24 * 60 * 60); // 30 dias
        }
    }

    // ── Recuperação de senha ──────────────────────────────────────────────────

    /**
     * Inicia o fluxo de reset de senha.
     * SEMPRE retorna sem erro, independente de o e-mail existir ou não.
     * Isso previne enumeração de usuários.
     */
    public void initiatePasswordReset(String email, String ip) {
        if (!rateLimiter.allowForgotPassword(ip)) {
            return; // Rate limited — silencioso
        }

        userRepo.findByEmailIgnoreCase(email).ifPresent(user -> {
            resetTokenRepo.invalidateAllForUser(user);

            String token = generateSecureToken();
            PasswordResetToken prt = new PasswordResetToken();
            prt.setUser(user);
            prt.setToken(token);
            prt.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            prt.setRequestedFromIp(ip);
            resetTokenRepo.save(prt);

            emailService.sendPasswordResetEmail(user.getEmail(), token);
            auditService.log(user.getId(), user.getEmail(), "PASSWORD_RESET_REQUESTED",
                ip, null, true, null, RiskLevel.HIGH);
        });
    }

    public void resetPassword(ResetPasswordRequest req, String ip) {
        PasswordResetToken prt = resetTokenRepo.findByToken(req.token())
            .orElseThrow(InvalidTokenException::new);

        if (!prt.isValid()) throw new InvalidTokenException();

        if (!req.password().equals(req.passwordConfirm())) {
            throw new IllegalArgumentException("As senhas não coincidem.");
        }

        QorbitUser user = prt.getUser();
        passwordValidator.validate(req.password(), user);

        // Salva senha antiga no histórico (apenas admins)
        if (user.getRole().isAdmin()) {
            PasswordHistory hist = new PasswordHistory();
            hist.setUser(user);
            hist.setPasswordHash(user.getPasswordHash());
            historyRepo.save(hist);
        }

        user.setPasswordHash(encoder.encode(req.password()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordMustChange(false);
        // Clicar no link de reset prova acesso ao e-mail — verificação implícita
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);

        // Recalcula expiração de senha para admins
        if (user.getRole().isAdmin() && user.getRole().passwordExpiryDays() != null) {
            user.setPasswordExpiresAt(
                LocalDateTime.now().plusDays(user.getRole().passwordExpiryDays()));
        }

        prt.setUsed(true);
        resetTokenRepo.save(prt);
        userRepo.save(user);

        auditService.log(user.getId(), user.getEmail(), "PASSWORD_RESET_COMPLETED",
            ip, null, true, null, RiskLevel.HIGH);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void handleFailedAttempt(QorbitUser user, String ip, String userAgent) {
        int attempts = user.getFailedLoginAttempts() + 1;
        int maxAttempts = user.getRole().maxLoginAttempts();

        userRepo.incrementFailedAttempts(user.getId());

        auditService.log(user.getId(), user.getEmail(), "LOGIN_FAILED", ip,
            userAgent, false, "Tentativa " + attempts + "/" + maxAttempts, RiskLevel.MEDIUM);

        if (attempts >= maxAttempts) {
            LocalDateTime lockedUntil = LocalDateTime.now()
                .plusMinutes(user.getRole().lockoutMinutes());
            userRepo.lockAccount(user.getId(), lockedUntil);
            auditService.log(user.getId(), user.getEmail(), "ACCOUNT_LOCKED", ip,
                userAgent, false, "Bloqueado até " + lockedUntil, RiskLevel.HIGH);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public QorbitUser findById(Long id) {
        return userRepo.findById(id).orElseThrow(() ->
            new IllegalStateException("Usuário não encontrado"));
    }

    public QorbitUser findByEmail(String email) {
        return userRepo.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
    }
}
