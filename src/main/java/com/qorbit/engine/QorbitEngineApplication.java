package com.qorbit.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Exclui a auto-configuração de UserDetailsService do Spring Security.
// O Qorbit não usa autenticação obrigatória — acesso local sem login.
// Sem essa exclusão, o Spring tenta criar um usuário/senha automático
// que conflita com o SecurityConfig e quebra o contexto de testes.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class QorbitEngineApplication {

    public static void main(String[] args) {
        prepararInfraBanco();
        SpringApplication.run(QorbitEngineApplication.class, args);
        System.out.println("====================================");
        System.out.println("  Qorbit iniciado!");
        System.out.println("  Acesse: http://localhost:18080");
        System.out.println("====================================");
    }

    private static void prepararInfraBanco() {
        String dbPath = System.getenv("QORBIT_DB_PATH");
        if (dbPath == null || dbPath.isBlank()) {
            dbPath = "db/qorbit.db";
        }

        try {
            Path dbFile = Paths.get(dbPath).toAbsolutePath().normalize();
            Path parent = dbFile.getParent();

            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
                System.out.println("[Qorbit Engine] Pasta do banco criada: " + parent);
            }

            if (Files.notExists(dbFile)) {
                Files.createFile(dbFile);
                System.out.println("[Qorbit Engine] Arquivo do banco criado: " + dbFile);
            }

            System.setProperty("QORBIT_DB_PATH", dbFile.toString());
            System.out.println("[Qorbit Engine] Banco configurado em: " + dbFile);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível preparar a infraestrutura do banco SQLite em '" + dbPath + "': " + e.getMessage(), e);
        }
    }
}
