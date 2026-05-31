package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.model.UserRole;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Cria o primeiro SUPER_ADMIN ao subir a aplicação, caso não exista nenhum.
 *
 * Executa apenas UMA vez: se já houver qualquer usuário com role SUPER_ADMIN
 * ou OWNER no banco, não faz nada.
 *
 * Configuração via application.properties ou variáveis de ambiente:
 *   qorbit.super-admin.email    (padrão: admin@qorbit.local)
 *   qorbit.super-admin.name     (padrão: Super Admin)
 *   qorbit.super-admin.password (OBRIGATÓRIO em produção — sem padrão seguro)
 *
 * O usuário é criado com:
 *   - emailVerified = true  (pula confirmação de e-mail)
 *   - passwordMustChange = true  (força troca de senha no primeiro login)
 *   - firstLogin = true
 *   - role = SUPER_ADMIN
 */
@Component
public class SuperAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final QorbitUserRepository userRepo;
    private final PasswordEncoder      encoder;

    @Value("${qorbit.super-admin.email:admin@qorbit.local}")
    private String adminEmail;

    @Value("${qorbit.super-admin.name:Super Admin}")
    private String adminName;

    /**
     * Senha temporária padrão — DEVE ser trocada no primeiro login.
     * Em produção, defina via variável de ambiente QORBIT_SUPER_ADMIN_PASSWORD.
     */
    @Value("${qorbit.super-admin.password:Qorbit@Admin#2025!}")
    private String adminPassword;

    public SuperAdminInitializer(QorbitUserRepository userRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder  = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Super-admin email/senha não configurados — provisionamento automático ignorado.");
            return;
        }

        // Remove usuários fantasma (SUPER_ADMIN com email vazio) criados por bug anterior
        userRepo.findAll().stream()
            .filter(u -> (u.getRole() == UserRole.SUPER_ADMIN || u.getRole() == UserRole.OWNER)
                      && (u.getEmail() == null || u.getEmail().isBlank()))
            .forEach(userRepo::delete);

        String targetEmail = adminEmail.toLowerCase().trim();

        // Se o email configurado já existe como SUPER_ADMIN ou OWNER, não recria
        boolean alreadyExists = userRepo.findByEmailIgnoreCase(targetEmail)
            .map(u -> u.getRole() == UserRole.SUPER_ADMIN || u.getRole() == UserRole.OWNER)
            .orElse(false);

        if (alreadyExists) {
            return;
        }

        QorbitUser admin = userRepo.findByEmailIgnoreCase(targetEmail)
            .orElse(new QorbitUser());

        admin.setEmail(targetEmail);
        admin.setFullName(adminName);
        admin.setPasswordHash(encoder.encode(adminPassword));
        admin.setRole(UserRole.SUPER_ADMIN);
        admin.setEmailVerified(true);
        admin.setPasswordMustChange(false);  // credenciais já são as definitivas
        admin.setFirstLogin(false);
        admin.setActive(true);
        admin.setPasswordChangedAt(LocalDateTime.now());
        admin.setPasswordExpiresAt(
            LocalDateTime.now().plusDays(UserRole.SUPER_ADMIN.passwordExpiryDays()));

        userRepo.save(admin);

        log.warn("=========================================================");
        log.warn("  SUPER_ADMIN provisionado: {}", targetEmail);
        log.warn("=========================================================");
    }
}
