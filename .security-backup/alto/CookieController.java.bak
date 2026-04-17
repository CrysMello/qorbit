package com.qorbit.engine.controller;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/cookies")
public class CookieController {

    // Armazena os cookies capturados em memória (MVP)
    private static List<Map<String, Object>> cookiesCapturados = new ArrayList<>();

    /**
     * Abre o browser na URL informada para o usuário fazer login manualmente.
     * Após o login, o usuário chama /capturar para salvar os cookies.
     */
    @PostMapping("/abrir-browser")
    public ResponseEntity<?> abrirBrowser(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(Map.of("erro", "URL é obrigatória"));

        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions opts = new ChromeOptions();
            // Abre o Chrome visível para o usuário fazer login
            WebDriver driver = new ChromeDriver(opts);
            driver.get(url);

            // Aguarda 60 segundos para o usuário logar e então captura os cookies
            Thread.sleep(5000); // espera inicial de 5s

            Set<Cookie> cookies = driver.manage().getCookies();
            cookiesCapturados.clear();
            cookies.forEach(c -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", c.getName());
                m.put("value", c.getValue());
                m.put("domain", c.getDomain());
                m.put("path", c.getPath() != null ? c.getPath() : "/");
                m.put("secure", c.isSecure());
                cookiesCapturados.add(m);
            });

            driver.quit();
            return ResponseEntity.ok(Map.of(
                "mensagem", cookiesCapturados.size() + " cookies capturados",
                "cookies", cookiesCapturados
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Erro ao capturar cookies: " + e.getMessage()));
        }
    }

    /**
     * Retorna os cookies capturados atualmente em memória.
     */
    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(Map.of(
            "total", cookiesCapturados.size(),
            "cookies", cookiesCapturados
        ));
    }

    /**
     * Limpa os cookies armazenados.
     */
    @DeleteMapping
    public ResponseEntity<Void> limpar() {
        cookiesCapturados.clear();
        return ResponseEntity.noContent().build();
    }

    public static List<Map<String, Object>> getCookiesCapturados() {
        return cookiesCapturados;
    }
}
