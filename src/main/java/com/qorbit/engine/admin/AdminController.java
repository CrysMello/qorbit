package com.qorbit.engine.admin;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.model.UserRole;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.auth.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final QorbitUserRepository userRepo;
    private final PasswordEncoder      encoder;
    private final AuditService         auditService;

    public AdminController(QorbitUserRepository userRepo,
                           PasswordEncoder encoder,
                           AuditService auditService) {
        this.userRepo     = userRepo;
        this.encoder      = encoder;
        this.auditService = auditService;
    }

    // ── Listagem ──────────────────────────────────────────────────────────────

    @GetMapping("/usuarios")
    public String listUsers(Model model) {
        List<QorbitUser> users = userRepo.findAll();
        long activeCount = users.stream().filter(QorbitUser::isActive).count();
        long adminCount  = users.stream().filter(u -> u.getRole().isAdmin()).count();
        model.addAttribute("users", users);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("roles", UserRole.values());
        return "admin/usuarios";
    }

    // ── Alterar role ──────────────────────────────────────────────────────────

    @PostMapping("/usuarios/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam String role,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttrs) {
        QorbitUser user = userRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        UserRole newRole;
        try {
            newRole = UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", "Perfil inválido: " + role);
            return "redirect:/admin/usuarios";
        }

        UserRole oldRole = user.getRole();
        user.setRole(newRole);
        userRepo.save(user);

        String ip = AuditService.extractIp(request);
        auditService.log(user.getId(), user.getEmail(), "ADMIN_ROLE_CHANGE", ip,
            null, true, oldRole + " → " + newRole,
            com.qorbit.engine.auth.model.RiskLevel.HIGH);

        redirectAttrs.addFlashAttribute("success",
            "Perfil de " + user.getEmail() + " alterado para " + newRole + ".");
        return "redirect:/admin/usuarios";
    }

    // ── Ativar / desativar ────────────────────────────────────────────────────

    @PostMapping("/usuarios/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttrs) {
        QorbitUser user = userRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        // Impede desativar o próprio OWNER/SUPER_ADMIN logado
        String loggedEmail = request.getUserPrincipal() != null
            ? request.getUserPrincipal().getName() : "";
        if (user.getEmail().equalsIgnoreCase(loggedEmail)) {
            redirectAttrs.addFlashAttribute("error", "Você não pode alterar seu próprio status.");
            return "redirect:/admin/usuarios";
        }

        boolean nowActive = !user.isActive();
        user.setActive(nowActive);
        userRepo.save(user);

        String action = nowActive ? "ADMIN_USER_ACTIVATED" : "ADMIN_USER_DEACTIVATED";
        auditService.log(user.getId(), user.getEmail(), action,
            AuditService.extractIp(request), null, true, null,
            com.qorbit.engine.auth.model.RiskLevel.HIGH);

        redirectAttrs.addFlashAttribute("success",
            user.getEmail() + (nowActive ? " ativado." : " desativado."));
        return "redirect:/admin/usuarios";
    }

    // ── Reset de senha ────────────────────────────────────────────────────────

    @PostMapping("/usuarios/{id}/reset-senha")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String novaSenha,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttrs) {
        if (novaSenha == null || novaSenha.length() < 8) {
            redirectAttrs.addFlashAttribute("error", "Senha deve ter no mínimo 8 caracteres.");
            return "redirect:/admin/usuarios";
        }

        QorbitUser user = userRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setPasswordHash(encoder.encode(novaSenha));
        user.setPasswordMustChange(true);
        userRepo.save(user);

        auditService.log(user.getId(), user.getEmail(), "ADMIN_PASSWORD_RESET",
            AuditService.extractIp(request), null, true, "Reset administrativo",
            com.qorbit.engine.auth.model.RiskLevel.HIGH);

        redirectAttrs.addFlashAttribute("success",
            "Senha de " + user.getEmail() + " redefinida. Usuário deverá trocá-la no próximo login.");
        return "redirect:/admin/usuarios";
    }
}
