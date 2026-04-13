package com.qorbit.engine.auth.controller;

import com.qorbit.engine.auth.dto.*;
import com.qorbit.engine.auth.exception.*;
import com.qorbit.engine.auth.service.AuthService;
import com.qorbit.engine.auth.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                             @RequestParam(required = false) String logout,
                             Model model) {
        if (error != null)  model.addAttribute("error", "E-mail ou senha inválidos.");
        if (logout != null) model.addAttribute("info", "Sessão encerrada com sucesso.");
        model.addAttribute("loginRequest", new LoginRequest("", "", false));
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest req,
                        BindingResult result,
                        HttpServletRequest httpReq,
                        Model model,
                        RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("error", "Preencha e-mail e senha.");
            return "auth/login";
        }
        try {
            authService.login(req, httpReq);
            return "redirect:/";
        } catch (MfaPendingException e) {
            return "redirect:/auth/mfa";
        } catch (AccountLockedException e) {
            model.addAttribute("error",
                "Conta bloqueada temporariamente. Tente novamente mais tarde.");
            return "auth/login";
        } catch (DisabledException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/login";
        } catch (LockedException | BadCredentialsException e) {
            model.addAttribute("error", "E-mail ou senha inválidos.");
            return "auth/login";
        } catch (Exception e) {
            model.addAttribute("error", "E-mail ou senha inválidos.");
            return "auth/login";
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/auth/login?logout";
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest",
            new RegisterRequest("", "", "", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest req,
                           BindingResult result,
                           HttpServletRequest httpReq,
                           Model model,
                           RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("registerRequest", req);
            return "auth/register";
        }
        try {
            String ip = AuditService.extractIp(httpReq);
            authService.register(req, ip);
            redirectAttrs.addFlashAttribute("success",
                "Conta criada! Verifique seu e-mail para ativar.");
            return "redirect:/auth/login";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("registerRequest", req);
            return "auth/register";
        }
    }

    // ── Verificação de e-mail ─────────────────────────────────────────────────

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token,
                              RedirectAttributes redirectAttrs) {
        try {
            authService.verifyEmail(token);
            redirectAttrs.addFlashAttribute("success",
                "E-mail verificado! Faça login.");
        } catch (InvalidTokenException e) {
            redirectAttrs.addFlashAttribute("error",
                "Link inválido ou expirado.");
        }
        return "redirect:/auth/login";
    }

    // ── Recuperação de senha ──────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("forgotRequest", new ForgotPasswordRequest(""));
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute ForgotPasswordRequest req,
                                  BindingResult result,
                                  HttpServletRequest httpReq,
                                  Model model,
                                  RedirectAttributes redirectAttrs) {
        if (!result.hasErrors()) {
            authService.initiatePasswordReset(req.email(), AuditService.extractIp(httpReq));
        }
        // Sempre mostra a mesma mensagem para evitar enumeração
        redirectAttrs.addFlashAttribute("success",
            "Se este e-mail estiver cadastrado, você receberá as instruções em instantes.");
        return "redirect:/auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("resetRequest",
            new ResetPasswordRequest(token, "", ""));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute ResetPasswordRequest req,
                                 BindingResult result,
                                 HttpServletRequest httpReq,
                                 Model model,
                                 RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("token", req.token());
            return "auth/reset-password";
        }
        try {
            authService.resetPassword(req, AuditService.extractIp(httpReq));
            redirectAttrs.addFlashAttribute("success",
                "Senha redefinida com sucesso! Faça login.");
            return "redirect:/auth/login";
        } catch (InvalidTokenException e) {
            model.addAttribute("error", "Link inválido ou expirado. Solicite um novo.");
            model.addAttribute("token", req.token());
            return "auth/reset-password";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("token", req.token());
            return "auth/reset-password";
        }
    }
}
