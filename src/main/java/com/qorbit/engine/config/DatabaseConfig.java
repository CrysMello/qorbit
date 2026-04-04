package com.qorbit.engine.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class DatabaseConfig {

    @Value("${qorbit.db.path:db/qorbit.db}")
    private String dbPath;

    @PostConstruct
    public void ensureDbFolderExists() {
        try {
            Path path = Paths.get(dbPath).toAbsolutePath();
            Path parent = path.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }
            System.out.println("[Qorbit Engine] Banco configurado em: " + path);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível preparar o diretório do banco SQLite: " + e.getMessage(), e);
        }
    }
}
