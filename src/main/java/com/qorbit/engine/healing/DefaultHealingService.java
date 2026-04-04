package com.qorbit.engine.healing;

import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.service.AIPluginService;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DefaultHealingService — Recuperação automática de elementos com seletor quebrado.
 *
 * Estratégias aplicadas em ordem de confiança:
 *   1. healed_selector — seletor já curado em execução anterior (máxima confiança)
 *   2. id             — pelo atributo id extraído do fingerprint ou seletor CSS original
 *   3. name           — pelo atributo name
 *   4. data-testid    — pelo atributo data-testid / data-qa / data-cy
 *   5. aria-label     — pelo atributo aria-label gravado no fingerprint
 *   6. placeholder    — pelo atributo placeholder
 *   7. label          — texto do label associado ao campo
 *   8. texto          — texto visível do elemento (para botões/links)
 *   9. IA             — via AIPluginService se habilitado (último recurso)
 *
 * O fingerprint é lido do campo {@code Elemento.descricao} no formato:
 *   key1=value1|key2=value2|...
 *
 * Chaves reconhecidas: id, name, data-testid, aria-label (ariaLabel), placeholder,
 *   label, texto, tag, healed_selector.
 */
@Service
public class DefaultHealingService implements HealingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultHealingService.class);

    @Autowired(required = false)
    private AIPluginService aiPluginService;

    // ── Fingerprint parsing ───────────────────────────────────────────────────

    /**
     * Extrai um mapa chave→valor do fingerprint armazenado em {@code descricao}.
     * Formato: "key1=value1|key2=value2|..."
     */
    Map<String, String> parsearFingerprint(String descricao) {
        Map<String, String> map = new LinkedHashMap<>();
        if (descricao == null || descricao.isBlank()) return map;
        for (String parte : descricao.split("\\|")) {
            int idx = parte.indexOf('=');
            if (idx > 0) {
                String chave = parte.substring(0, idx).trim();
                String valor = parte.substring(idx + 1).trim();
                if (!chave.isBlank() && !valor.isBlank()) {
                    map.put(chave, valor);
                }
            }
        }
        return map;
    }

    /**
     * Tenta extrair o atributo {@code id} do seletor CSS original.
     * Reconhece: "#meu-id", "[id='meu-id']", "[id=\"meu-id\"]".
     */
    String extrairIdDoSeletor(String seletor) {
        if (seletor == null) return null;
        // #id
        Matcher hash = Pattern.compile("^#([\\w-]+)").matcher(seletor.trim());
        if (hash.find()) return hash.group(1);
        // [id='...'] ou [id="..."]
        Matcher attr = Pattern.compile("\\[id=['\"]?([\\w-]+)['\"]?\\]").matcher(seletor);
        if (attr.find()) return attr.group(1);
        return null;
    }

    /**
     * Tenta extrair o atributo {@code name} do seletor CSS original.
     */
    String extrairNameDoSeletor(String seletor) {
        if (seletor == null) return null;
        Matcher m = Pattern.compile("\\[name=['\"]?([\\w-]+)['\"]?\\]").matcher(seletor);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Tenta extrair {@code data-testid} (ou data-qa / data-cy) do seletor.
     */
    String extrairTestIdDoSeletor(String seletor) {
        if (seletor == null) return null;
        Matcher m = Pattern.compile("\\[data-(?:testid|qa|cy)=['\"]?([\\w-]+)['\"]?\\]").matcher(seletor);
        return m.find() ? m.group(1) : null;
    }

    // ── Healing entry point ───────────────────────────────────────────────────

    @Override
    public HealingResult tentar(WebDriver driver, Elemento elemento, int timeout) {
        String nomeLogico = elemento.getNomeLogico();
        String descricao  = elemento.getDescricao();
        String seletor    = elemento.getSeletorTecnico();

        log.info("[healing] Iniciando self-healing para elemento='{}' seletor='{}'",
                nomeLogico, seletor);

        Map<String, String> fp = parsearFingerprint(descricao);

        // 1. healed_selector — curado em execução anterior
        HealingResult r = tentarHealedSelector(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        // 2. id
        r = tentarPorId(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        // 3. name
        r = tentarPorName(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        // 4. data-testid / data-qa / data-cy
        r = tentarPorTestId(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        // 5. aria-label
        r = tentarPorAriaLabel(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        // 6. placeholder
        r = tentarPorPlaceholder(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        // 7. label associado
        r = tentarPorLabel(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        // 8. texto visivel
        r = tentarPorTexto(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        // 9. IA (último recurso)
        r = tentarViaIA(driver, elemento, timeout);
        if (r != null) return log(r, nomeLogico);

        log.error("[healing] Todas as estratégias falharam para elemento='{}'", nomeLogico);
        return null;
    }

    // ── Estratégias individuais ───────────────────────────────────────────────

    private HealingResult tentarHealedSelector(WebDriver driver, Map<String, String> fp, int timeout) {
        String sel = fp.get("healed_selector");
        if (sel == null || sel.isBlank()) return null;
        WebElement el = porCSS(driver, sel, timeout);
        return el != null ? new HealingResult(el, "healed_selector", sel) : null;
    }

    private HealingResult tentarPorId(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String id = fp.getOrDefault("id", extrairIdDoSeletor(seletor));
        if (id == null || id.isBlank()) return null;
        WebElement el = porCSS(driver, "#" + id, timeout);
        if (el == null) el = porId(driver, id, timeout);
        return el != null ? new HealingResult(el, "id", "#" + id) : null;
    }

    private HealingResult tentarPorName(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String name = fp.getOrDefault("name", extrairNameDoSeletor(seletor));
        if (name == null || name.isBlank()) return null;
        WebElement el = porCSS(driver, "[name='" + name + "']", timeout);
        return el != null ? new HealingResult(el, "name", "[name='" + name + "']") : null;
    }

    private HealingResult tentarPorTestId(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String testId = fp.getOrDefault("data-testid", extrairTestIdDoSeletor(seletor));
        if (testId == null || testId.isBlank()) testId = fp.get("data-qa");
        if (testId == null || testId.isBlank()) testId = fp.get("data-cy");
        if (testId == null || testId.isBlank()) return null;
        String finalId = testId;
        for (String attr : new String[]{"data-testid", "data-qa", "data-cy"}) {
            WebElement el = porCSS(driver, "[" + attr + "='" + finalId + "']", timeout);
            if (el != null) return new HealingResult(el, attr, "[" + attr + "='" + finalId + "']");
        }
        return null;
    }

    private HealingResult tentarPorAriaLabel(WebDriver driver, Map<String, String> fp, int timeout) {
        String aria = fp.getOrDefault("ariaLabel", fp.get("aria-label"));
        if (aria == null || aria.isBlank()) return null;
        WebElement el = porCSS(driver, "[aria-label='" + aria + "']", timeout);
        return el != null ? new HealingResult(el, "aria-label", "[aria-label='" + aria + "']") : null;
    }

    private HealingResult tentarPorPlaceholder(WebDriver driver, Map<String, String> fp, int timeout) {
        String ph = fp.get("placeholder");
        if (ph == null || ph.isBlank()) return null;
        WebElement el = porCSS(driver, "[placeholder='" + ph + "']", timeout);
        return el != null ? new HealingResult(el, "placeholder", "[placeholder='" + ph + "']") : null;
    }

    private HealingResult tentarPorLabel(WebDriver driver, Map<String, String> fp, int timeout) {
        String label = fp.get("label");
        if (label == null || label.isBlank()) return null;
        // Tenta via for= usando texto do label
        WebElement el = porXPath(driver,
                "//label[normalize-space(text())='" + label + "']/following-sibling::*[1] | " +
                "//label[normalize-space(text())='" + label + "']/..//*[self::input or self::select or self::textarea][1]",
                timeout);
        return el != null ? new HealingResult(el, "label", "label='" + label + "'") : null;
    }

    private HealingResult tentarPorTexto(WebDriver driver, Map<String, String> fp, int timeout) {
        String texto = fp.get("texto");
        if (texto == null || texto.isBlank() || texto.length() > 80) return null;
        // Texto exato
        WebElement el = porXPath(driver, "//*[normalize-space(text())='" + texto + "']", timeout);
        if (el != null) return new HealingResult(el, "texto-exato", "text='" + texto + "'");
        // Texto parcial (primeiros 30 chars)
        String parcial = texto.length() > 30 ? texto.substring(0, 30) : texto;
        el = porXPath(driver, "//*[contains(normalize-space(text()),'" + parcial + "')]", timeout);
        return el != null ? new HealingResult(el, "texto-parcial", "text~='" + parcial + "'") : null;
    }

    private HealingResult tentarViaIA(WebDriver driver, Elemento elemento, int timeout) {
        if (aiPluginService == null
                || !aiPluginService.isHabilitado()
                || !aiPluginService.isAutoHealingHabilitado()) {
            return null;
        }
        try {
            WebElement healed = aiPluginService.tentarAutoHealing(
                    driver, elemento.getSeletorTecnico(), elemento.getTipoSeletor(), timeout);
            if (healed == null) return null;
            // Extrai seletor do elemento curado via outerHTML
            String novoSeletor = extrairSeletorDoOuterHtml(healed);
            return new HealingResult(healed, "ia", novoSeletor);
        } catch (Exception e) {
            log.debug("[healing] IA falhou: {}", e.getMessage());
            return null;
        }
    }

    // ── Utilidades de localização ─────────────────────────────────────────────

    private WebElement porCSS(WebDriver driver, String css, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(Math.min(timeout, 4)))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
        } catch (Exception e) { return null; }
    }

    private WebElement porId(WebDriver driver, String id, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(Math.min(timeout, 4)))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
        } catch (Exception e) { return null; }
    }

    private WebElement porXPath(WebDriver driver, String xpath, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(Math.min(timeout, 4)))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
        } catch (Exception e) { return null; }
    }

    private String extrairSeletorDoOuterHtml(WebElement element) {
        try {
            String html = element.getAttribute("outerHTML");
            if (html == null) return "";
            // Tenta extrair id
            Matcher mId = Pattern.compile("\\sid=['\"]([\\w-]+)['\"]").matcher(html);
            if (mId.find()) return "#" + mId.group(1);
            // Tenta data-testid
            Matcher mTestId = Pattern.compile("data-testid=['\"]([\\w-]+)['\"]").matcher(html);
            if (mTestId.find()) return "[data-testid='" + mTestId.group(1) + "']";
            // Tenta name
            Matcher mName = Pattern.compile("\\sname=['\"]([\\w-]+)['\"]").matcher(html);
            if (mName.find()) return "[name='" + mName.group(1) + "']";
            return "";
        } catch (Exception e) { return ""; }
    }

    private HealingResult log(HealingResult result, String nomeLogico) {
        log.info("[healing] Elemento='{}' recuperado via estrategia='{}' novoSeletor='{}'",
                nomeLogico, result.getEstrategia(), result.getNovoSeletor());
        return result;
    }
}
