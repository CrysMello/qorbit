package com.qorbit.engine.selenium;

import java.time.Duration;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriverService;
import java.io.File;

@Component
public class DriverManager {

    @Value("${scanner.headless:false}")
    private boolean headless;

    private final ThreadLocal<WebDriver> driverHolder = new ThreadLocal<>();

    /** Alias de criar() — usado pelo SeleniumWorker e pelos testes. */
    public WebDriver iniciar(String browser) {
        return criar(browser);
    }

    public WebDriver criar(String browser) {
        String b = (browser == null ? "chrome" : browser.trim().toLowerCase());
        System.out.println("[DriverManager] Iniciando browser: " + b
                + (headless ? " (headless)" : ""));

        WebDriver driver;

        try {
            switch (b) {
                case "firefox" -> {
                    WebDriverManager.firefoxdriver().setup();
                    FirefoxOptions ffOpts = new FirefoxOptions();
                    if (headless) ffOpts.addArguments("-headless");
                    driver = new FirefoxDriver(ffOpts);
                }
                case "edge" -> {
                    // Selenium Manager (embutido no Selenium 4.6+) gerencia o msedgedriver
                    // automaticamente sem precisar de acesso à rede externa
                    EdgeOptions edgeOpts = new EdgeOptions();
                    if (headless) edgeOpts.addArguments("--headless=new");
                    edgeOpts.addArguments("--start-maximized");
                    edgeOpts.addArguments("--disable-notifications");
                    edgeOpts.addArguments("--remote-allow-origins=*");
                    driver = new EdgeDriver(edgeOpts);
                }
                default -> {  // chrome (padrão)
                    WebDriverManager.chromedriver().setup();
                    ChromeOptions opts = new ChromeOptions();
                    if (headless) opts.addArguments("--headless=new");
                    opts.addArguments("--start-maximized");
                    opts.addArguments("--disable-notifications");
                    opts.addArguments("--remote-allow-origins=*");
                    opts.addArguments("--no-sandbox");
                    opts.addArguments("--disable-dev-shm-usage");

                    // Flags adicionais para estabilidade em servidores Linux
                    opts.addArguments("--disable-gpu");
                    opts.addArguments("--disable-software-rasterizer");
                    opts.addArguments("--disable-extensions");
                    opts.addArguments("--disable-setuid-sandbox");

                    // Permite configurar o caminho do binário do Chrome via propriedade
                    String chromeBin = System.getProperty("scanner.chrome.bin");
                    if (chromeBin != null && !chromeBin.isBlank()) {
                        opts.setBinary(chromeBin);
                        System.out.println("[DriverManager] Usando Chrome binary: " + chromeBin);
                    }

                    // ── Selenium Stealth — anti-detecção de bot ──────────────
                    opts.addArguments("--disable-blink-features=AutomationControlled");
                    opts.addArguments("--disable-infobars");
                    opts.addArguments("--lang=pt-BR");
                    opts.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                    opts.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "enable-logging"});
                    opts.setExperimentalOption("useAutomationExtension", false);

                    // Usa perfil real do Chrome se configurado (plano B anti-Incapsula)
                    String perfilChrome = System.getProperty("scanner.chrome.perfil");
                    if (perfilChrome != null && !perfilChrome.isBlank()) {
                        opts.addArguments("--user-data-dir=" + perfilChrome);
                        opts.addArguments("--profile-directory=Default");
                        System.out.println("[DriverManager] Usando perfil real do Chrome: " + perfilChrome);
                    }

                        // Cria ChromeDriverService com logging para diagnóstico
                        String logPath = System.getProperty("scanner.chromedriver.log", "/tmp/chromedriver.log");
                        ChromeDriverService service = new ChromeDriverService.Builder()
                            .usingAnyFreePort()
                            .withSilent(false)
                            .withVerbose(true)
                            .withLogFile(new File(logPath))
                            .build();

                        System.out.println("[DriverManager] Chromedriver log: " + logPath);
                        driver = new ChromeDriver(service, opts);

                    // Injeta JS stealth para esconder marcas do WebDriver
                    aplicarStealth((org.openqa.selenium.JavascriptExecutor) driver);
                }
            }
        } catch (Exception e) {
            System.err.println("[DriverManager] ERRO ao criar driver: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Falha ao iniciar o browser '" + b
                    + "'. Verifique se o Chrome está instalado. Detalhe: "
                    + e.getMessage(), e);
        }

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driverHolder.set(driver);

        System.out.println("[DriverManager] Browser iniciado com sucesso: " + b);
        return driver;
    }

    /** Injeta scripts JS para esconder marcas do WebDriver (anti-bot). */
    private void aplicarStealth(org.openqa.selenium.JavascriptExecutor js) {
        try {
            js.executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                "Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});" +
                "Object.defineProperty(navigator, 'languages', {get: () => ['pt-BR','pt','en']});" +
                "window.chrome = {runtime: {}};" +
                "Object.defineProperty(navigator, 'permissions', {get: () => ({query: () => Promise.resolve({state: 'granted'})})});"
            );
        } catch (Exception ignored) {
            // Não crítico — continua sem stealth se falhar
        }
    }

    public WebDriver atual() {
        return driverHolder.get();
    }

    /** Retorna true se houver um WebDriver ativo na thread corrente. */
    public boolean isAtivo() {
        return driverHolder.get() != null;
    }

    public void encerrar() {
        WebDriver driver = driverHolder.get();
        if (driver != null) {
            try {
                driver.quit();
                System.out.println("[DriverManager] Browser encerrado.");
            } catch (Exception e) {
                System.err.println("[DriverManager] Erro ao encerrar: " + e.getMessage());
            } finally {
                driverHolder.remove();
            }
        }
    }
}
