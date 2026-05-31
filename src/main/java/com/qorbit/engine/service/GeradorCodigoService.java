package com.qorbit.engine.service;

import com.qorbit.engine.gerador.NormalizadorNomes;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.ElementoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class GeradorCodigoService {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private NormalizadorNomes normalizador;

    public byte[] gerarZip(List<CasoDeTeste> casos) throws IOException {
        return gerarZip(casos, false, "CUCUMBER");
    }

    public byte[] gerarZip(List<CasoDeTeste> casos, boolean includeCiCd) throws IOException {
        return gerarZip(casos, includeCiCd, "CUCUMBER");
    }

    public byte[] gerarZip(List<CasoDeTeste> casos, boolean includeCiCd, String formato) throws IOException {
        return switch (formato == null ? "CUCUMBER" : formato.toUpperCase(Locale.ROOT)) {
            case "SELENIUM" -> gerarZipSeleniumPuro(casos, includeCiCd);
            case "TESTNG"   -> gerarZipTestNG(casos, includeCiCd);
            default         -> gerarZipCucumber(casos, includeCiCd);
        };
    }

    private byte[] gerarZipCucumber(List<CasoDeTeste> casos, boolean includeCiCd) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<Elemento> todosElementos = elementoRepo.findAll();

        Map<String, Elemento> elementosPorNome = todosElementos.stream()
                .filter(e -> e.getNomeLogico() != null && !e.getNomeLogico().isBlank())
                .collect(Collectors.toMap(
                        e -> e.getNomeLogico().trim().toLowerCase(Locale.ROOT),
                        e -> e,
                        this::priorizarElemento,
                        LinkedHashMap::new));

        Map<String, PageObjectSpec> pages = construirPages(todosElementos);

        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            adicionarArquivo(zip, "pom.xml", gerarPomXml());
            adicionarArquivo(zip, "src/test/java/runner/TestRunner.java", gerarTestRunner());
            adicionarArquivo(zip, "src/test/java/config/Hooks.java", gerarHooks());
            adicionarArquivo(zip, "src/test/java/config/DriverFactory.java", includeCiCd ? gerarDriverFactoryCiCd() : gerarDriverFactory());
            adicionarArquivo(zip, "src/test/java/report/StepEvidence.java", gerarStepEvidence());
            adicionarArquivo(zip, "src/test/java/report/ExecutionContext.java", gerarExecutionContext());
            adicionarArquivo(zip, "src/test/java/report/FeatureStepCatalog.java", gerarFeatureStepCatalog());
            adicionarArquivo(zip, "src/test/java/report/HtmlReportGenerator.java", gerarHtmlReportGenerator());
            adicionarArquivo(zip, "src/test/java/steps/CommonSteps.java", gerarCommonSteps(pages));

            for (PageObjectSpec page : pages.values()) {
                adicionarArquivo(zip,
                        "src/test/java/pages/" + page.className + ".java",
                        gerarPageObject(page));
            }

            for (CasoDeTeste caso : casos) {
                String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
                adicionarArquivo(zip,
                        "src/test/resources/features/" + nomeClasse.toLowerCase(Locale.ROOT) + ".feature",
                        gerarFeature(caso));
                adicionarArquivo(zip,
                        "src/test/java/steps/" + nomeClasse + "Steps.java",
                        gerarStepDefinitions(caso, pages, elementosPorNome));
            }

            adicionarArquivo(zip, "README.md", includeCiCd ? gerarReadmeCiCd() : gerarReadme());
            adicionarArquivo(zip, "executar-testes.bat", gerarExecutarTestesBat());
            adicionarArquivo(zip, "executar-testes.sh", gerarExecutarTestesSh());
            if (includeCiCd) {
                adicionarArquivo(zip, ".github/workflows/testes.yml", gerarGithubActionsWorkflow());
            }
            adicionarArquivo(zip, "src/test/java/healing/HealingService.java", gerarHealingService());
            adicionarArquivo(zip, "src/test/resources/healing.properties", gerarHealingProperties());
        }

        return baos.toByteArray();
    }

    // ─── Selenium puro (JUnit 4) ─────────────────────────────────────────────

    // ─── Selenium puro — JUnit 5 — Arquitetura Profissional ──────────────────

    private byte[] gerarZipSeleniumPuro(List<CasoDeTeste> casos, boolean includeCiCd) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<Elemento> todosElementos = elementoRepo.findAll();
        Map<String, Elemento> elementosPorNome = todosElementos.stream()
                .filter(e -> e.getNomeLogico() != null && !e.getNomeLogico().isBlank())
                .collect(Collectors.toMap(
                        e -> e.getNomeLogico().trim().toLowerCase(Locale.ROOT),
                        e -> e, this::priorizarElemento, LinkedHashMap::new));
        Map<String, PageObjectSpec> pages = construirPages(todosElementos);

        // URL base: extrai do primeiro step NAVEGAR encontrado nos casos
        String urlBase = casos.stream()
                .flatMap(c -> orderedSteps(c).stream())
                .filter(s -> "NAVEGAR".equalsIgnoreCase(s.getAcao()))
                .map(s -> Optional.ofNullable(s.getValorEntrada()).orElse("https://app.suaempresa.com.br"))
                .findFirst().orElse("https://app.suaempresa.com.br");

        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // ── Infraestrutura ──
            adicionarArquivo(zip, "pom.xml",                                                 pro5Pom());
            adicionarArquivo(zip, "src/main/java/config/ConfigReader.java",                  pro5ConfigReader());
            adicionarArquivo(zip, "src/test/java/driver/DriverFactory.java",                 pro5DriverFactory(includeCiCd));
            adicionarArquivo(zip, "src/test/java/base/BaseTest.java",                        pro5BaseTest());
            adicionarArquivo(zip, "src/test/java/pages/BasePage.java",                       pro5BasePage());
            adicionarArquivo(zip, "src/test/java/utils/WaitUtils.java",                      pro5WaitUtils());
            adicionarArquivo(zip, "src/test/java/utils/ElementUtils.java",                   pro5ElementUtils());
            adicionarArquivo(zip, "src/test/java/utils/ScreenshotUtils.java",                pro5ScreenshotUtils());
            adicionarArquivo(zip, "src/test/java/utils/JsonDataReader.java",                 pro5JsonDataReader());
            adicionarArquivo(zip, "src/test/java/listeners/TestListener.java",               pro5TestListener());
            adicionarArquivo(zip, "src/test/java/models/UsuarioTeste.java",                  pro5UsuarioTeste());
            adicionarArquivo(zip, "src/test/resources/config.properties",                    pro5ConfigProperties(urlBase));
            adicionarArquivo(zip, "src/test/resources/testdata/usuarios.json",               pro5UsuariosJson());

            // ── Page Objects ──
            for (PageObjectSpec page : pages.values())
                adicionarArquivo(zip, "src/test/java/pages/" + page.className + ".java",
                        pro5PageObject(page));

            // ── Testes ──
            for (CasoDeTeste caso : casos) {
                String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
                adicionarArquivo(zip, "src/test/java/tests/" + nomeClasse + "Test.java",
                        pro5TesteJUnit5(caso, pages, elementosPorNome));
            }

            adicionarArquivo(zip, "README.md", pro5Readme());
            adicionarArquivo(zip, "executar-testes.bat", gerarExecutarTestesBat());
            adicionarArquivo(zip, "executar-testes.sh", gerarExecutarTestesSh());
            if (includeCiCd)
                adicionarArquivo(zip, ".github/workflows/testes.yml", gerarGithubActionsWorkflow());
        }
        return baos.toByteArray();
    }

    // ── pom.xml ───────────────────────────────────────────────────────────────

    private String pro5Pom() {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.qorbit.automation</groupId>
    <artifactId>qorbit-tests-export</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <selenium.version>4.18.1</selenium.version>
        <junit.version>5.10.2</junit.version>
        <wdm.version>5.7.0</wdm.version>
        <jackson.version>2.17.0</jackson.version>
        <surefire.version>3.2.5</surefire.version>
    </properties>

    <dependencies>

        <!-- Selenium -->
        <dependency>
            <groupId>org.seleniumhq.selenium</groupId>
            <artifactId>selenium-java</artifactId>
            <version>${selenium.version}</version>
        </dependency>

        <!-- WebDriverManager -->
        <dependency>
            <groupId>io.github.bonigarcia</groupId>
            <artifactId>webdrivermanager</artifactId>
            <version>${wdm.version}</version>
        </dependency>

        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-launcher</artifactId>
            <version>1.10.2</version>
            <scope>test</scope>
        </dependency>

        <!-- Jackson — leitura de dados de teste em JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>

    </dependencies>

    <build>
        <plugins>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <release>17</release>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${surefire.version}</version>
                <configuration>
                    <includes>
                        <include>**/tests/**/*Test.java</include>
                    </includes>
                    <systemPropertyVariables>
                        <headless>${headless}</headless>
                        <file.encoding>UTF-8</file.encoding>
                    </systemPropertyVariables>
                </configuration>
            </plugin>

        </plugins>
    </build>

</project>
""";
    }

    // ── ConfigReader.java ─────────────────────────────────────────────────────

    private String pro5ConfigReader() {
        return """
package config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Leitor centralizado de configuracoes do arquivo config.properties.
 * Implementado como singleton para evitar multiplas leituras do classpath.
 */
public final class ConfigReader {

    private static volatile ConfigReader instance;
    private final Properties props = new Properties();

    private ConfigReader() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new IllegalStateException("config.properties nao encontrado no classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar config.properties", e);
        }
    }

    public static ConfigReader getInstance() {
        if (instance == null) {
            synchronized (ConfigReader.class) {
                if (instance == null) instance = new ConfigReader();
            }
        }
        return instance;
    }

    public String getBaseUrl() {
        return getRequired("base.url");
    }

    public String getBrowser() {
        return props.getProperty("browser", "chrome").trim().toLowerCase();
    }

    public int getTimeoutSeconds() {
        return Integer.parseInt(props.getProperty("timeout.seconds", "10"));
    }

    public boolean isHeadless() {
        // Prioridade: system property > config.properties
        String sysProp = System.getProperty("headless");
        if (sysProp != null) return Boolean.parseBoolean(sysProp);
        return Boolean.parseBoolean(props.getProperty("headless", "false"));
    }

    public String getScreenshotsDir() {
        return props.getProperty("screenshots.dir", "target/screenshots");
    }

    private String getRequired(String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Propriedade obrigatoria ausente: " + key);
        }
        return value.trim();
    }
}
""";
    }

    // ── DriverFactory.java ────────────────────────────────────────────────────

    private String pro5DriverFactory(boolean ciCd) {
        return "package driver;\n\n"
            + "import config.ConfigReader;\n"
            + "import io.github.bonigarcia.wdm.WebDriverManager;\n"
            + "import org.openqa.selenium.WebDriver;\n"
            + "import org.openqa.selenium.chrome.ChromeDriver;\n"
            + "import org.openqa.selenium.chrome.ChromeOptions;\n"
            + "import org.openqa.selenium.edge.EdgeDriver;\n"
            + "import org.openqa.selenium.edge.EdgeOptions;\n"
            + "import org.openqa.selenium.firefox.FirefoxDriver;\n"
            + "import org.openqa.selenium.firefox.FirefoxOptions;\n\n"
            + "/**\n"
            + " * Responsavel exclusivamente pela criacao do WebDriver.\n"
            + " * Suporta Chrome, Firefox e Edge. Expande adicionando novos cases.\n"
            + " */\n"
            + "public final class DriverFactory {\n\n"
            + "    private DriverFactory() {}\n\n"
            + "    public static WebDriver create() {\n"
            + "        ConfigReader cfg = ConfigReader.getInstance();\n"
            + "        boolean headless = cfg.isHeadless();\n"
            + "        return switch (cfg.getBrowser()) {\n"
            + "            case \"firefox\" -> createFirefox(headless);\n"
            + "            case \"edge\"    -> createEdge(headless);\n"
            + "            default         -> createChrome(headless);\n"
            + "        };\n"
            + "    }\n\n"
            + "    private static WebDriver createChrome(boolean headless) {\n"
            + "        WebDriverManager.chromedriver().setup();\n"
            + "        ChromeOptions options = new ChromeOptions();\n"
            + "        options.addArguments(\"--disable-notifications\");\n"
            + "        options.addArguments(\"--disable-popup-blocking\");\n"
            + "        options.addArguments(\"--disable-extensions\");\n"
            + (ciCd ? "        options.addArguments(\"--headless=new\");\n"
                    + "        options.addArguments(\"--no-sandbox\");\n"
                    + "        options.addArguments(\"--disable-dev-shm-usage\");\n"
                    + "        options.addArguments(\"--window-size=1920,1080\");\n"
                : "        if (headless) {\n"
                    + "            options.addArguments(\"--headless=new\");\n"
                    + "            options.addArguments(\"--no-sandbox\");\n"
                    + "            options.addArguments(\"--disable-dev-shm-usage\");\n"
                    + "            options.addArguments(\"--window-size=1920,1080\");\n"
                    + "        }\n")
            + "        ChromeDriver driver = new ChromeDriver(options);\n"
            + "        if (!headless) driver.manage().window().maximize();\n"
            + "        return driver;\n"
            + "    }\n\n"
            + "    private static WebDriver createFirefox(boolean headless) {\n"
            + "        WebDriverManager.firefoxdriver().setup();\n"
            + "        FirefoxOptions options = new FirefoxOptions();\n"
            + "        if (headless) options.addArguments(\"-headless\");\n"
            + "        return new FirefoxDriver(options);\n"
            + "    }\n\n"
            + "    private static WebDriver createEdge(boolean headless) {\n"
            + "        WebDriverManager.edgedriver().setup();\n"
            + "        EdgeOptions options = new EdgeOptions();\n"
            + "        if (headless) options.addArguments(\"--headless=new\");\n"
            + "        return new EdgeDriver(options);\n"
            + "    }\n"
            + "}\n";
    }

    // ── BaseTest.java ─────────────────────────────────────────────────────────

    private String pro5BaseTest() {
        return """
package base;

import config.ConfigReader;
import driver.DriverFactory;
import listeners.TestListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

/**
 * Classe base para todos os testes.
 * Gerencia o ciclo de vida do WebDriver e expoe driver e config para os testes filhos.
 *
 * Uso: todas as classes de teste devem herdar de BaseTest.
 */
@ExtendWith(TestListener.class)
public abstract class BaseTest {

    protected WebDriver driver;
    protected ConfigReader config;

    @BeforeEach
    void setUp() {
        config = ConfigReader.getInstance();
        driver = DriverFactory.create();
        // Usando explicit waits — implicit wait desativado intencionalmente
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
""";
    }

    // ── BasePage.java ─────────────────────────────────────────────────────────

    private String pro5BasePage() {
        return """
package pages;

import config.ConfigReader;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import utils.ElementUtils;
import utils.WaitUtils;

/**
 * Classe base para todos os Page Objects.
 * Centraliza a criacao de WaitUtils e ElementUtils,
 * evitando duplicacao e garantindo consistencia entre paginas.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WaitUtils waitUtils;
    protected final ElementUtils elementUtils;
    protected final ConfigReader config;

    protected BasePage(WebDriver driver) {
        this.driver        = driver;
        this.config        = ConfigReader.getInstance();
        this.waitUtils     = new WaitUtils(driver, config.getTimeoutSeconds());
        this.elementUtils  = new ElementUtils(driver, waitUtils);
    }

    /**
     * Navega para a URL informada e aguarda o carregamento da pagina.
     */
    public void abrir(String url) {
        driver.get(url);
        aguardarCarregamento();
    }

    /**
     * Aguarda o documento estar pronto e sem indicadores de loading visiveis.
     */
    public void aguardarCarregamento() {
        try {
            waitUtils.waitForCondition(d -> {
                String state = String.valueOf(
                    ((JavascriptExecutor) d).executeScript("return document.readyState"));
                Boolean busy = (Boolean) ((JavascriptExecutor) d).executeScript(
                    "return !!document.querySelector('[aria-busy=true], .loading, .loader, .spinner')");
                return ("complete".equals(state) || "interactive".equals(state))
                       && !Boolean.TRUE.equals(busy);
            });
        } catch (Exception ignored) { }
    }

    public String obterTitulo() {
        return driver.getTitle();
    }

    public String obterUrlAtual() {
        return driver.getCurrentUrl();
    }
}
""";
    }

    // ── WaitUtils.java ────────────────────────────────────────────────────────

    private String pro5WaitUtils() {
        return """
package utils;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Centralizador de esperas explicitas.
 * Nenhuma classe de teste ou page object deve chamar Thread.sleep diretamente.
 */
public class WaitUtils {

