package com.qorbit.engine.auth.controller;

import com.qorbit.engine.auth.dto.MfaVerifyRequest;
import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.model.RiskLevel;
import com.qorbit.engine.auth.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/auth/mfa")
public class MfaController {

    private final MfaService      mfaService;
    private final AuthService     authService;
    private final AuditService    auditService;
    private final PasswordEncoder passwordEncoder;

    public MfaController(MfaService mfaService, AuthService authService,
                         AuditService auditService, PasswordEncoder passwordEncoder) {
        this.mfaService      = mfaService;
        this.authService     = authService;
        this.auditService    = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Verificação de MFA no login ───────────────────────────────────────────

    @GetMapping
    public String mfaPage(HttpSession session, Model model) {
        if (session.getAttribute(AuthService.MFA_PENDING_USER_ID) == null) {
            return "redirect:/auth/login";
        }
        model.addAttribute("mfaRequest", new MfaVerifyRequest("", false));
        return "auth/mfa";
    }

    @PostMapping
    public String verifyMfa(@Valid @ModelAttribute MfaVerifyRequest req,
                             BindingResult result,
                             HttpServletRequest httpReq,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttrs) {
        Long userId = (Long) session.getAttribute(AuthService.MFA_PENDING_USER_ID);
        if (userId == null) return "redirect:/auth/login";

        QorbitUser user = authService.findById(userId);
        String ip = AuditService.extractIp(httpReq);
        boolean valid;

        if (req.useBackupCode()) {
            valid = mfaService.verifyBackupCode(user, req.code());
        } else {
            valid = mfaService.verifyCode(user.getMfaSecret(), req.code());
        }

        if (!valid) {
            int attempts = user.getFailedMfaAttempts() + 1;
            user.setFailedMfaAttempts(attempts);
            int maxAttempts = user.getRole().isAdmin() ? 3 : 5;

            if (attempts >= maxAttempts) {
                session.invalidate();
                auditService.log(userId, user.getEmail(), "MFA_LOCKOUT", ip, null,
                    false, "MFA bloqueado após " + attempts + " tentativas", RiskLevel.HIGH);
                redirectAttrs.addFlashAttribute("error",
                    "Conta bloqueada por excesso de tentativas de MFA.");
                return "redirect:/auth/login";
            }

            auditService.log(userId, user.getEmail(), "MFA_FAILED", ip, null,
                false, "Tentativa " + attempts, RiskLevel.HIGH);
            model.addAttribute("error", "Código inválido. Tentativa " + attempts + "/" + maxAttempts + ".");
            model.addAttribute("mfaRequest", req);
            return "auth/mfa";
        }

        // MFA válido
        user.setFailedMfaAttempts(0);
        session.removeAttribute(AuthService.MFA_PENDING_USER_ID);
        authService.completeLogin(user, httpReq, false);

        auditService.log(userId, user.getEmail(), "LOGIN_SUCCESS_MFA", ip, null,
            true, null, RiskLevel.MEDIUM);

        return "redirect:/";
    }

    // ── Setup de MFA (perfil do usuário) ──────────────────────────────────────

    @GetMapping("/setup")
    public String setupPage(HttpSession session, Model model,
                             @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) return "redirect:/auth/login";
        String email = principal.getUsername();
        String secret = mfaService.generateSecret();
        session.setAttribute("MFA_SETUP_SECRET", secret);
        model.addAttribute("secret", secret);
        model.addAttribute("qrUri", mfaService.getQrImageUri(secret, email));
        model.addAttribute("mfaRequest", new MfaVerifyRequest("", false));
        return "auth/mfa-setup";
    }

    @PostMapping("/enable")
    public String enableMfa(@Valid @ModelAttribute MfaVerifyRequest req,
                             BindingResult result,
                             HttpSession session,
                             RedirectAttributes redirectAttrs,
                             @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) return "redirect:/auth/login";

        String secret = (String) session.getAttribute("MFA_SETUP_SECRET");
        if (secret == null) return "redirect:/auth/mfa/setup";

        if (!mfaService.verifyCode(secret, req.code())) {
            redirectAttrs.addFlashAttribute("error", "Código inválido. Tente novamente.");
            return "redirect:/auth/mfa/setup";
        }

        QorbitUser user = authService.findById(extractUserId(principal));
        List<String> backupCodes = mfaService.enableMfa(user, secret);
        session.removeAttribute("MFA_SETUP_SECRET");

        redirectAttrs.addFlashAttribute("backupCodes", backupCodes);
        redirectAttrs.addFlashAttribute("success", "MFA ativado com sucesso!");
        return "redirect:/auth/mfa/backup-codes";
    }

    @GetMapping("/backup-codes")
    public String backupCodesPage(Model model) {
        if (!model.containsAttribute("backupCodes")) {
            return "redirect:/";
        }
        return "auth/mfa-backup-codes";
    }

    @PostMapping("/disable")
    public String disableMfa(@RequestParam String currentPassword,
                              RedirectAttributes redirectAttrs,
                              @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) return "redirect:/auth/login";
        QorbitUser user = authService.findById(extractUserId(principal));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            redirectAttrs.addFlashAttribute("error", "Senha incorreta.");
            return "redirect:/perfil";
        }

        mfaService.disableMfa(user);
        redirectAttrs.addFlashAttribute("success", "MFA desativado.");
        return "redirect:/perfil";
    }

    private Long extractUserId(UserDetails principal) {
        return authService.findByEmail(principal.getUsername()).getId();
    }
}
