package com.qorbit.engine.controller;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostico")
public class DiagnosticoController {

    /**
     * Testa se o Chrome + ChromeDriver funcionam corretamente.
     * Acesse: GET http://localhost:8080/api/diagnostico/chrome
     */
    @GetMapping("/chrome")
    public Map<String, Object> testarChrome() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("javaVersion", System.getProperty("java.version"));
        resultado.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));

        // Verifica chromedriver via WebDriverManager
        try {
            WebDriverManager.chromedriver().setup();
            String driverPath = System.getProperty("webdriver.chrome.driver");
            resultado.put("chromedriverPath", driverPath != null ? driverPath : "gerenciado pelo WDM");
            resultado.put("chromedriverStatus", "OK — configurado com sucesso");
        } catch (Exception e) {
            resultado.put("chromedriverStatus", "ERRO: " + e.getMessage());
            resultado.put("ok", false);
            return resultado;
        }

        // Tenta abrir e fechar o Chrome
        ChromeDriver driver = null;
        try {
            ChromeOptions opts = new ChromeOptions();
            opts.addArguments("--headless=new", "--no-sandbox",
                    "--disable-dev-shm-usage", "--remote-allow-origins=*");
            driver = new ChromeDriver(opts);
            String titulo = driver.getTitle();
            resultado.put("browserAbriu", true);
            resultado.put("ok", true);
            resultado.put("mensagem", "Chrome funcionando corretamente!");
        } catch (Exception e) {
            resultado.put("browserAbriu", false);
            resultado.put("ok", false);
            resultado.put("erro", e.getMessage());
            resultado.put("dica", "Verifique se o Google Chrome está instalado. "
                    + "Baixe em: https://www.google.com/chrome/");
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        return resultado;
    }

    /** Status rápido sem abrir browser. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", "Qorbit");
        m.put("javaVersion", System.getProperty("java.version"));
        m.put("os", System.getProperty("os.name"));
        try {
            WebDriverManager.chromedriver().setup();
            m.put("chromedriverDisponivel", true);
        } catch (Exception e) {
            m.put("chromedriverDisponivel", false);
            m.put("chromedriverErro", e.getMessage());
        }
        return m;
    }
}
