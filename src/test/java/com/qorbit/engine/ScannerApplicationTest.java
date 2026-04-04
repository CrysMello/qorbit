package com.qorbit.engine;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica que o contexto Spring Boot sobe corretamente com todas as
 * configurações do perfil de teste (H2 + sem WebSocket real).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ScannerApplication — Teste de contexto")
class ScannerApplicationTest {

    @Test
    @DisplayName("Contexto Spring Boot deve subir sem erros")
    void contextLoads() {
        // Se o contexto não subir, este teste falha automaticamente
    }
}
