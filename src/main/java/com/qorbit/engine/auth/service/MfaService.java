package com.qorbit.engine.auth.service;

import com.qorbit.engine.auth.model.MfaBackupCode;
import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.MfaBackupCodeRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

/**
 * Gerencia TOTP (Time-Based One-Time Passwords) para MFA.
 *
 * Fluxo de ativação:
 *   1. generateSecret()    → gera e retorna secret Base32
 *   2. getQrImageUri()     → retorna data URI da imagem QR (para o app autenticador)
 *   3. verifyCode()        → valida código de 6 dígitos antes de ativar
 *   4. enableMfa()         → persiste secret e gera backup codes
 *
 * Fluxo de verificação no login:
 *   1. verifyCode() com secret do usuário
 *   2. Se falhar: verifyBackupCode() como fallback
 */
@Service
@Transactional
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 8;

    private final MfaBackupCodeRepository backupCodeRepo;
    private final PasswordEncoder encoder;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    public MfaService(MfaBackupCodeRepository backupCodeRepo, PasswordEncoder encoder) {
        this.backupCodeRepo = backupCodeRepo;
        this.encoder        = encoder;
    }

    /** Gera um novo secret TOTP. Não persiste — deve ser confirmado com verifyCode antes de salvar. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** Retorna URI de dados da imagem QR para configuração no app autenticador. */
    public String getQrImageUri(String secret, String email) {
        QrData data = new QrData.Builder()
            .label(email)
            .secret(secret)
            .issuer("Qorbit")
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();
        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(data);
            String mimeType  = generator.getImageMimeType();
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException e) {
            log.warn("[MFA] Falha ao gerar QR: {}", e.getMessage());
            return "";
        }
    }

    /** Verifica se o código TOTP de 6 dígitos é válido para o secret fornecido. */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        return codeVerifier.isValidCode(secret, code);
    }

    /**
     * Ativa MFA para o usuário: persiste o secret e gera 10 códigos de backup.
     * Retorna os códigos em plaintext — mostrar UMA ÚNICA VEZ ao usuário.
     */
    public List<String> enableMfa(QorbitUser user, String secret) {
        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        user.setFailedMfaAttempts(0);

        // Remove backup codes anteriores (caso esteja reativando)
        backupCodeRepo.deleteAllByUser(user);

        // Gera 10 novos backup codes
        List<String> plainCodes = new ArrayList<>();
        SecureRandom rng = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem caracteres ambíguos

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                code.append(chars.charAt(rng.nextInt(chars.length())));
            }
            String plainCode = code.toString();
            plainCodes.add(plainCode);

            MfaBackupCode backup = new MfaBackupCode();
            backup.setUser(user);
            backup.setCodeHash(encoder.encode(plainCode));
            backupCodeRepo.save(backup);
        }

        return plainCodes;
    }

    /** Desativa MFA e remove todos os backup codes. */
    public void disableMfa(QorbitUser user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setFailedMfaAttempts(0);
        backupCodeRepo.deleteAllByUser(user);
    }

    /**
     * Verifica um código de backup. Se válido, marca como usado.
     * Retorna true se o código foi aceito.
     */
    public boolean verifyBackupCode(QorbitUser user, String code) {
        if (code == null || code.isBlank()) return false;
        String normalizedCode = code.toUpperCase().replaceAll("[^A-Z0-9]", "");

        return backupCodeRepo.findByUserAndUsedFalse(user).stream()
            .filter(bc -> encoder.matches(normalizedCode, bc.getCodeHash()))
            .findFirst()
            .map(bc -> {
                bc.setUsed(true);
                bc.setUsedAt(java.time.LocalDateTime.now());
                backupCodeRepo.save(bc);
                return true;
            })
            .orElse(false);
    }

    public long countRemainingBackupCodes(QorbitUser user) {
        return backupCodeRepo.countByUserAndUsedFalse(user);
    }
}