    private final WebDriverWait wait;
    private final WebDriverWait shortWait;

    public WaitUtils(WebDriver driver, int timeoutSeconds) {
        this.wait      = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
        this.shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));
    }

    public WebElement waitForVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitForClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public WebElement waitForPresence(By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public void waitForInvisibility(By locator) {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public void waitForTextPresent(By locator, String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, text));
    }

    public <T> T waitForCondition(ExpectedCondition<T> condition) {
        return wait.until(condition);
    }

    /**
     * Retorna true se o elemento ficar visivel dentro do timeout curto.
     * Nao lanca excecao em caso de timeout.
     */
    public boolean isVisible(By locator) {
        try {
            return shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retorna true se o elemento estiver presente no DOM.
     */
    public boolean isPresent(By locator) {
        try {
            return shortWait.until(ExpectedConditions.presenceOfElementLocated(locator)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
""";
    }

    // ── ElementUtils.java ─────────────────────────────────────────────────────

    private String pro5ElementUtils() {
        return """
package utils;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

/**
 * Utilitario de interacao com elementos.
 * Encapsula operacoes Selenium para manter os Page Objects limpos e sem duplicacao.
 */
public class ElementUtils {

    private final WebDriver driver;
    private final WaitUtils wait;

    public ElementUtils(WebDriver driver, WaitUtils wait) {
        this.driver = driver;
        this.wait   = wait;
    }

    /** Clica em um elemento apos aguardar que esteja clicavel. */
    public void click(By locator) {
        wait.waitForClickable(locator).click();
    }

    /** Clica via JavaScript — util para elementos bloqueados por overlay. */
    public void clickJs(By locator) {
        WebElement el = wait.waitForPresence(locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    /** Limpa o campo e digita o texto informado. */
    public void type(By locator, String text) {
        WebElement el = wait.waitForVisible(locator);
        el.clear();
        el.sendKeys(text);
    }

    /** Retorna o texto visivel do elemento. */
    public String getText(By locator) {
        return wait.waitForVisible(locator).getText().trim();
    }

    /** Retorna o valor do atributo 'value' (campos de formulario). */
    public String getValue(By locator) {
        return wait.waitForVisible(locator).getAttribute("value");
    }

    /** Verifica se o elemento esta visivel na tela. */
    public boolean isDisplayed(By locator) {
        return wait.isVisible(locator);
    }

    /** Faz scroll ate o elemento. */
    public void scrollTo(By locator) {
        WebElement el = wait.waitForPresence(locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", el);
    }

    /** Seleciona opcao de dropdown pelo texto visivel. */
    public void selectByText(By locator, String text) {
        new Select(wait.waitForVisible(locator)).selectByVisibleText(text);
    }

    /** Seleciona opcao de dropdown pelo valor (atributo value). */
    public void selectByValue(By locator, String value) {
        new Select(wait.waitForVisible(locator)).selectByValue(value);
    }

    /** Move o mouse sobre o elemento (hover). */
    public void hover(By locator) {
        new Actions(driver).moveToElement(wait.waitForVisible(locator)).perform();
    }

    /** Retorna o texto de um atributo arbitrario do elemento. */
    public String getAttribute(By locator, String attribute) {
        return wait.waitForPresence(locator).getAttribute(attribute);
    }
}
""";
    }

    // ── ScreenshotUtils.java ──────────────────────────────────────────────────

    private String pro5ScreenshotUtils() {
        return """
package utils;

import config.ConfigReader;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilitario para captura de screenshots.
 * Salva evidencias em target/screenshots com nome identificavel pelo teste.
 */
public final class ScreenshotUtils {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ScreenshotUtils() {}

    /**
     * Captura screenshot e salva no diretorio configurado.
     *
     * @param driver   WebDriver ativo
     * @param testName nome do teste (usado no nome do arquivo)
     * @return Path do arquivo salvo, ou null em caso de falha
     */
    public static Path capture(WebDriver driver, String testName) {
        String dir = ConfigReader.getInstance().getScreenshotsDir();
        try {
            Path dirPath = Paths.get(dir);
            Files.createDirectories(dirPath);

            String sanitized  = testName.replaceAll("[^a-zA-Z0-9_\\\\-]", "_");
            String timestamp  = LocalDateTime.now().format(FMT);
            Path   dest       = dirPath.resolve(sanitized + "_" + timestamp + ".png");

            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(dest, bytes);
            System.out.println("[Screenshot] Evidencia salva: " + dest.toAbsolutePath());
            return dest;

        } catch (IOException e) {
            System.err.println("[Screenshot] Falha ao salvar evidencia: " + e.getMessage());
            return null;
        }
    }
}
""";
    }

    // ── JsonDataReader.java ───────────────────────────────────────────────────

    private String pro5JsonDataReader() {
        return """
package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import models.UsuarioTeste;

import java.io.InputStream;

/**
 * Leitor de dados de teste externalizados em JSON.
 * Evita hardcode de dados sensiveis ou variaveis diretamente no codigo de teste.
 */
public final class JsonDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonDataReader() {}

    /**
     * Le um usuario de teste do arquivo testdata/usuarios.json.
     *
     * @param tipo chave do JSON (ex: "valido", "invalido", "admin")
     * @return UsuarioTeste preenchido
     */
    public static UsuarioTeste lerUsuario(String tipo) {
        try (InputStream is = JsonDataReader.class
                .getClassLoader()
                .getResourceAsStream("testdata/usuarios.json")) {

            if (is == null) {
                throw new IllegalStateException("testdata/usuarios.json nao encontrado no classpath");
            }

            JsonNode root = MAPPER.readTree(is);
            JsonNode node = root.get(tipo);

            if (node == null) {
                throw new IllegalArgumentException(
                    "Entrada '" + tipo + "' nao encontrada em testdata/usuarios.json");
            }

            return MAPPER.treeToValue(node, UsuarioTeste.class);

        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler dados de teste: " + e.getMessage(), e);
        }
    }

    /**
     * Le qualquer node JSON e converte para a classe informada.
     */
    public static <T> T lerDados(String arquivo, String chave, Class<T> tipo) {
        try (InputStream is = JsonDataReader.class
                .getClassLoader()
                .getResourceAsStream("testdata/" + arquivo)) {

            if (is == null) {
                throw new IllegalStateException("testdata/" + arquivo + " nao encontrado");
            }
            JsonNode node = MAPPER.readTree(is).get(chave);
            if (node == null) throw new IllegalArgumentException("Chave '" + chave + "' nao encontrada");
            return MAPPER.treeToValue(node, tipo);

        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler " + arquivo + "[" + chave + "]: " + e.getMessage(), e);
        }
    }
}
""";
    }

    // ── TestListener.java ─────────────────────────────────────────────────────

    private String pro5TestListener() {
        return """
package listeners;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.WebDriver;
import utils.ScreenshotUtils;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Listener JUnit 5 que captura screenshot automaticamente quando um teste falha.
 * Registrado via @ExtendWith(TestListener.class) em BaseTest.
 */
public class TestListener implements TestWatcher {

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        context.getTestInstance().ifPresent(instance -> {
            WebDriver driver = extrairDriver(instance);
            if (driver != null) {
                String testName = context.getDisplayName()
                    .replaceAll("[^a-zA-Z0-9_\\\\-]", "_");
                ScreenshotUtils.capture(driver, testName);
            }
        });
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        // Extensao: adicionar log ou metricas de sucesso aqui se necessario
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        System.out.println("[TestListener] Teste desativado: "
            + context.getDisplayName()
            + reason.map(r -> " — " + r).orElse(""));
    }

    private WebDriver extrairDriver(Object instance) {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField("driver");
                f.setAccessible(true);
                Object val = f.get(instance);
                if (val instanceof WebDriver wd) return wd;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                System.err.println("[TestListener] Erro ao acessar driver: " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}
""";
    }

    // ── UsuarioTeste.java ─────────────────────────────────────────────────────

    private String pro5UsuarioTeste() {
        return """
package models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Model de dados para usuario de teste.
 * Lido a partir de testdata/usuarios.json via JsonDataReader.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsuarioTeste {

    private String email;
    private String senha;
    private String nome;
    private String perfil;

    public UsuarioTeste() {}

    public UsuarioTeste(String email, String senha, String nome, String perfil) {
        this.email  = email;
        this.senha  = senha;
        this.nome   = nome;
        this.perfil = perfil;
    }

    public String getEmail()  { return email; }
    public String getSenha()  { return senha; }
    public String getNome()   { return nome; }
    public String getPerfil() { return perfil; }

    public void setEmail(String email)   { this.email  = email; }
    public void setSenha(String senha)   { this.senha  = senha; }
    public void setNome(String nome)     { this.nome   = nome; }
    public void setPerfil(String perfil) { this.perfil = perfil; }

    @Override
    public String toString() {
        return "UsuarioTeste{email='" + email + "', perfil='" + perfil + "'}";
    }
}
""";
    }

    // ── config.properties ─────────────────────────────────────────────────────

    private String pro5ConfigProperties(String urlBase) {
        return "# ============================================================\n"
             + "# Configuracoes do framework de automacao — Qorbit Export\n"
             + "# ============================================================\n\n"
             + "# URL base da aplicacao testada\n"
             + "base.url=" + urlBase + "\n\n"
             + "# Navegador: chrome | firefox | edge\n"
             + "browser=chrome\n\n"
             + "# Timeout padrao para esperas explicitas (segundos)\n"
             + "timeout.seconds=10\n\n"
             + "# Executar em modo headless: true | false\n"
             + "# Pode ser sobrescrito via -Dheadless=true no mvn test\n"
             + "headless=false\n\n"
             + "# Diretorio para salvar screenshots de falha\n"
             + "screenshots.dir=target/screenshots\n";
    }

    // ── testdata/usuarios.json ────────────────────────────────────────────────

    private String pro5UsuariosJson() {
        return "{\n"
             + "  \"valido\": {\n"
             + "    \"email\":  \"usuario@suaempresa.com.br\",\n"
             + "    \"senha\":  \"SenhaValida@123\",\n"
             + "    \"nome\":   \"Usuario Valido\",\n"
             + "    \"perfil\": \"USUARIO\"\n"
             + "  },\n"
             + "  \"admin\": {\n"
             + "    \"email\":  \"admin@suaempresa.com.br\",\n"
             + "    \"senha\":  \"AdminSenha@456\",\n"
             + "    \"nome\":   \"Administrador\",\n"
             + "    \"perfil\": \"ADMIN\"\n"
             + "  },\n"
             + "  \"invalido\": {\n"
             + "    \"email\":  \"invalido@suaempresa.com.br\",\n"
             + "    \"senha\":  \"SenhaErrada\",\n"
             + "    \"nome\":   \"Usuario Invalido\",\n"
             + "    \"perfil\": \"USUARIO\"\n"
             + "  }\n"
             + "}\n";
    }

    // ── Page Object profissional ──────────────────────────────────────────────

    private String pro5PageObject(PageObjectSpec page) {
        StringBuilder sb = new StringBuilder();
        sb.append("package pages;\n\n");
        sb.append("import org.openqa.selenium.By;\n");
        sb.append("import org.openqa.selenium.WebDriver;\n\n");
        sb.append("/**\n");
        sb.append(" * Page Object — ").append(page.rawPageName).append("\n");
        sb.append(" * Encapsula locators e acoes da pagina.\n");
        sb.append(" * Assertions ficam nos testes — esta classe expoe apenas comportamento.\n");
        sb.append(" */\n");
        sb.append("public class ").append(page.className).append(" extends BasePage {\n\n");

        // Locators
        if (!page.elementos.isEmpty()) {
            sb.append("    // ── Locators ────────────────────────────────────────────────────\n\n");
            for (ElementSpec el : page.elementos) {
                String byExpr = pro5ByExpression(el.locator());
                sb.append("    private final By ").append(el.methodName())
                  .append(" = ").append(byExpr).append(";\n");
            }
        } else {
            sb.append("    // Nenhum elemento capturado para esta pagina\n");
            sb.append("    // Adicione seus locators aqui:\n");
            sb.append("    // private final By meuElemento = By.cssSelector(\"#seuSeletor\");\n");
        }

        sb.append("\n");
        sb.append("    public ").append(page.className).append("(WebDriver driver) {\n");
        sb.append("        super(driver);\n");
        sb.append("    }\n\n");

        if (!page.elementos.isEmpty()) {
            sb.append("    // ── Acoes ────────────────────────────────────────────────────────\n\n");
            for (ElementSpec el : page.elementos) {
                String name = el.methodName();
                String cap  = Character.toUpperCase(name.charAt(0)) + name.substring(1);

                // preencher
                sb.append("    /** Preenche o campo ").append(name).append(" com o valor informado. */\n");
                sb.append("    public void preencher").append(cap).append("(String valor) {\n");
                sb.append("        elementUtils.type(").append(name).append(", valor);\n");
                sb.append("    }\n\n");

                // clicar
                sb.append("    /** Clica no elemento ").append(name).append(". */\n");
                sb.append("    public void clicar").append(cap).append("() {\n");
                sb.append("        elementUtils.click(").append(name).append(");\n");
                sb.append("    }\n\n");

                // visibilidade
                sb.append("    /** Retorna true se ").append(name).append(" estiver visivel. */\n");
                sb.append("    public boolean is").append(cap).append("Visivel() {\n");
                sb.append("        return elementUtils.isDisplayed(").append(name).append(");\n");
                sb.append("    }\n\n");

                // texto
                sb.append("    /** Retorna o texto visivel de ").append(name).append(". */\n");
                sb.append("    public String obterTexto").append(cap).append("() {\n");
                sb.append("        return elementUtils.getText(").append(name).append(");\n");
                sb.append("    }\n\n");
            }
        } else {
            sb.append("    // Adicione metodos de acao aqui seguindo o padrao:\n");
            sb.append("    // public void preencherCampo(String valor) { elementUtils.type(campo, valor); }\n");
            sb.append("    // public void clicarBotao() { elementUtils.click(botao); }\n");
            sb.append("    // public boolean isBotaoVisivel() { return elementUtils.isDisplayed(botao); }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String pro5ByExpression(LocatorSpec loc) {
        if (loc == null) return "By.cssSelector(\"[data-testid=\\\"elemento\\\"]\")";;
        String v = normalizador.literalJava(loc.valor());
        return switch (loc.tipo().toUpperCase(Locale.ROOT)) {
            case "XPATH"      -> "By.xpath(\"" + v + "\")";
            case "ID"         -> "By.id(\"" + v + "\")";
            case "NAME"       -> "By.name(\"" + v + "\")";
            case "LINK_TEXT"  -> "By.linkText(\"" + v + "\")";
            case "TAG"        -> "By.tagName(\"" + v + "\")";
            default           -> "By.cssSelector(\"" + v + "\")";
        };
    }

    // ── Classe de teste JUnit 5 ───────────────────────────────────────────────

    private String pro5TesteJUnit5(CasoDeTeste caso, Map<String, PageObjectSpec> pages,
                                   Map<String, Elemento> elementosPorNome) {
        String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
        String codigo     = Optional.ofNullable(caso.getCodigo()).orElse("CT");
        StringBuilder sb  = new StringBuilder();

        // Imports
        sb.append("package tests;\n\n");
        sb.append("import base.BaseTest;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");

        LinkedHashMap<String, String> pageVars = new LinkedHashMap<>();
        List<ResolvedStep> resolvedSteps = resolverSteps(caso, pages, elementosPorNome);
        for (ResolvedStep rs : resolvedSteps) {
            if (rs.pageClassName() != null && !pageVars.containsKey(rs.pageClassName())) {
                pageVars.put(rs.pageClassName(), lowerFirst(rs.pageClassName()));
                sb.append("import pages.").append(rs.pageClassName()).append(";\n");
            }
        }
        if (pageVars.isEmpty() && !pages.isEmpty()) {
            String fallback = pages.keySet().iterator().next();
            pageVars.put(fallback, lowerFirst(fallback));
            sb.append("import pages.").append(fallback).append(";\n");
        }

        sb.append("\n/**\n");
        sb.append(" * Teste gerado automaticamente pelo Qorbit.\n");
        sb.append(" * Caso: ").append(safe(caso.getNome())).append("\n");
        sb.append(" *\n");
        sb.append(" * Herda de BaseTest — driver e config sao inicializados automaticamente.\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"").append(codigo).append(": ").append(safe(caso.getNome())).append("\")\n");
        sb.append("public class ").append(nomeClasse).append("Test extends BaseTest {\n\n");

        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Executar fluxo: ").append(safe(caso.getNome())).append("\")\n");
        sb.append("    void executar").append(nomeClasse).append("() {\n");

        // Inicializa page objects dentro do metodo de teste
        sb.append("        // ── Page Objects ────────────────────────────────────────────────\n");
        for (Map.Entry<String, String> e : pageVars.entrySet())
            sb.append("        ").append(e.getKey()).append(" ").append(e.getValue())
              .append(" = new ").append(e.getKey()).append("(driver);\n");
        sb.append("\n");

        sb.append("        // ── Fluxo ────────────────────────────────────────────────────────\n");
        List<StepTeste> ordered = orderedSteps(caso);
        for (int i = 0; i < ordered.size(); i++) {
            StepTeste step   = ordered.get(i);
            ResolvedStep res = i < resolvedSteps.size() ? resolvedSteps.get(i) : null;
            pro5CorpoStep(sb, step, res, pageVars);
        }

        sb.append("    }\n}\n");
        return sb.toString();
    }

    private void pro5CorpoStep(StringBuilder sb, StepTeste step, ResolvedStep resolved,
                                Map<String, String> pageVars) {
        String acao   = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String pageVar = (resolved != null && resolved.pageClassName() != null)
                ? pageVars.getOrDefault(resolved.pageClassName(), pageVars.values().iterator().next())
                : pageVars.isEmpty() ? "page" : pageVars.values().iterator().next();
        String valor   = normalizador.literalJava(Optional.ofNullable(step.getValorEntrada()).orElse(""));
        String methRaw = resolved != null ? resolved.methodName() : null;
        String cap     = methRaw != null
                ? Character.toUpperCase(methRaw.charAt(0)) + methRaw.substring(1)
                : null;

        switch (acao) {
            case "NAVEGAR" ->
                sb.append("        ").append(pageVar).append(".abrir(\"").append(valor).append("\");\n");
            case "PREENCHER" -> {
                if (cap != null)
                    sb.append("        ").append(pageVar).append(".preencher").append(cap)
                      .append("(\"").append(valor).append("\");\n");
                else
                    sb.append("        // TODO: preencher elemento nao resolvido — valor: \"")
                      .append(valor).append("\"\n");
            }
            case "CLICAR" -> {
                if (cap != null)
                    sb.append("        ").append(pageVar).append(".clicar").append(cap).append("();\n");
                else
                    sb.append("        // TODO: clicar em elemento nao resolvido\n");
            }
            case "VALIDAR" -> {
                if (cap != null)
                    sb.append("        assertTrue(").append(pageVar).append(".is").append(cap)
                      .append("Visivel(), \"Elemento '").append(methRaw).append("' deve estar visivel\");\n");
                else
                    sb.append("        // TODO: validar elemento nao resolvido\n");
            }
            default ->
                sb.append("        // Step: ").append(acao).append(" — ")
                  .append(descricaoStep(step)).append("\n");
        }
    }

    // ── README.md ─────────────────────────────────────────────────────────────

    private String pro5Readme() {
        return """
# Qorbit — Projeto Exportado (Selenium + JUnit 5)

Arquitetura profissional de automacao UI: Java 17 + Selenium 4 + JUnit 5 + Maven.

## Estrutura

```
src
├── main/java/config/
│   └── ConfigReader.java           — leitura de config.properties
└── test/
    ├── java/
    │   ├── base/BaseTest.java       — ciclo de vida do driver (@BeforeEach/@AfterEach)
    │   ├── driver/DriverFactory.java — criacao do WebDriver (Chrome, Firefox, Edge)
    │   ├── pages/
    │   │   ├── BasePage.java        — base com WaitUtils e ElementUtils
    │   │   └── *Page.java           — page objects gerados
    │   ├── utils/
    │   │   ├── WaitUtils.java       — esperas explicitas centralizadas
    │   │   ├── ElementUtils.java    — operacoes reutilizaveis de interacao
    │   │   ├── ScreenshotUtils.java — captura de evidencias
    │   │   └── JsonDataReader.java  — leitura de dados de teste em JSON
    │   ├── listeners/TestListener.java — screenshot automatico em falha
    │   ├── models/UsuarioTeste.java — model de dados de teste
    │   └── tests/*Test.java         — testes gerados
    └── resources/
        ├── config.properties        — configuracoes do ambiente
        └── testdata/usuarios.json   — dados externos de teste
```

## Como executar

```bash
# Execucao padrao (visivel)
mvn test

# Modo headless (para CI/CD)
mvn test -Dheadless=true

# Navegador especifico
mvn test -Dbrowser=firefox
```

## Como adicionar uma nova pagina

1. Crie `src/test/java/pages/NovaPagina.java` estendendo `BasePage`
2. Declare os locators como `private final By campo = By.cssSelector("...")`
3. Adicione metodos de acao usando `elementUtils` e `waitUtils`

## Como criar um novo teste

1. Crie `src/test/java/tests/NovaCenarioTest.java` estendendo `BaseTest`
2. Adicione `@Test` nos metodos de teste
3. Instancie os Page Objects usando `driver` (herdado de `BaseTest`)
4. Use `Assertions.assertTrue/assertEquals/assertNotNull` para validacoes

## Como trocar de navegador

Edite `src/test/resources/config.properties`:
```properties
browser=firefox   # ou chrome, edge
```

Ou passe via linha de comando:
```bash
mvn test -Dbrowser=edge
```

## Capturas de evidencia

Screenshots de falha sao salvas automaticamente em `target/screenshots/`.
Configuravel em `config.properties` via `screenshots.dir`.

## Importante

Nao use `mvn spring-boot:run` — este projeto nao possui aplicacao Spring Boot.
""";
    }

    // ─── Selenium + TestNG — Arquitetura Profissional ────────────────────────

    private byte[] gerarZipTestNG(List<CasoDeTeste> casos, boolean includeCiCd) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<Elemento> todosElementos = elementoRepo.findAll();
        Map<String, Elemento> elementosPorNome = todosElementos.stream()
                .filter(e -> e.getNomeLogico() != null && !e.getNomeLogico().isBlank())
                .collect(Collectors.toMap(
                        e -> e.getNomeLogico().trim().toLowerCase(Locale.ROOT),
                        e -> e, this::priorizarElemento, LinkedHashMap::new));
        Map<String, PageObjectSpec> pages = construirPages(todosElementos);

        String urlBase = casos.stream()
                .flatMap(c -> orderedSteps(c).stream())
                .filter(s -> "NAVEGAR".equalsIgnoreCase(s.getAcao()))
                .map(s -> Optional.ofNullable(s.getValorEntrada()).orElse("https://app.suaempresa.com.br"))
                .findFirst().orElse("https://app.suaempresa.com.br");

        List<String> classesTng = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // ── Infraestrutura (reaproveitada do JUnit 5 onde possivel) ──
            adicionarArquivo(zip, "pom.xml",                                                  tngPom());
            adicionarArquivo(zip, "src/main/java/config/ConfigReader.java",                   pro5ConfigReader());
            adicionarArquivo(zip, "src/test/java/driver/DriverFactory.java",                  pro5DriverFactory(includeCiCd));
            adicionarArquivo(zip, "src/test/java/base/BaseTest.java",                         tngBaseTest());
            adicionarArquivo(zip, "src/test/java/pages/BasePage.java",                        pro5BasePage());
            adicionarArquivo(zip, "src/test/java/utils/WaitUtils.java",                       pro5WaitUtils());
            adicionarArquivo(zip, "src/test/java/utils/ElementUtils.java",                    pro5ElementUtils());
            adicionarArquivo(zip, "src/test/java/utils/ScreenshotUtils.java",                 pro5ScreenshotUtils());
            adicionarArquivo(zip, "src/test/java/utils/JsonDataReader.java",                  pro5JsonDataReader());
            adicionarArquivo(zip, "src/test/java/listeners/TestListener.java",                tngTestListener());
            adicionarArquivo(zip, "src/test/java/models/UsuarioTeste.java",                   pro5UsuarioTeste());
            adicionarArquivo(zip, "src/test/resources/config.properties",                     pro5ConfigProperties(urlBase));
            adicionarArquivo(zip, "src/test/resources/testdata/usuarios.json",                pro5UsuariosJson());

            // ── Page Objects ──
            for (PageObjectSpec page : pages.values())
                adicionarArquivo(zip, "src/test/java/pages/" + page.className + ".java",
                        pro5PageObject(page));

            // ── Testes ──
            for (CasoDeTeste caso : casos) {
                String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
                adicionarArquivo(zip, "src/test/java/tests/" + nomeClasse + "Test.java",
                        tngTeste(caso, pages, elementosPorNome));
                classesTng.add("tests." + nomeClasse + "Test");
            }

            adicionarArquivo(zip, "testng.xml",   tngXml(classesTng));
            adicionarArquivo(zip, "README.md",     tngReadme());
            adicionarArquivo(zip, "executar-testes.bat", gerarExecutarTestesBat());
            adicionarArquivo(zip, "executar-testes.sh",  gerarExecutarTestesSh());
            if (includeCiCd)
                adicionarArquivo(zip, ".github/workflows/testes.yml", gerarGithubActionsWorkflow());
        }
        return baos.toByteArray();
    }

    // ── pom.xml TestNG ────────────────────────────────────────────────────────

    private String tngPom() {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.qorbit.automation</groupId>
    <artifactId>qorbit-tests-export</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <selenium.version>4.18.1</selenium.version>
        <testng.version>7.9.0</testng.version>
        <wdm.version>5.7.0</wdm.version>
        <jackson.version>2.17.0</jackson.version>
        <surefire.version>3.2.5</surefire.version>
    </properties>

    <dependencies>

        <!-- Selenium -->
        <dependency>
            <groupId>org.seleniumhq.selenium</groupId>
            <artifactId>selenium-java</artifactId>
            <version>${selenium.version}</version>
        </dependency>

        <!-- WebDriverManager -->
        <dependency>
            <groupId>io.github.bonigarcia</groupId>
            <artifactId>webdrivermanager</artifactId>
            <version>${wdm.version}</version>
        </dependency>

        <!-- TestNG -->
        <dependency>
            <groupId>org.testng</groupId>
            <artifactId>testng</artifactId>
            <version>${testng.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Jackson — leitura de dados de teste em JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>

    </dependencies>

    <build>
        <plugins>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <release>17</release>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${surefire.version}</version>
                <configuration>
                    <!-- TestNG suite -->
                    <suiteXmlFiles>
                        <suiteXmlFile>testng.xml</suiteXmlFile>
                    </suiteXmlFiles>
                    <systemPropertyVariables>
                        <headless>${headless}</headless>
                        <file.encoding>UTF-8</file.encoding>
                    </systemPropertyVariables>
                </configuration>
            </plugin>

        </plugins>
    </build>

</project>
""";
    }

    // ── BaseTest.java TestNG ──────────────────────────────────────────────────

    private String tngBaseTest() {
        return """
package base;

import config.ConfigReader;
import driver.DriverFactory;
import listeners.TestListener;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;

import java.time.Duration;

/**
 * Classe base para todos os testes TestNG.
 * Gerencia o ciclo de vida do WebDriver e expoe driver e config para subclasses.
 *
 * Uso: todas as classes de teste devem herdar de BaseTest.
 */
@Listeners(TestListener.class)
public abstract class BaseTest {

    protected WebDriver driver;
    protected ConfigReader config;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        config = ConfigReader.getInstance();
        driver = DriverFactory.create();
        // Usando explicit waits — implicit wait desativado intencionalmente
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
""";
    }

    // ── TestListener.java TestNG ──────────────────────────────────────────────

    private String tngTestListener() {
        return """
package listeners;

import org.openqa.selenium.WebDriver;
import org.testng.ITestListener;
import org.testng.ITestResult;
import utils.ScreenshotUtils;

import java.lang.reflect.Field;

/**
 * Listener TestNG que captura screenshot automaticamente quando um teste falha.
 * Registrado via @Listeners(TestListener.class) em BaseTest.
 */
public class TestListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        WebDriver driver = extrairDriver(result.getInstance());
        if (driver != null) {
            String testName = result.getTestClass().getName()
                + "_" + result.getName();
            ScreenshotUtils.capture(driver, testName);
        }
        System.err.println("[FALHOU] " + result.getName()
            + (result.getThrowable() != null ? " — " + result.getThrowable().getMessage() : ""));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        System.out.println("[OK] " + result.getName());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        System.out.println("[IGNORADO] " + result.getName());
    }

    @Override
    public void onTestStart(ITestResult result) {
        System.out.println("[INICIANDO] " + result.getTestClass().getSimpleName()
            + " :: " + result.getName());
    }

    private WebDriver extrairDriver(Object instance) {
        if (instance == null) return null;
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField("driver");
                f.setAccessible(true);
                Object val = f.get(instance);
                if (val instanceof WebDriver wd) return wd;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                System.err.println("[TestListener] Erro ao acessar driver: " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}
""";
    }

    // ── Classe de teste TestNG ────────────────────────────────────────────────

    private String tngTeste(CasoDeTeste caso, Map<String, PageObjectSpec> pages,
                            Map<String, Elemento> elementosPorNome) {
        String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
        String codigo     = Optional.ofNullable(caso.getCodigo()).orElse("CT");
        StringBuilder sb  = new StringBuilder();

        sb.append("package tests;\n\n");
        sb.append("import base.BaseTest;\n");
        sb.append("import org.testng.Assert;\n");
        sb.append("import org.testng.annotations.Test;\n\n");

        LinkedHashMap<String, String> pageVars = new LinkedHashMap<>();
        List<ResolvedStep> resolvedSteps = resolverSteps(caso, pages, elementosPorNome);
        for (ResolvedStep rs : resolvedSteps) {
            if (rs.pageClassName() != null && !pageVars.containsKey(rs.pageClassName())) {
                pageVars.put(rs.pageClassName(), lowerFirst(rs.pageClassName()));
                sb.append("import pages.").append(rs.pageClassName()).append(";\n");
            }
        }
        if (pageVars.isEmpty() && !pages.isEmpty()) {
            String fallback = pages.keySet().iterator().next();
            pageVars.put(fallback, lowerFirst(fallback));
            sb.append("import pages.").append(fallback).append(";\n");
        }

        sb.append("\n/**\n");
        sb.append(" * Teste gerado automaticamente pelo Qorbit.\n");
        sb.append(" * Caso: ").append(safe(caso.getNome())).append("\n");
        sb.append(" *\n");
        sb.append(" * Herda de BaseTest — driver e config sao inicializados automaticamente.\n");
        sb.append(" */\n");
        sb.append("public class ").append(nomeClasse).append("Test extends BaseTest {\n\n");

        sb.append("    @Test(description = \"").append(codigo).append(": ").append(safe(caso.getNome())).append("\")\n");
        sb.append("    public void executar").append(nomeClasse).append("() {\n");

        sb.append("        // ── Page Objects ────────────────────────────────────────────────\n");
        for (Map.Entry<String, String> e : pageVars.entrySet())
            sb.append("        ").append(e.getKey()).append(" ").append(e.getValue())
              .append(" = new ").append(e.getKey()).append("(driver);\n");
        sb.append("\n");

        sb.append("        // ── Fluxo ────────────────────────────────────────────────────────\n");
        List<StepTeste> ordered = orderedSteps(caso);
        for (int i = 0; i < ordered.size(); i++) {
            StepTeste step   = ordered.get(i);
            ResolvedStep res = i < resolvedSteps.size() ? resolvedSteps.get(i) : null;
            tngCorpoStep(sb, step, res, pageVars);
        }

        sb.append("    }\n}\n");
        return sb.toString();
    }

    private void tngCorpoStep(StringBuilder sb, StepTeste step, ResolvedStep resolved,
                               Map<String, String> pageVars) {
        String acao    = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String pageVar = (resolved != null && resolved.pageClassName() != null)
                ? pageVars.getOrDefault(resolved.pageClassName(), pageVars.values().iterator().next())
                : pageVars.isEmpty() ? "page" : pageVars.values().iterator().next();
        String valor   = normalizador.literalJava(Optional.ofNullable(step.getValorEntrada()).orElse(""));
        String methRaw = resolved != null ? resolved.methodName() : null;
        String cap     = methRaw != null
                ? Character.toUpperCase(methRaw.charAt(0)) + methRaw.substring(1)
                : null;

        switch (acao) {
            case "NAVEGAR" ->
                sb.append("        ").append(pageVar).append(".abrir(\"").append(valor).append("\");\n");
            case "PREENCHER" -> {
                if (cap != null)
                    sb.append("        ").append(pageVar).append(".preencher").append(cap)
                      .append("(\"").append(valor).append("\");\n");
                else
                    sb.append("        // TODO: preencher elemento nao resolvido — valor: \"")
                      .append(valor).append("\"\n");
            }
            case "CLICAR" -> {
                if (cap != null)
                    sb.append("        ").append(pageVar).append(".clicar").append(cap).append("();\n");
                else
                    sb.append("        // TODO: clicar em elemento nao resolvido\n");
            }
            case "VALIDAR" -> {
                if (cap != null)
                    sb.append("        Assert.assertTrue(").append(pageVar).append(".is").append(cap)
                      .append("Visivel(), \"Elemento '").append(methRaw).append("' deve estar visivel\");\n");
                else
                    sb.append("        // TODO: validar elemento nao resolvido\n");
            }
            default ->
                sb.append("        // Step: ").append(acao).append(" — ")
                  .append(descricaoStep(step)).append("\n");
        }
    }

    // ── testng.xml ────────────────────────────────────────────────────────────

    private String tngXml(List<String> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n\n");
        sb.append("<!--\n");
        sb.append("  Suite de testes gerada pelo Qorbit.\n");
        sb.append("  Para executar: mvn test\n");
        sb.append("  Para headless: mvn test -Dheadless=true\n");
        sb.append("-->\n");
        sb.append("<suite name=\"Qorbit Test Suite\" verbose=\"1\" parallel=\"none\">\n\n");
        sb.append("    <listeners>\n");
        sb.append("        <listener class-name=\"listeners.TestListener\"/>\n");
        sb.append("    </listeners>\n\n");
        sb.append("    <test name=\"Testes Automatizados\" preserve-order=\"true\">\n");
        sb.append("        <classes>\n");
        for (String cls : classes)
            sb.append("            <class name=\"").append(cls).append("\"/>\n");
        sb.append("        </classes>\n");
        sb.append("    </test>\n\n");
        sb.append("</suite>\n");
        return sb.toString();
    }

    // ── README.md TestNG ──────────────────────────────────────────────────────

    private String tngReadme() {
        return """
# Qorbit — Projeto Exportado (Selenium + TestNG)

Arquitetura profissional de automacao UI: Java 17 + Selenium 4 + TestNG + Maven.

## Estrutura

```
src
├── main/java/config/
│   └── ConfigReader.java              — leitura de config.properties
└── test/
    ├── java/
    │   ├── base/BaseTest.java          — ciclo de vida do driver (@BeforeMethod/@AfterMethod)
    │   ├── driver/DriverFactory.java   — criacao do WebDriver (Chrome, Firefox, Edge)
    │   ├── pages/
    │   │   ├── BasePage.java           — base com WaitUtils e ElementUtils
    │   │   └── *Page.java              — page objects gerados
    │   ├── utils/
    │   │   ├── WaitUtils.java          — esperas explicitas centralizadas
    │   │   ├── ElementUtils.java       — operacoes reutilizaveis de interacao
    │   │   ├── ScreenshotUtils.java    — captura de evidencias em falha
    │   │   └── JsonDataReader.java     — leitura de dados de teste em JSON
    │   ├── listeners/TestListener.java — screenshot automatico (ITestListener)
    │   ├── models/UsuarioTeste.java    — model de dados de teste
    │   └── tests/*Test.java            — testes gerados
    └── resources/
        ├── config.properties           — configuracoes do ambiente
        ├── testng.xml                  — suite TestNG
        └── testdata/usuarios.json      — dados externos de teste
```

## Como executar

```bash
# Execucao padrao (visivel)
mvn test

# Modo headless (para CI/CD)
mvn test -Dheadless=true

# Navegador especifico
mvn test -Dbrowser=firefox
```

## Como adicionar uma nova pagina

1. Crie `src/test/java/pages/NovaPagina.java` estendendo `BasePage`
2. Declare locators como `private final By campo = By.cssSelector("...")`
3. Adicione metodos de acao usando `elementUtils` e `waitUtils`

## Como criar um novo teste

1. Crie `src/test/java/tests/NovaCenarioTest.java` estendendo `BaseTest`
2. Anote o metodo com `@Test`
3. Instancie Page Objects usando `driver` (herdado de `BaseTest`)
4. Use `Assert.assertTrue/assertEquals` do TestNG para validacoes

## Como registrar o novo teste na suite

Adicione em `testng.xml`:
```xml
<class name="tests.NovaCenarioTest"/>
```

## Como trocar de navegador

Edite `src/test/resources/config.properties`:
```properties
browser=firefox   # ou chrome, edge
```

Ou passe via linha de comando:
```bash
mvn test -Dbrowser=edge
```

## Capturas de evidencia

Screenshots de falha sao salvas automaticamente em `target/screenshots/`.
Configuravel em `config.properties` via `screenshots.dir`.

## Importante

Nao use `mvn spring-boot:run` — este projeto nao possui aplicacao Spring Boot.
""";
    }

    private String gerarDriverFactoryCiCd() {
        return "package config;\n\n"
                + "import io.github.bonigarcia.wdm.WebDriverManager;\n"
                + "import org.openqa.selenium.WebDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeOptions;\n\n"
                + "public class DriverFactory {\n\n"
                + "    private static WebDriver driver;\n\n"
                + "    public static WebDriver getDriver() {\n"
                + "        if (driver == null) {\n"
                + "            WebDriverManager.chromedriver().setup();\n"
                + "            ChromeOptions options = new ChromeOptions();\n"
                + "            boolean headless = !\"false\".equalsIgnoreCase(System.getProperty(\"headless\", \"true\"));\n"
                + "            if (headless) {\n"
                + "                options.addArguments(\"--headless=new\");\n"
                + "                options.addArguments(\"--no-sandbox\");\n"
                + "                options.addArguments(\"--disable-dev-shm-usage\");\n"
                + "                options.addArguments(\"--window-size=1920,1080\");\n"
                + "            }\n"
                + "            driver = new ChromeDriver(options);\n"
                + "            if (!headless) driver.manage().window().maximize();\n"
                + "        }\n"
                + "        return driver;\n"
                + "    }\n\n"
                + "    public static void encerrar() {\n"
                + "        if (driver != null) {\n"
                + "            driver.quit();\n"
                + "            driver = null;\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarGithubActionsWorkflow() {
        return "name: Testes Automatizados Qorbit\n\n"
                + "on:\n"
                + "  push:\n"
                + "    branches: [ \"**\" ]\n"
                + "  pull_request:\n"
                + "    branches: [ \"**\" ]\n\n"
                + "jobs:\n"
                + "  testes:\n"
                + "    runs-on: ubuntu-latest\n\n"
                + "    steps:\n"
                + "      - name: Checkout do repositório\n"
                + "        uses: actions/checkout@v4\n\n"
                + "      - name: Configurar Java 17\n"
                + "        uses: actions/setup-java@v4\n"
                + "        with:\n"
                + "          java-version: '17'\n"
                + "          distribution: 'temurin'\n"
                + "          cache: maven\n\n"
                + "      - name: Instalar Google Chrome\n"
                + "        run: |\n"
                + "          sudo apt-get update\n"
                + "          sudo apt-get install -y google-chrome-stable\n\n"
                + "      - name: Executar testes\n"
                + "        run: mvn clean test\n\n"
                + "      - name: Publicar artefatos de teste\n"
                + "        if: always()\n"
                + "        uses: actions/upload-artifact@v4\n"
                + "        with:\n"
                + "          name: relatorio-testes\n"
                + "          path: |\n"
                + "            target/relatorio.html\n"
                + "            target/evidencias/\n"
                + "            target/relatorio-cucumber.html\n"
                + "          retention-days: 30\n";
    }

    private String gerarReadmeCiCd() {
        return "# Qorbit - Projeto Exportado\n\n"
                + "## Como executar localmente\n\n"
                + "Execute dentro da pasta `qorbit-tests-export`:\n\n"
                + "```bash\n"
                + "mvn test -Dheadless=false\n"
                + "```\n\n"
                + "No Windows:\n\n"
                + "```bash\n"
                + "executar-testes.bat\n"
                + "```\n\n"
                + "No Linux/Mac:\n\n"
                + "```bash\n"
                + "chmod +x executar-testes.sh\n"
                + "./executar-testes.sh\n"
                + "```\n\n"
                + "## Como executar em modo headless\n\n"
                + "```bash\n"
                + "mvn clean test\n"
                + "```\n\n"
                + "> O modo headless é o **padrão**. Para ver o navegador, passe `-Dheadless=false`.\n\n"
                + "## Execução via GitHub Actions\n\n"
                + "Este projeto já contém o arquivo `.github/workflows/testes.yml`.\n\n"
                + "Para usar o pipeline:\n\n"
                + "1. Suba este projeto para um repositório GitHub\n"
                + "2. O pipeline executa automaticamente a cada `push` ou `pull_request`\n"
                + "3. Os artefatos (relatório HTML e evidências) ficam disponíveis na aba **Actions** do repositório\n\n"
                + "## Importante\n\n"
                + "Nao use `mvn spring-boot:run` neste projeto exportado — ele nao possui aplicacao Spring Boot.\n\n"
                + "## Melhorias incluidas nesta versao\n"
                + "- Pipeline GitHub Actions pronto para uso\n"
                + "- DriverFactory com headless configuravel via `-Dheadless=false`\n"
                + "- `--no-sandbox` e `--disable-dev-shm-usage` incluidos para compatibilidade com CI\n"
                + "- Resolucao padrao de 1920x1080 no headless\n"
                + "- Publicacao automatica de artefatos mesmo em caso de falha\n"
                + "- Page Objects (POM) gerados automaticamente\n"
                + "- Arquivos .feature (Gherkin) em pt-BR\n"
                + "- Step Definitions, TestRunner e Hooks incluidos\n"
                + "- Self-Healing local ativado\n"
                + "- Capturas de evidencia por step em target/evidencias\n"
                + "- Relatorio HTML em target/relatorio.html\n";
    }

    private Map<String, PageObjectSpec> construirPages(List<Elemento> elementos) {
        Map<String, List<Elemento>> porPagina = elementos.stream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.getPagina()).orElse("PaginaInicial"), LinkedHashMap::new, Collectors.toList()));

        Map<String, PageObjectSpec> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, List<Elemento>> entry : porPagina.entrySet()) {
            String rawPageName = entry.getKey();
            String className = normalizador.normalizarClasse(rawPageName, "PaginaInicial") + "Page";
            PageObjectSpec spec = new PageObjectSpec(className, rawPageName);
            Set<String> nomesUsados = new LinkedHashSet<>();
            for (Elemento el : entry.getValue()) {
                if (el.getSeletorTecnico() == null || el.getSeletorTecnico().isBlank()) continue;
                ElementContext ctx = parseContext(el);
                String baseMethod = normalizador.nomeMetodoParaElemento(el, prefixoParaElemento(el, ctx.componentType));
                String methodName = normalizador.tornarUnico(baseMethod, nomesUsados);
                spec.elementos.add(new ElementSpec(el, methodName, locatorFrom(el, ctx), ctx));
                spec.logicalToMethod.put(el.getNomeLogico().trim().toLowerCase(Locale.ROOT), methodName);
            }
            resultado.put(className, spec);
        }
        if (resultado.isEmpty()) {
            resultado.put("PaginaInicialPage", new PageObjectSpec("PaginaInicialPage", "PaginaInicial"));
        }
        return resultado;
    }

    private String gerarPageObject(PageObjectSpec page) {
        StringBuilder sb = new StringBuilder();
        sb.append("package pages;\n\n");
        sb.append("import org.openqa.selenium.By;\n");
        sb.append("import org.openqa.selenium.JavascriptExecutor;\n");
        sb.append("import org.openqa.selenium.NoSuchElementException;\n");
        sb.append("import org.openqa.selenium.StaleElementReferenceException;\n");
        sb.append("import org.openqa.selenium.ElementClickInterceptedException;\n");
        sb.append("import org.openqa.selenium.TimeoutException;\n");
        sb.append("import org.openqa.selenium.WebDriver;\n");
        sb.append("import org.openqa.selenium.WebElement;\n");
        sb.append("import org.openqa.selenium.support.ui.ExpectedConditions;\n");
        sb.append("import org.openqa.selenium.support.ui.Select;\n");
        sb.append("import org.openqa.selenium.support.ui.WebDriverWait;\n\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Locale;\n\n");
        sb.append("public class ").append(page.className).append(" {\n\n");
        sb.append("    private final WebDriver driver;\n");
        sb.append("    private final WebDriverWait wait;\n\n");
        sb.append("    public ").append(page.className).append("(WebDriver driver) {\n");
        sb.append("        this.driver = driver;\n");
        sb.append("        this.wait = new WebDriverWait(driver, Duration.ofSeconds(12));\n");
        sb.append("    }\n\n");
        sb.append("    public void abrir(String url) {\n");
        sb.append("        driver.get(url);\n");
        sb.append("        aguardarEstabilidade();\n");
        sb.append("    }\n\n");
        sb.append("    public void aguardarEstabilidade() {\n");
        sb.append("        try {\n");
        sb.append("            wait.until(d -> {\n");
        sb.append("                Object state = ((JavascriptExecutor) d).executeScript(\"return document.readyState\");\n");
        sb.append("                Object busy = ((JavascriptExecutor) d).executeScript(\"return !!document.querySelector('[aria-busy=\\\"true\\\"], .loading, .loader, .spinner, .ant-spin-spinning, .MuiCircularProgress-root');\");\n");
        sb.append("                return (\"complete\".equals(String.valueOf(state)) || \"interactive\".equals(String.valueOf(state))) && !Boolean.TRUE.equals(busy);\n");
        sb.append("            });\n");
        sb.append("        } catch (Exception ignored) { }\n");
        sb.append("    }\n\n");
        sb.append("    private void resetContext() {\n");
        sb.append("        driver.switchTo().defaultContent();\n");
        sb.append("    }\n\n");
        sb.append("    private void switchToFramePath(String framePath) {\n");
        sb.append("        resetContext();\n");
        sb.append("        if (framePath == null || framePath.isBlank() || \"root\".equalsIgnoreCase(framePath)) return;\n");
        sb.append("        String[] parts = framePath.split(\">\");\n");
        sb.append("        for (String part : parts) {\n");
        sb.append("            String token = part == null ? \"\" : part.trim();\n");
        sb.append("            if (token.isBlank() || \"root\".equalsIgnoreCase(token)) continue;\n");
        sb.append("            List<WebElement> frames = driver.findElements(By.cssSelector(\"iframe,frame\"));\n");
        sb.append("            WebElement alvo = null;\n");
        sb.append("            if (token.startsWith(\"index-\")) {\n");
        sb.append("                int idx = Integer.parseInt(token.substring(6));\n");
        sb.append("                if (idx >= 0 && idx < frames.size()) alvo = frames.get(idx);\n");
        sb.append("            } else {\n");
        sb.append("                for (WebElement frame : frames) {\n");
        sb.append("                    String name = frame.getAttribute(\"name\");\n");
        sb.append("                    String id = frame.getAttribute(\"id\");\n");
        sb.append("                    if (token.equals(name) || token.equals(id)) { alvo = frame; break; }\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("            if (alvo == null) throw new NoSuchElementException(\"Frame nao encontrado: \" + token);\n");
        sb.append("            driver.switchTo().frame(alvo);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    private WebElement localizarShadow(String shadowPath) {\n");
        sb.append("        Object found = ((JavascriptExecutor) driver).executeScript(\"\"\"\n");
        sb.append("                const path = arguments[0];\n");
        sb.append("                if (!path) return null;\n");
        sb.append("                const parts = String(path).split('>').map(p => p.trim()).filter(Boolean);\n");
        sb.append("                function queryIn(root, token) {\n");
        sb.append("                  if (!root || !token) return null;\n");
        sb.append("                  if (token.startsWith('shadow-host(') && token.endsWith(')')) {\n");
        sb.append("                    const hostSel = token.substring(12, token.length - 1).trim();\n");
        sb.append("                    return root.querySelector(hostSel);\n");
        sb.append("                  }\n");
        sb.append("                  return root.querySelector(token);\n");
        sb.append("                }\n");
        sb.append("                let currentRoot = document;\n");
        sb.append("                let currentEl = null;\n");
        sb.append("                for (const token of parts) {\n");
        sb.append("                  if (token.startsWith('shadow-host(')) {\n");
        sb.append("                    currentEl = queryIn(currentRoot, token);\n");
        sb.append("                    if (!currentEl) return null;\n");
        sb.append("                    currentRoot = currentEl.shadowRoot || currentEl;\n");
        sb.append("                    continue;\n");
        sb.append("                  }\n");
        sb.append("                  currentEl = queryIn(currentRoot, token);\n");
        sb.append("                  if (!currentEl) return null;\n");
        sb.append("                  if (currentEl.shadowRoot) currentRoot = currentEl.shadowRoot;\n");
        sb.append("                }\n");
        sb.append("                return currentEl;\n");
        sb.append("                \"\"\", shadowPath);\n");
        sb.append("        if (found instanceof WebElement element) return element;\n");
        sb.append("        throw new NoSuchElementException(\"Elemento shadow nao encontrado: \" + shadowPath);\n");
        sb.append("    }\n\n");
        sb.append("    private WebElement localizar(String tipo, String seletor, String framePath) {\n");
        sb.append("        switchToFramePath(framePath);\n");
        sb.append("        aguardarEstabilidade();\n");
        sb.append("        if (\"SHADOW_CSS\".equalsIgnoreCase(tipo)) return localizarShadow(seletor);\n");
        sb.append("        By by = switch ((tipo == null ? \"CSS\" : tipo).toUpperCase(Locale.ROOT)) {\n");
        sb.append("            case \"XPATH\" -> By.xpath(seletor);\n");
        sb.append("            case \"ID\" -> By.id(seletor);\n");
        sb.append("            case \"NAME\" -> By.name(seletor);\n");
        sb.append("            case \"LINK_TEXT\" -> By.linkText(seletor);\n");
        sb.append("            default -> By.cssSelector(seletor);\n");
        sb.append("        };\n");
        sb.append("        return wait.until(ExpectedConditions.presenceOfElementLocated(by));\n");
        sb.append("    }\n\n");
        sb.append("    private void scrollParaCentro(WebElement elemento) {\n");
        sb.append("        try { ((JavascriptExecutor) driver).executeScript(\"arguments[0].scrollIntoView({block:'center', inline:'center'});\", elemento); } catch (Exception ignored) { }\n");
        sb.append("    }\n\n");
        sb.append("    private boolean possuiInterceptacao(WebElement elemento) {\n");
        sb.append("        try {\n");
        sb.append("            Object result = ((JavascriptExecutor) driver).executeScript(\"\"\"\n");
        sb.append("                    const el = arguments[0];\n");
        sb.append("                    if (!el) return false;\n");
        sb.append("                    const r = el.getBoundingClientRect();\n");
        sb.append("                    const x = Math.min(Math.max(r.left + r.width / 2, 0), window.innerWidth - 1);\n");
        sb.append("                    const y = Math.min(Math.max(r.top + Math.min(r.height / 2, 10), 0), window.innerHeight - 1);\n");
        sb.append("                    const top = document.elementFromPoint(x, y);\n");
        sb.append("                    if (!top) return false;\n");
        sb.append("                    return !(top === el || el.contains(top) || top.contains(el));\n");
        sb.append("                    \"\"\", elemento);\n");
        sb.append("            return Boolean.TRUE.equals(result);\n");
        sb.append("        } catch (Exception ignored) { return false; }\n");
        sb.append("    }\n\n");
        sb.append("    private void aguardarAnimacaoCurta() {\n");
        sb.append("        try { Thread.sleep(250); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }\n");
        sb.append("    }\n\n");
        sb.append("    private void fecharOverlaysConhecidos() {\n");
        sb.append("        try {\n");
        sb.append("            ((JavascriptExecutor) driver).executeScript(\"\"\"\n");
        sb.append("                    const selectors = [\n");
        sb.append("                      '#onetrust-accept-btn-handler',\n");
        sb.append("                      'button[aria-label*=\"Fechar\"]',\n");
        sb.append("                      'button[aria-label*=\"fechar\"]',\n");
        sb.append("                      'button[title*=\"Fechar\"]',\n");
        sb.append("                      '[data-testid*=\"close\"]',\n");
        sb.append("                      '.cookie-banner button',\n");
        sb.append("                      '.cookies button',\n");
        sb.append("                      '.modal button.close',\n");
        sb.append("                      '.drawer button.close',\n");
        sb.append("                      '.lgpd button',\n");
        sb.append("                      'button'\n");
        sb.append("                    ];\n");
        sb.append("                    const texts = ['aceitar','ok','fechar','entendi','continuar','prosseguir'];\n");
        sb.append("                    function visible(el){\n");
        sb.append("                      if(!el) return false;\n");
        sb.append("                      const s = window.getComputedStyle(el);\n");
        sb.append("                      const r = el.getBoundingClientRect();\n");
        sb.append("                      return s && s.visibility !== 'hidden' && s.display !== 'none' && r.width > 0 && r.height > 0;\n");
        sb.append("                    }\n");
        sb.append("                    for (const sel of selectors) {\n");
        sb.append("                      for (const el of Array.from(document.querySelectorAll(sel))) {\n");
        sb.append("                        const txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim().toLowerCase();\n");
        sb.append("                        const genericButton = sel === 'button';\n");
        sb.append("                        if (visible(el) && (!genericButton || texts.some(t => txt.includes(t)))) { try { el.click(); return; } catch (e) {} }\n");
        sb.append("                      }\n");
        sb.append("                    }\n");
        sb.append("                    document.dispatchEvent(new KeyboardEvent('keydown', {key:'Escape', code:'Escape', keyCode:27, which:27, bubbles:true}));\n");
        sb.append("                    \"\"\");\n");
        sb.append("        } catch (Exception ignored) { }\n");
        sb.append("        aguardarAnimacaoCurta();\n");
        sb.append("    }\n\n");
        sb.append("    private void clicarViaJs(WebElement elemento) {\n");
        sb.append("        ((JavascriptExecutor) driver).executeScript(\"arguments[0].click();\", elemento);\n");
        sb.append("    }\n\n");
        sb.append("    public void clicar(WebElement elemento) {\n");
        sb.append("        Exception ultimaFalha = null;\n");
        sb.append("        for (int tentativa = 0; tentativa < 3; tentativa++) {\n");
        sb.append("            try {\n");
        sb.append("                aguardarEstabilidade();\n");
        sb.append("                scrollParaCentro(elemento);\n");
        sb.append("                aguardarAnimacaoCurta();\n");
        sb.append("                if (possuiInterceptacao(elemento)) { fecharOverlaysConhecidos(); scrollParaCentro(elemento); }\n");
        sb.append("                wait.until(ExpectedConditions.elementToBeClickable(elemento)).click();\n");
        sb.append("                return;\n");
        sb.append("            } catch (ElementClickInterceptedException | TimeoutException e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                fecharOverlaysConhecidos();\n");
        sb.append("                try { scrollParaCentro(elemento); } catch (Exception ignored) { }\n");
        sb.append("                if (tentativa == 2) {\n");
        sb.append("                    try { clicarViaJs(elemento); return; } catch (Exception jsEx) { ultimaFalha = jsEx; }\n");
        sb.append("                }\n");
        sb.append("            } catch (StaleElementReferenceException e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                aguardarAnimacaoCurta();\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                try { clicarViaJs(elemento); return; } catch (Exception jsEx) { ultimaFalha = jsEx; }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        throw new RuntimeException(\"Falha ao clicar no elemento de forma resiliente\", ultimaFalha);\n");
        sb.append("    }\n\n");
        // Overload resiliente: re-localiza o elemento em cada tentativa para evitar StaleElementReferenceException
        sb.append("    public void clicar(By locator) {\n");
        sb.append("        Exception ultimaFalha = null;\n");
        sb.append("        for (int tentativa = 0; tentativa < 4; tentativa++) {\n");
        sb.append("            try {\n");
        sb.append("                aguardarEstabilidade();\n");
        sb.append("                WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(locator));\n");
        sb.append("                scrollParaCentro(elemento);\n");
        sb.append("                aguardarAnimacaoCurta();\n");
        sb.append("                if (possuiInterceptacao(elemento)) { fecharOverlaysConhecidos(); elemento = driver.findElement(locator); scrollParaCentro(elemento); }\n");
        sb.append("                elemento.click();\n");
        sb.append("                return;\n");
        sb.append("            } catch (ElementClickInterceptedException e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                fecharOverlaysConhecidos();\n");
        sb.append("                if (tentativa == 3) {\n");
        sb.append("                    try { clicarViaJs(driver.findElement(locator)); return; } catch (Exception jsEx) { ultimaFalha = jsEx; }\n");
        sb.append("                }\n");
        sb.append("            } catch (StaleElementReferenceException e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                aguardarAnimacaoCurta();\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                try { clicarViaJs(driver.findElement(locator)); return; } catch (Exception jsEx) { ultimaFalha = jsEx; }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        throw new RuntimeException(\"Falha ao clicar no elemento de forma resiliente\", ultimaFalha);\n");
        sb.append("    }\n\n");
        sb.append("    public void preencher(String valor, WebElement elemento) {\n");
        sb.append("        try { elemento.clear(); } catch (Exception ignored) { }\n");
        sb.append("        elemento.sendKeys(valor);\n");
        sb.append("    }\n\n");
        sb.append("    /**\n");
        sb.append("     * Preenche input[type=\"date\"] via JavaScript.\n");
        sb.append("     * Suporta formatos: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy\n");
        sb.append("     * Se o campo abrir calendário, tenta digitação directa.\n");
        sb.append("     */\n");
        sb.append("    public void preencherData(String valor, WebElement elemento) {\n");
        sb.append("        try {\n");
        sb.append("            // Normaliza para yyyy-MM-dd (formato aceite pelo input[type=date])\n");
        sb.append("            String dataFormatada = valor;\n");
        sb.append("            if (valor != null && valor.matches(\"\\\\d{2}[/\\\\-]\\\\d{2}[/\\\\-]\\\\d{4}\")) {\n");
        sb.append("                String[] partes = valor.split(\"[/\\\\-]\");\n");
        sb.append("                dataFormatada = partes[2] + \"-\" + partes[1] + \"-\" + partes[0];\n");
        sb.append("            }\n");
        sb.append("            // Tenta via JavaScript (mais fiável para input[type=date])\n");
        sb.append("            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\n");
        sb.append("                \"arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', {bubbles:true})); arguments[0].dispatchEvent(new Event('change', {bubbles:true}));\",\n");
        sb.append("                elemento, dataFormatada);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            // Fallback: tenta sendKeys\n");
        sb.append("            try { elemento.clear(); } catch (Exception ignored) { }\n");
        sb.append("            elemento.sendKeys(valor != null ? valor : \"\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    public void interagirComSelecao(String valor, WebElement elemento, String componentType) {\n");
        sb.append("        String tipo = componentType == null ? \"\" : componentType.toUpperCase(Locale.ROOT);\n");
        sb.append("        switch (tipo) {\n");
        sb.append("            case \"SELECT\" -> new Select(elemento).selectByVisibleText(valor);\n");
        sb.append("            case \"CUSTOM_SELECT\", \"AUTOCOMPLETE\" -> {\n");
        sb.append("                clicar(elemento);\n");
        sb.append("                try { elemento.sendKeys(valor); } catch (Exception ignored) { }\n");
        sb.append("                WebElement opcao = wait.until(d -> {\n");
        sb.append("                    List<WebElement> matches = d.findElements(By.xpath(\"//*[self::li or self::div or self::span or self::button][normalize-space()=\\\"\" + valor + \"\\\" or contains(normalize-space(),\\\"\" + valor + \"\\\")]\"));\n");
        sb.append("                    return matches.isEmpty() ? null : matches.get(0);\n");
        sb.append("                });\n");
        sb.append("                clicar(opcao);\n");
        sb.append("            }\n");
        sb.append("            case \"DATEPICKER\" -> {\n");
        sb.append("                preencherData(valor, elemento);\n");
        sb.append("            }\n");
        sb.append("            default -> preencher(valor, elemento);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        for (ElementSpec element : page.elementos) {
            String tipoLiteral = normalizador.literalJava(element.locator().tipo());
            String seletorLiteral = normalizador.literalJava(element.locator().valor());
            String frameLiteral = normalizador.literalJava(element.context().framePath());

            sb.append("    // ").append(normalizador.literalJava(element.source().getNomeLogico())).append("\n");
            sb.append("    public WebElement ").append(element.methodName()).append("() {\n");
            sb.append("        return localizar(\"").append(tipoLiteral).append("\", \"")
                    .append(seletorLiteral).append("\", \"").append(frameLiteral).append("\");\n");
            sb.append("    }\n\n");

            // Método byXxx() — retorna By para uso no clicar(By) resiliente
            String byMethod = "by" + Character.toUpperCase(element.methodName().charAt(0)) + element.methodName().substring(1);
            sb.append("    public By ").append(byMethod).append("() {\n");
            String byExpression = switch (tipoLiteral.toUpperCase(Locale.ROOT)) {
                case "XPATH" -> "By.xpath(\"" + seletorLiteral + "\")";
                case "ID" -> "By.id(\"" + seletorLiteral + "\")";
                case "NAME" -> "By.name(\"" + seletorLiteral + "\")";
                case "LINK_TEXT" -> "By.linkText(\"" + seletorLiteral + "\")";
                default -> "By.cssSelector(\"" + seletorLiteral + "\")";
            };
            sb.append("        return ").append(byExpression).append(";\n");
            sb.append("    }\n\n");

            sb.append("    public String ").append(element.methodName()).append("TipoComponente() {\n");
            sb.append("        return \"").append(normalizador.literalJava(element.context().componentType())).append("\";\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String gerarFeature(CasoDeTeste caso) {
        StringBuilder sb = new StringBuilder();
        sb.append("# language: pt\n");
        sb.append("# Gerado pelo Qorbit\n\n");
        sb.append("Funcionalidade: ").append(safe(caso.getNome())).append("\n\n");
        sb.append("  Cenario: ").append(safe(caso.getNome())).append("\n");

        String prefixoCaso = prefixoCenario(caso);
        List<StepTeste> steps = orderedSteps(caso);
        for (int i = 0; i < steps.size(); i++) {
            StepTeste step = steps.get(i);
            String keyword = i == 0 ? "Dado" : ("VALIDAR".equalsIgnoreCase(step.getAcao()) ? "Entao" : "E");
            String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
            String desc = descricaoStep(step);
            // NAVEGAR fica sem prefixo pois é tratado pelo CommonSteps com expressão genérica
            String textoStep = "NAVEGAR".equals(acao) ? desc : prefixoCaso + desc;
            sb.append("    ").append(keyword).append(" ").append(textoStep).append("\n");
        }
        return sb.toString();
    }

    private String gerarStepDefinitions(CasoDeTeste caso,
                                        Map<String, PageObjectSpec> pages,
                                        Map<String, Elemento> elementosPorNome) {
        String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
        StringBuilder sb = new StringBuilder();
        sb.append("package steps;\n\n");
        sb.append("import config.DriverFactory;\n");
        sb.append("import io.cucumber.java.pt.*;\n");
        sb.append("import org.junit.Assert;\n");
        sb.append("import org.openqa.selenium.WebDriver;\n");

        LinkedHashMap<String, String> pageVars = new LinkedHashMap<>();
        List<ResolvedStep> resolvedSteps = resolverSteps(caso, pages, elementosPorNome);
        for (ResolvedStep step : resolvedSteps) {
            if (step.pageClassName() != null && !pageVars.containsKey(step.pageClassName())) {
                pageVars.put(step.pageClassName(), lowerFirst(step.pageClassName()));
                sb.append("import pages.").append(step.pageClassName()).append(";\n");
            }
        }
        if (pageVars.isEmpty()) {
            String fallbackPage = pages.keySet().iterator().next();
            pageVars.put(fallbackPage, lowerFirst(fallbackPage));
            sb.append("import pages.").append(fallbackPage).append(";\n");
        }

        sb.append("\npublic class ").append(nomeClasse).append("Steps {\n\n");
        sb.append("    private final WebDriver driver = DriverFactory.getDriver();\n");
        for (Map.Entry<String, String> pageVar : pageVars.entrySet()) {
            sb.append("    private final ").append(pageVar.getKey()).append(" ").append(pageVar.getValue())
                    .append(" = new ").append(pageVar.getKey()).append("(driver);\n");
        }
        sb.append("\n");

        String prefixoCaso = prefixoCenario(caso);
        List<StepTeste> ordered = orderedSteps(caso);
        Set<String> expressoesGeradas = new LinkedHashSet<>();
        int metodoSeq = 1;
        for (int i = 0; i < ordered.size(); i++) {
            StepTeste step = ordered.get(i);
            String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
            if ("NAVEGAR".equals(acao)) {
                continue;
            }
            ResolvedStep resolved = i < resolvedSteps.size() ? resolvedSteps.get(i) : null;
            String annotation = i == 0 ? "@Dado" : ("VALIDAR".equalsIgnoreCase(step.getAcao()) ? "@Entao" : "@E");
            // Prefixo garante unicidade global entre steps de casos diferentes
            String expressaoRaw = prefixoCaso + expressaoStep(step);
            if (!expressoesGeradas.add(expressaoRaw)) {
                continue;
            }
            String expressao = normalizador.literalJava(expressaoRaw);
            String assinaturaMetodo = expressaoTemParametro(step) ? "(String valorStep)" : "()";
            sb.append("    ").append(annotation).append("(\"").append(expressao).append("\")\n");
            sb.append("    public void step").append(metodoSeq++).append(assinaturaMetodo).append(" {\n");
            gerarCorpoStep(sb, step, resolved, pageVars);
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private void gerarCorpoStep(StringBuilder sb, StepTeste step, ResolvedStep resolved, Map<String, String> pageVars) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String pageVar = resolved != null && resolved.pageClassName() != null
                ? pageVars.getOrDefault(resolved.pageClassName(), pageVars.values().iterator().next())
                : pageVars.values().iterator().next();
        String valor = normalizador.literalJava(Optional.ofNullable(step.getValorEntrada()).orElse(""));
        String valorReferencia = expressaoTemParametro(step) ? "valorStep" : "\"" + valor + "\"";

        switch (acao) {
            case "NAVEGAR" -> sb.append("        ").append(pageVar).append(".abrir(").append(valorReferencia).append(");\n");
            case "PREENCHER" -> {
                if (resolved != null && resolved.methodName() != null) {
                    if (isDynamicComponent(resolved.componentType())) {
                        sb.append("        ").append(pageVar).append(".interagirComSelecao(").append(valorReferencia).append(", ")
                                .append(pageVar).append(".").append(resolved.methodName()).append("(), ")
                                .append(pageVar).append(".").append(resolved.methodName()).append("TipoComponente());\n");
                    } else {
                        sb.append("        ").append(pageVar).append(".preencher(").append(valorReferencia).append(", ")
                                .append(pageVar).append(".").append(resolved.methodName()).append("());\n");
                    }
                } else {
                    failElemento(sb, "preenchimento", step);
                }
            }
            case "CLICAR" -> {
                if (resolved != null && resolved.methodName() != null) {
                    String byMethod = "by" + Character.toUpperCase(resolved.methodName().charAt(0)) + resolved.methodName().substring(1);
                    sb.append("        ").append(pageVar).append(".clicar(")
                            .append(pageVar).append(".").append(byMethod).append("());\n");
                } else {
                    failElemento(sb, "clique", step);
                }
            }
            case "VALIDAR" -> {
                if (resolved != null && resolved.methodName() != null) {
                    sb.append("        Assert.assertTrue(").append(pageVar).append(".").append(resolved.methodName())
                            .append("().isDisplayed());\n");
                } else {
                    failElemento(sb, "validacao", step);
                }
            }
            case "SELECIONAR" -> {
                if (resolved != null && resolved.methodName() != null) {
                    sb.append("        ").append(pageVar).append(".interagirComSelecao(").append(valorReferencia).append(", ")
                            .append(pageVar).append(".").append(resolved.methodName()).append("(), ")
                            .append(pageVar).append(".").append(resolved.methodName()).append("TipoComponente());\n");
                } else {
                    failElemento(sb, "selecao", step);
                }
            }
            default -> sb.append("        // Acao nao suportada: ").append(normalizador.literalJava(acao)).append("\n");
        }
    }

    private void failElemento(StringBuilder sb, String tipoFalha, StepTeste step) {
        sb.append("        Assert.fail(\"Elemento nao encontrado para ").append(tipoFalha).append(": ")
                .append(normalizador.literalJava(Optional.ofNullable(step.getNomeLogicoElemento()).orElse("")))
                .append("\");\n");
    }

    private List<ResolvedStep> resolverSteps(CasoDeTeste caso,
                                             Map<String, PageObjectSpec> pages,
                                             Map<String, Elemento> elementosPorNome) {
        List<ResolvedStep> resolvidos = new ArrayList<>();
        for (StepTeste step : orderedSteps(caso)) {
            String nomeLogico = Optional.ofNullable(step.getNomeLogicoElemento()).orElse("").trim().toLowerCase(Locale.ROOT);
            Elemento elemento = elementosPorNome.get(nomeLogico);
            if (elemento == null) {
                resolvidos.add(new ResolvedStep(null, null, null));
                continue;
            }
            String pageClassName = normalizador.normalizarClasse(elemento.getPagina(), "PaginaInicial") + "Page";
            PageObjectSpec page = pages.get(pageClassName);
            String methodName = page != null ? page.logicalToMethod.get(nomeLogico) : null;
            String componentType = parseContext(elemento).componentType();
            resolvidos.add(new ResolvedStep(pageClassName, methodName, componentType));
        }
        return resolvidos;
    }

    private LocatorSpec locatorFrom(Elemento elemento, ElementContext ctx) {
        String tipo = Optional.ofNullable(elemento.getTipoSeletor()).orElse("CSS").toUpperCase(Locale.ROOT);
        String seletor = Optional.ofNullable(elemento.getSeletorTecnico()).orElse("");
        if ("SHADOW_CSS".equals(tipo) && (seletor.isBlank() || !seletor.contains("shadow-host("))) {
            tipo = "CSS";
        }
        return new LocatorSpec(tipo, seletor);
    }

    private ElementContext parseContext(Elemento elemento) {
        String descricao = Optional.ofNullable(elemento.getDescricao()).orElse("");
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String part : descricao.split("\\\\|")) {
            String token = part.trim();
            int idx = token.indexOf('=');
            if (idx > 0) {
                parsed.put(token.substring(0, idx).trim(), token.substring(idx + 1).trim());
            }
        }
        return new ElementContext(
                parsed.getOrDefault("frame", "root"),
                parsed.getOrDefault("componentType", "INTERACTIVE"),
                parsed.getOrDefault("shadowDom", "false")
        );
    }

    private boolean isDynamicComponent(String componentType) {
        if (componentType == null) return false;
        return Set.of("CUSTOM_SELECT", "AUTOCOMPLETE", "DATEPICKER", "SELECT").contains(componentType.toUpperCase(Locale.ROOT));
    }

    private String prefixoCenario(CasoDeTeste caso) {
        return caso.getNome().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "") + ": ";
    }

    private String expressaoStep(StepTeste step) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String nome = Optional.ofNullable(step.getNomeLogicoElemento()).orElse("elemento");
        return switch (acao) {
            case "PREENCHER" -> "preencho " + nome + " com {string}";
            case "NAVEGAR" -> "acesso a URL {string}";
            case "SELECIONAR" -> "seleciono {string} em " + nome;
            default -> descricaoStep(step);
        };
    }

    private boolean expressaoTemParametro(StepTeste step) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        return Set.of("NAVEGAR", "PREENCHER", "SELECIONAR").contains(acao);
    }

    private String descricaoStep(StepTeste step) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String nome = Optional.ofNullable(step.getNomeLogicoElemento()).orElse("elemento");
        String valor = Optional.ofNullable(step.getValorEntrada()).orElse("");
        return switch (acao) {
            case "CLICAR" -> "clico em " + nome;
            case "PREENCHER" -> "preencho " + nome + " com \"" + valor + "\"";
            case "NAVEGAR" -> "acesso a URL \"" + valor + "\"";
            case "VALIDAR" -> nome + " esta visivel";
            case "SELECIONAR" -> "seleciono \"" + valor + "\" em " + nome;
            default -> Optional.ofNullable(step.getDescricaoGherkin()).orElse(acao + " " + nome);
        };
    }

    private List<StepTeste> orderedSteps(CasoDeTeste caso) {
        if (caso.getSteps() == null) return List.of();
        return caso.getSteps().stream()
                .sorted(Comparator.comparingInt(s -> Optional.ofNullable(s.getNumeroStep()).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private Elemento priorizarElemento(Elemento a, Elemento b) {
        return scoreElemento(b) > scoreElemento(a) ? b : a;
    }

    private int scoreElemento(Elemento e) {
        String tipo = Optional.ofNullable(e.getTipoSeletor()).orElse("").toUpperCase(Locale.ROOT);
        int base = switch (tipo) {
            case "ID" -> 100;
            case "CSS" -> 90;
            case "NAME" -> 80;
            case "LINK_TEXT" -> 70;
            case "SHADOW_CSS" -> 85;
            default -> 60;
        };
        String descricao = Optional.ofNullable(e.getDescricao()).orElse("");
        if (descricao.contains("dataTestId=")) base += 15;
        if (descricao.contains("frame=")) base += 5;
        return base;
    }

    private String prefixoParaElemento(Elemento elemento, String componentType) {
        String nome = Optional.ofNullable(elemento.getNomeLogico()).orElse("").toLowerCase(Locale.ROOT);
        String component = Optional.ofNullable(componentType).orElse("").toUpperCase(Locale.ROOT);
        if (Set.of("SELECT","CUSTOM_SELECT").contains(component) || nome.startsWith("select")) return "select";
        if (Set.of("BUTTON","MODAL_ACTION").contains(component) || nome.startsWith("botao") || nome.startsWith("link")) return "botao";
        if ("AUTOCOMPLETE".equals(component)) return "autoComplete";
        if ("DATEPICKER".equals(component)) return "data";
        return "campo";
    }

    private String lowerFirst(String texto) {
        return texto == null || texto.isBlank() ? "page" : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }

    private void adicionarArquivo(ZipOutputStream zip, String caminho, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(caminho));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String gerarCommonSteps(Map<String, PageObjectSpec> pages) {
        String defaultPage = pages.keySet().iterator().next();
        return "package steps;\n\n"
                + "import config.DriverFactory;\n"
                + "import io.cucumber.java.pt.Dado;\n"
                + "import org.openqa.selenium.WebDriver;\n"
                + "import pages." + defaultPage + ";\n\n"
                + "public class CommonSteps {\n\n"
                + "    private final WebDriver driver = DriverFactory.getDriver();\n"
                + "    private final " + defaultPage + " page = new " + defaultPage + "(driver);\n\n"
                + "    @Dado(\"acesso a URL {string}\")\n"
                + "    public void acessoAUrl(String valorStep) {\n"
                + "        page.abrir(valorStep);\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarTestRunner() {
        return "package runner;\n\n"
                + "import io.cucumber.junit.Cucumber;\n"
                + "import io.cucumber.junit.CucumberOptions;\n"
                + "import org.junit.runner.RunWith;\n\n"
                + "@RunWith(Cucumber.class)\n"
                + "@CucumberOptions(\n"
                + "    features = \"src/test/resources/features\",\n"
                + "    glue = {\"steps\", \"config\"},\n"
                + "    plugin = {\n"
                + "        \"pretty\",\n"
                + "        \"html:target/relatorio-cucumber.html\",\n"
                + "        \"json:target/relatorio-cucumber.json\"\n"
                + "    },\n"
                + "    monochrome = true\n"
                + ")\n"
                + "public class TestRunner {\n"
                + "}\n";
    }

    private String gerarDriverFactory() {
        return "package config;\n\n"
                + "import io.github.bonigarcia.wdm.WebDriverManager;\n"
                + "import org.openqa.selenium.WebDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeOptions;\n\n"
                + "public class DriverFactory {\n\n"
                + "    private static WebDriver driver;\n\n"
                + "    public static WebDriver getDriver() {\n"
                + "        if (driver == null) {\n"
                + "            WebDriverManager.chromedriver().setup();\n"
                + "            ChromeOptions options = new ChromeOptions();\n"
                + "            // options.addArguments(\"--headless=new\");\n"
                + "            driver = new ChromeDriver(options);\n"
                + "            driver.manage().window().maximize();\n"
                + "        }\n"
                + "        return driver;\n"
                + "    }\n\n"
                + "    public static void encerrar() {\n"
                + "        if (driver != null) {\n"
                + "            driver.quit();\n"
                + "            driver = null;\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarHooks() {
        return "package config;\n\n"
                + "import io.cucumber.java.After;\n"
                + "import io.cucumber.java.AfterStep;\n"
                + "import io.cucumber.java.Before;\n"
                + "import io.cucumber.java.Scenario;\n"
                + "import org.openqa.selenium.OutputType;\n"
                + "import org.openqa.selenium.TakesScreenshot;\n"
                + "import report.ExecutionContext;\n"
                + "import report.FeatureStepCatalog;\n"
                + "import report.HtmlReportGenerator;\n\n"
                + "public class Hooks {\n\n"
                + "    @Before\n"
                + "    public void antes(Scenario scenario) {\n"
                + "        DriverFactory.getDriver();\n"
                + "        // Credenciais via terminal: mvn test -Dambiente.url=X -Dambiente.usuario=Y -Dambiente.senha=Z\n"
                + "        String url = System.getProperty(\"ambiente.url\", \"\");\n"
                + "        String usuario = System.getProperty(\"ambiente.usuario\", \"\");\n"
                + "        String senha = System.getProperty(\"ambiente.senha\", \"\");\n"
                + "        if (!url.isBlank()) {\n"
                + "            DriverFactory.getDriver().get(url);\n"
                + "            System.out.println(\"[Hooks] Navegando para: \" + url);\n"
                + "        }\n"
                + "        if (!usuario.isBlank() && !senha.isBlank()) {\n"
                + "            System.out.println(\"[Hooks] Credenciais recebidas para usuario: \" + usuario);\n"
                + "            // Adapte os seletores abaixo ao login do seu sistema\n"
                + "            try {\n"
                + "                org.openqa.selenium.WebElement campoUser = DriverFactory.getDriver().findElement(org.openqa.selenium.By.cssSelector(\"input[type=text], input[name*=user], input[id*=user], input[name*=login], input[id*=login]\"));\n"
                + "                campoUser.sendKeys(usuario);\n"
                + "                org.openqa.selenium.WebElement campoSenha = DriverFactory.getDriver().findElement(org.openqa.selenium.By.cssSelector(\"input[type=password]\"));\n"
                + "                campoSenha.sendKeys(senha);\n"
                + "                org.openqa.selenium.WebElement btnLogin = DriverFactory.getDriver().findElement(org.openqa.selenium.By.cssSelector(\"button[type=submit], input[type=submit]\"));\n"
                + "                btnLogin.click();\n"
                + "                System.out.println(\"[Hooks] Login efectuado.\");\n"
                + "            } catch (Exception e) {\n"
                + "                System.out.println(\"[Hooks] Login automatico falhou: \" + e.getMessage() + \". Adapte os seletores no Hooks.java.\");\n"
                + "            }\n"
                + "        }\n"
                + "        ExecutionContext.iniciarCenario(scenario.getName(), DriverFactory.getDriver().getCurrentUrl());\n"
                + "        System.out.println(\"Iniciando cenario: \" + scenario.getName());\n"
                + "    }\n\n"
                + "    @AfterStep\n"
                + "    public void aposCadaStep(Scenario scenario) {\n"
                + "        try {\n"
                + "            if (!(DriverFactory.getDriver() instanceof TakesScreenshot takesScreenshot)) {\n"
                + "                return;\n"
                + "            }\n"
                + "            byte[] screenshot = takesScreenshot.getScreenshotAs(OutputType.BYTES);\n"
                + "            int indiceStep = ExecutionContext.proximoIndiceStep(scenario.getName());\n"
                + "            String descricao = FeatureStepCatalog.descricaoDoStep(scenario.getName(), indiceStep);\n"
                + "            String status = scenario.isFailed() ? \"FALHOU\" : \"PASSOU\";\n"
                + "            String nomeArquivo = ExecutionContext.salvarScreenshot(indiceStep, status, screenshot);\n"
                + "            scenario.attach(screenshot, \"image/png\", \"Step \" + indiceStep + \" - \" + descricao);\n"
                + "            ExecutionContext.registrarStep(scenario.getName(), indiceStep, descricao, status, nomeArquivo, scenario.isFailed() ? \"Falha no step\" : null);\n"
                + "        } catch (Exception e) {\n"
                + "            System.out.println(\"Erro ao capturar evidencia do step: \" + e.getMessage());\n"
                + "        }\n"
                + "    }\n\n"
                + "    @After\n"
                + "    public void depois(Scenario scenario) {\n"
                + "        try {\n"
                + "            if (scenario.isFailed()) {\n"
                + "                ExecutionContext.registrarErroFinal(scenario.getName(), \"Cenario falhou. Verifique a última evidência e o console para detalhes.\");\n"
                + "            }\n"
                + "            HtmlReportGenerator.gerar(ExecutionContext.snapshot(), ExecutionContext.getUrlBase());\n"
                + "        } catch (Exception e) {\n"
                + "            System.out.println(\"Erro ao gerar relatório final: \" + e.getMessage());\n"
                + "        } finally {\n"
                + "            DriverFactory.encerrar();\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarStepEvidence() {
        return "package report;\n\n"
                + "public record StepEvidence(int indice, String descricao, String status, String detalheFalha, String imagemRelativa) { }\n";
    }

    private String gerarExecutionContext() {
        return "package report;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.nio.file.Paths;\n"
                + "import java.time.Duration;\n"
                + "import java.time.Instant;\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.LinkedHashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "public final class ExecutionContext {\n\n"
                + "    private static final Path TARGET_DIR = Paths.get(\"target\");\n"
                + "    private static final Path EVIDENCIAS_DIR = TARGET_DIR.resolve(\"evidencias\");\n"
                + "    private static final Map<String, List<StepEvidence>> STEPS_POR_CENARIO = new LinkedHashMap<>();\n"
                + "    private static final Map<String, Integer> CONTADORES = new ConcurrentHashMap<>();\n"
                + "    private static final Map<String, Instant> INICIO_CENARIO = new ConcurrentHashMap<>();\n"
                + "    private static String urlBase = \"\";\n\n"
                + "    private ExecutionContext() { }\n\n"
                + "    public static synchronized void iniciarCenario(String nomeCenario, String urlAtual) {\n"
                + "        try {\n"
                + "            Files.createDirectories(EVIDENCIAS_DIR);\n"
                + "        } catch (IOException e) {\n"
                + "            throw new IllegalStateException(\"Nao foi possivel criar a pasta de evidencias\", e);\n"
                + "        }\n"
                + "        STEPS_POR_CENARIO.putIfAbsent(nomeCenario, new ArrayList<>());\n"
                + "        CONTADORES.put(nomeCenario, 0);\n"
                + "        INICIO_CENARIO.put(nomeCenario, Instant.now());\n"
                + "        if ((urlBase == null || urlBase.isBlank()) && urlAtual != null) {\n"
                + "            urlBase = urlAtual;\n"
                + "        }\n"
                + "    }\n\n"
                + "    public static synchronized int proximoIndiceStep(String nomeCenario) {\n"
                + "        int proximo = CONTADORES.getOrDefault(nomeCenario, 0) + 1;\n"
                + "        CONTADORES.put(nomeCenario, proximo);\n"
                + "        return proximo;\n"
                + "    }\n\n"
                + "    public static synchronized String salvarScreenshot(int indice, String status, byte[] bytes) throws IOException {\n"
                + "        Files.createDirectories(EVIDENCIAS_DIR);\n"
                + "        String nomeArquivo = \"step-\" + indice + \"-\" + (\"FALHOU\".equalsIgnoreCase(status) ? \"fail\" : \"ok\") + \"-t1.png\";\n"
                + "        Files.write(EVIDENCIAS_DIR.resolve(nomeArquivo), bytes);\n"
                + "        return \"evidencias/\" + nomeArquivo;\n"
                + "    }\n\n"
                + "    public static synchronized void registrarStep(String nomeCenario, int indice, String descricao, String status, String imagemRelativa, String detalheFalha) {\n"
                + "        STEPS_POR_CENARIO.computeIfAbsent(nomeCenario, chave -> new ArrayList<>())\n"
                + "                .add(new StepEvidence(indice, descricao, status, detalheFalha, imagemRelativa));\n"
                + "    }\n\n"
                + "    public static synchronized void registrarErroFinal(String nomeCenario, String mensagem) {\n"
                + "        List<StepEvidence> steps = STEPS_POR_CENARIO.get(nomeCenario);\n"
                + "        if (steps == null || steps.isEmpty()) return;\n"
                + "        StepEvidence ultimo = steps.get(steps.size() - 1);\n"
                + "        steps.set(steps.size() - 1, new StepEvidence(ultimo.indice(), ultimo.descricao(), \"FALHOU\", mensagem, ultimo.imagemRelativa()));\n"
                + "    }\n\n"
                + "    public static synchronized ReportSnapshot snapshot() {\n"
                + "        int total = STEPS_POR_CENARIO.values().stream().mapToInt(List::size).sum();\n"
                + "        int passou = (int) STEPS_POR_CENARIO.values().stream().flatMap(List::stream).filter(s -> \"PASSOU\".equalsIgnoreCase(s.status())).count();\n"
                + "        int falhou = total - passou;\n"
                + "        Duration duracao = Duration.between(INICIO_CENARIO.values().stream().min(Instant::compareTo).orElse(Instant.now()), Instant.now());\n"
                + "        Map<String, List<StepEvidence>> copia = new LinkedHashMap<>();\n"
                + "        for (Map.Entry<String, List<StepEvidence>> entry : STEPS_POR_CENARIO.entrySet()) {\n"
                + "            copia.put(entry.getKey(), new ArrayList<>(entry.getValue()));\n"
                + "        }\n"
                + "        return new ReportSnapshot(copia, total, passou, falhou, duracao.getSeconds());\n"
                + "    }\n\n"
                + "    public static String getUrlBase() {\n"
                + "        return urlBase == null ? \"\" : urlBase;\n"
                + "    }\n\n"
                + "    public record ReportSnapshot(Map<String, List<StepEvidence>> stepsPorCenario, int total, int passou, int falhou, long duracaoSegundos) { }\n"
                + "}\n";
    }

    private String gerarFeatureStepCatalog() {
        return "package report;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.nio.file.Paths;\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.stream.Stream;\n\n"
                + "public final class FeatureStepCatalog {\n\n"
                + "    private static final Map<String, List<String>> STEPS_POR_CENARIO = carregar();\n\n"
                + "    private FeatureStepCatalog() { }\n\n"
                + "    public static String descricaoDoStep(String nomeCenario, int indiceStep) {\n"
                + "        List<String> steps = STEPS_POR_CENARIO.getOrDefault(nomeCenario, List.of());\n"
                + "        if (indiceStep > 0 && indiceStep <= steps.size()) {\n"
                + "            return steps.get(indiceStep - 1);\n"
                + "        }\n"
                + "        return \"Step \" + indiceStep;\n"
                + "    }\n\n"
                + "    private static Map<String, List<String>> carregar() {\n"
                + "        Map<String, List<String>> mapa = new HashMap<>();\n"
                + "        Path raiz = Paths.get(\"src\", \"test\", \"resources\", \"features\");\n"
                + "        if (!Files.exists(raiz)) return mapa;\n"
                + "        try (Stream<Path> arquivos = Files.walk(raiz)) {\n"
                + "            arquivos.filter(path -> path.toString().endsWith(\".feature\")).forEach(path -> carregarArquivo(path, mapa));\n"
                + "        } catch (IOException ignored) { }\n"
                + "        return mapa;\n"
                + "    }\n\n"
                + "    private static void carregarArquivo(Path arquivo, Map<String, List<String>> mapa) {\n"
                + "        try {\n"
                + "            List<String> linhas = Files.readAllLines(arquivo);\n"
                + "            String cenarioAtual = null;\n"
                + "            List<String> steps = new ArrayList<>();\n"
                + "            for (String linha : linhas) {\n"
                + "                String valor = linha == null ? \"\" : linha.trim();\n"
                + "                if (valor.startsWith(\"Cenario:\")) {\n"
                + "                    if (cenarioAtual != null) {\n"
                + "                        mapa.put(cenarioAtual, new ArrayList<>(steps));\n"
                + "                        steps.clear();\n"
                + "                    }\n"
                + "                    cenarioAtual = valor.substring(8).trim();\n"
                + "                } else if (valor.startsWith(\"Dado \") || valor.startsWith(\"Quando \") || valor.startsWith(\"Entao \") || valor.startsWith(\"E \")) {\n"
                + "                    int idx = valor.indexOf(' ');\n"
                + "                    steps.add(valor.substring(idx + 1).trim());\n"
                + "                }\n"
                + "            }\n"
                + "            if (cenarioAtual != null) {\n"
                + "                mapa.put(cenarioAtual, new ArrayList<>(steps));\n"
                + "            }\n"
                + "        } catch (IOException ignored) { }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarHtmlReportGenerator() {
        return "package report;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.nio.file.Paths;\n"
                + "import java.time.LocalDateTime;\n"
                + "import java.time.format.DateTimeFormatter;\n"
                + "import java.util.List;\n"
                + "import java.util.stream.Collectors;\n\n"
                + "public final class HtmlReportGenerator {\n\n"
                + "    private HtmlReportGenerator() { }\n\n"
                + "    public static void gerar(ExecutionContext.ReportSnapshot snapshot, String urlBase) {\n"
                + "        try {\n"
                + "            Path target = Paths.get(\"target\");\n"
                + "            Files.createDirectories(target);\n"
                + "            Files.writeString(target.resolve(\"relatorio.html\"), montarHtml(snapshot, urlBase), StandardCharsets.UTF_8);\n"
                + "        } catch (IOException e) {\n"
                + "            throw new IllegalStateException(\"Erro ao escrever relatorio.html\", e);\n"
                + "        }\n"
                + "    }\n\n"
                + "    private static String montarHtml(ExecutionContext.ReportSnapshot snapshot, String urlBase) {\n"
                + "        int total = snapshot.total();\n"
                + "        int passou = snapshot.passou();\n"
                + "        int falhou = snapshot.falhou();\n"
                + "        int percentual = total == 0 ? 0 : (int) Math.round((passou * 100.0) / total);\n"
                + "        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\"));\n"
                + "        String duracao = snapshot.duracaoSegundos() + \"s\";\n"
                + "        List<StepEvidence> steps = snapshot.stepsPorCenario().values().stream().flatMap(List::stream).collect(Collectors.toList());\n"
                + "        String tabela = steps.stream().map(step -> \"<tr><td><b>\" + step.indice() + \"</b></td><td>\" + esc(step.descricao()) + \"</td><td><span class='badge \" + cssStatus(step.status()) + \"'>\" + simbolo(step.status()) + \" \" + esc(step.status()) + \"</span></td><td style='color:#DC2626;font-size:12px'>\" + esc(vazioOuTraco(step.detalheFalha())) + \"</td></tr>\").collect(Collectors.joining());\n"
                + "        StringBuilder evBuilder = new StringBuilder();\n"
                + "        int[] ctIdx = {1};\n"
                + "        snapshot.stepsPorCenario().forEach((nomeCenario, stepsCenario) -> {\n"
                + "            evBuilder.append(\"<div class='ct-header'><span class='ct-badge'>CT\").append(ctIdx[0]++).append(\"</span><span class='ct-titulo'>\").append(esc(nomeCenario)).append(\"</span></div>\");\n"
                + "            stepsCenario.forEach(step -> evBuilder.append(\"<div class='step-box'><div class='step-head'><div class='step-num'>\" + step.indice() + \"</div><div style='flex:1;font-size:13px;font-weight:500'>\" + esc(step.descricao()) + \"</div><span class='badge \" + cssStatus(step.status()) + \"'>\" + simbolo(step.status()) + \" \" + esc(step.status()) + \"</span></div><div class='step-body'>\" + (step.detalheFalha() == null || step.detalheFalha().isBlank() ? \"\" : \"<div class='erro'>\" + esc(step.detalheFalha()) + \"</div>\") + \"<img src='\" + esc(step.imagemRelativa()) + \"' alt='Step \" + step.indice() + \"'></div></div>\"));\n"
                + "        });\n"
                + "        String evidencias = evBuilder.toString();\n"
                + "        String labels = steps.stream().map(step -> \"'Step \" + step.indice() + \"'\").collect(Collectors.joining(\",\"));\n"
                + "        String dataPassou = steps.stream().map(step -> \"PASSOU\".equalsIgnoreCase(step.status()) ? \"1\" : \"0\").collect(Collectors.joining(\",\"));\n"
                + "        String dataFalhou = steps.stream().map(step -> \"FALHOU\".equalsIgnoreCase(step.status()) ? \"1\" : \"0\").collect(Collectors.joining(\",\"));\n"
                + "        return \"<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Relatório de Execução</title><script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script><style>*{box-sizing:border-box;margin:0;padding:0}body{font-family:'Segoe UI',Arial,sans-serif;background:#F1F5F9;color:#1E293B}.header{background:linear-gradient(135deg,#1F4E79 0%,#2563EB 100%);color:white;padding:32px 40px}.header h1{font-size:24px;font-weight:700;margin-bottom:6px}.header .meta{font-size:13px;opacity:.85}.header .meta span{margin-right:20px}.body{max-width:1100px;margin:0 auto;padding:32px 24px}.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:28px}.card{background:white;border-radius:12px;padding:20px 24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}.card-label{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.8px;color:#64748B;margin-bottom:6px}.card-value{font-size:32px;font-weight:700}.charts{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:24px}.chart-box{background:white;border-radius:12px;padding:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}.chart-box h2{font-size:15px;font-weight:600;margin-bottom:16px;color:#1E293B}.section{background:white;border-radius:12px;padding:24px;margin-bottom:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}.section h2{font-size:15px;font-weight:600;margin-bottom:16px;padding-bottom:10px;border-bottom:2px solid #E2E8F0}table{width:100%;border-collapse:collapse;font-size:13px}thead th{background:#F8FAFC;color:#475569;font-size:11px;text-transform:uppercase;padding:10px 14px;text-align:left;border-bottom:2px solid #E2E8F0}tbody td{padding:11px 14px;border-bottom:1px solid #F1F5F9;vertical-align:top}tbody tr:hover{background:#F8FAFC}.badge{display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:600}.passou{background:#DCFCE7;color:#166534}.falhou{background:#FEE2E2;color:#DC2626}.step-box{border:1px solid #E2E8F0;border-radius:8px;margin-bottom:12px;overflow:hidden}.step-head{display:flex;align-items:center;gap:10px;padding:12px 16px;background:#F8FAFC;border-bottom:1px solid #E2E8F0}.step-num{width:26px;height:26px;border-radius:50%;background:#1F4E79;color:white;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0}.step-body{padding:12px 16px}.erro{color:#DC2626;background:#FEF2F2;padding:8px;border-radius:6px;font-size:12px;margin-top:6px;font-family:monospace}.step-body img{max-width:100%;border:1px solid #E2E8F0;border-radius:6px;margin-top:8px}.bar{height:8px;background:#E2E8F0;border-radius:4px;overflow:hidden;margin-top:8px}.bar-fill{height:100%;border-radius:4px;background:linear-gradient(90deg,#16A34A,#22C55E)}.footer{text-align:center;padding:24px;color:#94A3B8;font-size:12px}.ct-header{display:flex;align-items:center;gap:10px;padding:12px 16px;background:linear-gradient(90deg,#1F4E79,#2563EB);border-radius:8px;margin:16px 0 8px;color:white}.ct-badge{background:white;color:#1F4E79;padding:3px 10px;border-radius:20px;font-size:12px;font-weight:700;flex-shrink:0}.ct-titulo{font-size:14px;font-weight:600}details.steps-accordion{width:100%}summary.steps-toggle{cursor:pointer;list-style:none;display:flex;align-items:center;font-size:15px;font-weight:600;color:#1E293B;padding:4px 0;gap:8px}summary.steps-toggle::-webkit-details-marker{display:none}summary.steps-toggle::after{content:'\\25BC';margin-left:auto;font-size:11px;color:#64748B}details.steps-accordion[open] summary.steps-toggle::after{content:'\\25B2'}.expand-dica{font-size:12px;font-weight:400;color:#64748B}</style></head><body><div class='header'><h1>&#128203; Relatório de Execução</h1><div class='meta'><span>&#128279; \" + esc(urlBase == null || urlBase.isBlank() ? \"URL não capturada\" : urlBase) + \"</span><span>&#128336; \" + dataHora + \"</span><span>&#9200; Duração: \" + duracao + \"</span></div></div><div class='body'><div class='cards'><div class='card'><div class='card-label'>Total</div><div class='card-value' style='color:#2563EB'>\" + total + \"</div></div><div class='card'><div class='card-label'>Passou</div><div class='card-value' style='color:#16A34A'>\" + passou + \"</div></div><div class='card'><div class='card-label'>Falhou</div><div class='card-value' style='color:#DC2626'>\" + falhou + \"</div></div><div class='card'><div class='card-label'>% Sucesso</div><div class='card-value' style='color:#16A34A'>\" + percentual + \"%</div><div class='bar'><div class='bar-fill' style='width:\" + percentual + \"%'></div></div></div></div><div class='charts'><div class='chart-box'><h2>&#128202; Resultado por Step</h2><canvas id='c1'></canvas></div><div class='chart-box'><h2>&#128200; Resumo</h2><canvas id='c2'></canvas></div></div><div class='section'><details class='steps-accordion'><summary class='steps-toggle'>&#128203; Tabela de Steps <span class='expand-dica'>— clique para expandir</span></summary><div style='overflow-x:auto;margin-top:12px'><table><thead><tr><th>#</th><th>Descrição</th><th>Status</th><th>Detalhe da falha</th></tr></thead><tbody>\" + tabela + \"</tbody></table></div></details></div><div class='section'><h2>&#128247; Evidências</h2>\" + evidencias + \"</div><div class='footer'>Gerado pelo Qorbit v2.0</div></div><script>new Chart(document.getElementById('c1').getContext('2d'),{type:'bar',data:{labels:[\" + labels + \"],datasets:[{label:'Passou',data:[\" + dataPassou + \"],backgroundColor:'#22C55E'},{label:'Falhou',data:[\" + dataFalhou + \"],backgroundColor:'#EF4444'}]},options:{responsive:true,scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{position:'top'}}}});new Chart(document.getElementById('c2').getContext('2d'),{type:'bar',data:{labels:['Passou','Falhou'],datasets:[{label:'Steps',data:[\" + passou + \",\" + falhou + \"],backgroundColor:['#22C55E','#EF4444']}]},options:{responsive:true,indexAxis:'y',scales:{x:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{display:false}}}});</script></body></html>\";\n"
                + "    }\n\n"
                + "    private static String cssStatus(String status) {\n"
                + "        return \"FALHOU\".equalsIgnoreCase(status) ? \"falhou\" : \"passou\";\n"
                + "    }\n\n"
                + "    private static String simbolo(String status) {\n"
                + "        return \"FALHOU\".equalsIgnoreCase(status) ? \"&#10007;\" : \"&#10003;\";\n"
                + "    }\n\n"
                + "    private static String vazioOuTraco(String valor) {\n"
                + "        return valor == null || valor.isBlank() ? \"—\" : valor;\n"
                + "    }\n\n"
                + "    private static String esc(String valor) {\n"
                + "        if (valor == null) return \"\";\n"
                + "        return valor.replace(\"&\", \"&amp;\").replace(\"<\", \"&lt;\").replace(\">\", \"&gt;\").replace(\"\\\"\", \"&quot;\");\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarPomXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.qorbit.engine</groupId>\n"
                + "    <artifactId>qorbit-tests-export</artifactId>\n"
                + "    <version>2.0.0</version>\n"
                + "    <properties>\n"
                + "        <maven.compiler.source>17</maven.compiler.source>\n"
                + "        <maven.compiler.target>17</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "        <selenium.version>4.18.1</selenium.version>\n"
                + "        <cucumber.version>7.15.0</cucumber.version>\n"
                + "        <maven.compiler.plugin.version>3.11.0</maven.compiler.plugin.version>\n"
                + "        <maven.surefire.plugin.version>3.2.5</maven.surefire.plugin.version>\n"
                + "    </properties>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>org.seleniumhq.selenium</groupId>\n"
                + "            <artifactId>selenium-java</artifactId>\n"
                + "            <version>${selenium.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>io.github.bonigarcia</groupId>\n"
                + "            <artifactId>webdrivermanager</artifactId>\n"
                + "            <version>5.7.0</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>io.cucumber</groupId>\n"
                + "            <artifactId>cucumber-java</artifactId>\n"
                + "            <version>${cucumber.version}</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>io.cucumber</groupId>\n"
                + "            <artifactId>cucumber-junit</artifactId>\n"
                + "            <version>${cucumber.version}</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "    <build>\n"
                + "        <plugins>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                <version>${maven.compiler.plugin.version}</version>\n"
                + "                <configuration>\n"
                + "                    <release>17</release>\n"
                + "                    <encoding>UTF-8</encoding>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-surefire-plugin</artifactId>\n"
                + "                <version>${maven.surefire.plugin.version}</version>\n"
                + "                <configuration>\n"
                + "                    <includes>\n"
                + "                        <include>**/Test*.java</include>\n"
                + "                        <include>**/*Test.java</include>\n"
                + "                        <include>**/*Runner.java</include>\n"
                + "                    </includes>\n"
                + "                    <systemPropertyVariables>\n"
                + "                        <file.encoding>UTF-8</file.encoding>\n"
                + "                    </systemPropertyVariables>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "        </plugins>\n"
                + "    </build>\n"
                + "</project>\n";
    }

    private String gerarHealingProperties() {
        return """
# ============================================================
# Self-Healing Testing — Configuração opcional
# ============================================================
# O Self-Healing funciona SEM IA usando estratégias locais:
#   1. Tenta pelo texto visível do elemento
#   2. Tenta pelo aria-label
#   3. Tenta pelo href parcial
#
# Para activar a IA (nível extra de resiliência):
#   1. Mude healing.ai.habilitado para true
#   2. Preencha o endpoint e modelo da sua IA
#   3. Se a IA não precisar de chave, deixe healing.ai.api-key em branco
#      e mude healing.ai.auth-tipo para none
#
# ONDE ACTUALIZAR A CHAVE:
#   Ficheiro: src/test/resources/healing.properties
#   Campo:    healing.ai.api-key=SUA_CHAVE_AQUI
#
# Exemplo com Groq (gratuito — https://console.groq.com):
#   healing.ai.habilitado=true
#   healing.ai.endpoint=https://api.groq.com/openai/v1/chat/completions
#   healing.ai.modelo=llama-3.3-70b-versatile
#   healing.ai.auth-tipo=bearer
#   healing.ai.api-key=gsk_xxxx
#
# Exemplo com IA interna sem autenticação:
#   healing.ai.habilitado=true
#   healing.ai.endpoint=https://minha-ia-interna.com/v1/chat/completions
#   healing.ai.modelo=meu-modelo
#   healing.ai.auth-tipo=none
#   healing.ai.api-key=
# ============================================================

healing.ai.habilitado=false
healing.ai.endpoint=https://api.groq.com/openai/v1/chat/completions
healing.ai.modelo=llama-3.3-70b-versatile
healing.ai.auth-tipo=bearer
healing.ai.api-key=
""";
    }

    private String gerarHealingService() {
        // Lê o template do HealingService do ficheiro de recursos
        // Evita escaping complexo de strings Java dentro de strings Java
        try (java.io.InputStream is = getClass().getClassLoader()
                .getResourceAsStream("HealingServiceTemplate.java")) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[Qorbit] Erro ao ler HealingServiceTemplate.java: " + e.getMessage());
        }
        return "// HealingService nao disponivel\n";
    }

    private String gerarReadme() {
        return "# Qorbit - Projeto Exportado\n\n"
                + "## Como executar\n\n"
                + "Este projeto exportado **nao e um backend Spring Boot**.\n\n"
                + "Use um destes comandos dentro da pasta `qorbit-tests-export`:\n\n"
                + "```bash\n"
                + "mvn test\n"
                + "```\n\n"
                + "No Windows, voce tambem pode executar:\n\n"
                + "```bash\n"
                + "executar-testes.bat\n"
                + "```\n\n"
                + "No Linux/Mac:\n\n"
                + "```bash\n"
                + "chmod +x executar-testes.sh\n"
                + "./executar-testes.sh\n"
                + "```\n\n"
                + "## Importante\n\n"
                + "Nao use `mvn spring-boot:run` neste projeto exportado, porque ele nao possui aplicacao Spring Boot.\n\n"
                + "## Melhorias incluidas nesta versao\n"
                + "- imports obrigatorios adicionados nos Page Objects gerados\n"
                + "- configuracao do Maven reforcada para UTF-8 e execucao do runner\n"
                + "- script `.bat` e `.sh` para facilitar a execucao\n"
                + "- imports explicitos nas classes de steps\n"
                + "- nomes de metodos consistentes entre Page Object e Steps\n"
                + "- seletor tecnico respeitando CSS, XPATH, ID, NAME e SHADOW_CSS\n"
                + "- suporte inicial a iframe no codigo exportado\n"
                + "- waits mais fortes para UI dinamica\n"
                + "- tratamento especial para select customizado, autocomplete e datepicker\n"
                + "- suporte a UTF-8 no projeto exportado\n"
                + "- tratamento de strings com aspas e quebras de linha\n"
                + "- captura de evidencias por step (PASS e FAIL)\n"
                + "- relatorio HTML estilo Qorbit em target/relatorio.html\n"
                + "- screenshots fisicas em target/evidencias\n";
    }

    private String gerarExecutarTestesBat() {
        return "@echo off\r\n"
                + "cd /d %~dp0\r\n"
                + "mvn test\r\n";
    }

    private String gerarExecutarTestesSh() {
        return "#!/usr/bin/env bash\n"
                + "set -e\n"
                + "cd \"$(dirname \"$0\")\"\n"
                + "mvn test\n";
    }

    private String safe(String valor) {
        return valor == null ? "" : valor;
    }

    private record LocatorSpec(String tipo, String valor) { }
    private record ElementContext(String framePath, String componentType, String shadowDom) { }

    private static class PageObjectSpec {
        private final String className;
        private final String rawPageName;
        private final List<ElementSpec> elementos = new ArrayList<>();
        private final Map<String, String> logicalToMethod = new LinkedHashMap<>();

        private PageObjectSpec(String className, String rawPageName) {
            this.className = className;
            this.rawPageName = rawPageName;
        }
    }

    private record ElementSpec(Elemento source, String methodName, LocatorSpec locator, ElementContext context) { }
    private record ResolvedStep(String pageClassName, String methodName, String componentType) { }
}
