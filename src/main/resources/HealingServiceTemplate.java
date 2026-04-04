package healing;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

/**
 * Self-Healing Testing — Qorbit Export
 *
 * Quando um selector falha, tenta recuperar o elemento automaticamente
 * usando estratégias locais e, opcionalmente, IA.
 *
 * COMO ACTUALIZAR A CHAVE DE API:
 *   1. Abra: src/test/resources/healing.properties
 *   2. Actualize: healing.ai.api-key=SUA_NOVA_CHAVE
 *   3. Guarde e re-execute: mvn test
 */
public class HealingService {

    private final WebDriver driver;
    private final int timeoutSegundos;
    private final Properties props;

    public HealingService(WebDriver driver, int timeoutSegundos) {
        this.driver = driver;
        this.timeoutSegundos = timeoutSegundos;
        this.props = carregarPropriedades();
    }

    /**
     * Tenta recuperar um elemento com selector falho.
     * Ordem de tentativas:
     *   1. Texto visível
     *   2. aria-label
     *   3. href parcial
     *   4. IA (se configurada em healing.properties)
     */
    public WebElement tentar(String seletorOriginal, String tipoSeletor,
                              String textoElemento, String ariaLabel, String href) {
        System.out.println("[Healing] Selector falhou: " + seletorOriginal);

        // Estratégia 1 — texto visível
        if (textoElemento != null && !textoElemento.isBlank()) {
            String txt = textoElemento.trim();
            WebElement el = tentarPorXPath("//*[normalize-space(text())='" + txt + "']");
            if (el != null) { log("texto exato"); return el; }
            String parcial = txt.length() > 20 ? txt.substring(0, 20) : txt;
            el = tentarPorXPath("//*[contains(normalize-space(text()),'" + parcial + "')]");
            if (el != null) { log("texto parcial"); return el; }
        }

        // Estratégia 2 — aria-label
        if (ariaLabel != null && !ariaLabel.isBlank()) {
            WebElement el = tentarPorCSS("[aria-label='" + ariaLabel + "']");
            if (el != null) { log("aria-label"); return el; }
        }

        // Estratégia 3 — href parcial
        if (href != null && !href.isBlank() && href.length() > 3) {
            String slug = href.contains("/")
                ? href.substring(href.lastIndexOf("/") + 1).split("\\?")[0]
                : href;
            if (slug.length() > 3) {
                String hrefParcial = slug.length() > 30 ? slug.substring(0, 30) : slug;
                WebElement el = tentarPorCSS("a[href*='" + hrefParcial + "']");
                if (el != null) { log("href parcial"); return el; }
            }
        }

        // Estratégia 4 — IA (opcional)
        if (Boolean.parseBoolean(props.getProperty("healing.ai.habilitado", "false"))) {
            WebElement el = tentarViaIA(seletorOriginal, tipoSeletor);
            if (el != null) { log("IA"); return el; }
        }

        System.out.println("[Healing] Nao foi possivel recuperar: " + seletorOriginal);
        return null;
    }

    private void log(String estrategia) {
        System.out.println("[Healing] Recuperado via: " + estrategia);
    }

    private WebElement tentarPorCSS(String css) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
        } catch (Exception e) { return null; }
    }

    private WebElement tentarPorXPath(String xpath) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
        } catch (Exception e) { return null; }
    }

    private WebElement tentarViaIA(String seletorOriginal, String tipoSeletor) {
        try {
            String endpoint = props.getProperty("healing.ai.endpoint", "");
            String modelo   = props.getProperty("healing.ai.modelo", "");
            String apiKey   = props.getProperty("healing.ai.api-key", "");
            String authTipo = props.getProperty("healing.ai.auth-tipo", "bearer");
            if (endpoint.isBlank() || modelo.isBlank()) return null;

            String html = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.body.innerHTML.substring(0, 4000)");

            // Monta JSON manualmente para evitar dependência do Jackson
            String msgContent = "Selector CSS falhou: " + seletorOriginal
                    + ". URL: " + driver.getCurrentUrl()
                    + ". Sugere selector CSS alternativo. Responde APENAS com o selector CSS.";
            msgContent = msgContent.replace("\"", "'").replace("\n", " ");

            String jsonBody = "{\"model\":\"" + modelo + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"" + msgContent + "\"}],"
                    + "\"max_tokens\":100,\"temperature\":0.2}";

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if ("bearer".equalsIgnoreCase(authTipo) && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey);
            } else if ("api-key".equalsIgnoreCase(authTipo) && !apiKey.isBlank()) {
                req.header("x-api-key", apiKey);
            }

            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(req.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                String body = resp.body();
                // Extrai o conteúdo da resposta
                int idx = body.indexOf("\"content\":\"");
                if (idx < 0) idx = body.indexOf("\"text\":\"");
                if (idx >= 0) {
                    int start = body.indexOf("\"", idx + 10) + 1;
                    int end   = body.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        String novoSeletor = body.substring(start, end)
                                .replace("```", "").trim();
                        if (!novoSeletor.isBlank()) {
                            System.out.println("[Healing] IA sugeriu: " + novoSeletor);
                            return tentarPorCSS(novoSeletor);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Healing] Erro na chamada IA: " + e.getMessage());
        }
        return null;
    }

    private Properties carregarPropriedades() {
        Properties p = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("healing.properties")) {
            if (is != null) p.load(is);
        } catch (Exception ignored) { }
        return p;
    }
}
