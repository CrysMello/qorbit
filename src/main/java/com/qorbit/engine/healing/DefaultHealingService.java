package com.qorbit.engine.healing;

import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.service.AIPluginService;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
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
 *   1. healed_selector — seletor já curado em execução anterior
 *   2. id             — pelo atributo id estável
 *   3. value          — especialmente útil para radio / checkbox
 *   4. name           — pelo atributo name estável
 *   5. data-testid    — data-testid / data-qa / data-cy
 *   6. aria-label     — pelo aria-label gravado
 *   7. placeholder    — pelo placeholder
 *   8. label          — texto do label associado
 *   9. texto          — texto visível do elemento
 *  10. IA             — último recurso
 */
@Service
public class DefaultHealingService implements HealingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultHealingService.class);

    @Autowired(required = false)
    private AIPluginService aiPluginService;

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

    String extrairIdDoSeletor(String seletor) {
        if (seletor == null) return null;

        Matcher hash = Pattern.compile("^#([\\w-]+)").matcher(seletor.trim());
        if (hash.find()) return hash.group(1);

        Matcher attr = Pattern.compile("\\[id=['\"]?([\\w-]+)['\"]?\\]").matcher(seletor);
        if (attr.find()) return attr.group(1);

        return null;
    }

    String extrairNameDoSeletor(String seletor) {
        if (seletor == null) return null;
        Matcher m = Pattern.compile("\\[name=['\"]?([^'\"\\]]+)['\"]?\\]").matcher(seletor);
        return m.find() ? m.group(1) : null;
    }

    String extrairTestIdDoSeletor(String seletor) {
        if (seletor == null) return null;
        Matcher m = Pattern.compile("\\[data-(?:testid|qa|cy)=['\"]?([^'\"\\]]+)['\"]?\\]").matcher(seletor);
        return m.find() ? m.group(1) : null;
    }

    String extrairValueDoSeletor(String seletor) {
        if (seletor == null) return null;
        Matcher m = Pattern.compile("\\[value=['\"]?([^'\"\\]]+)['\"]?\\]").matcher(seletor);
        return m.find() ? m.group(1) : null;
    }

    String extrairTypeDoSeletor(String seletor) {
        if (seletor == null) return null;
        Matcher m = Pattern.compile("\\[type=['\"]?([^'\"\\]]+)['\"]?\\]").matcher(seletor);
        return m.find() ? m.group(1) : null;
    }

    boolean pareceDinamico(String valor) {
        if (valor == null || valor.isBlank()) return false;
        String v = valor.toLowerCase();
        return v.matches("^mui-\\d+$")
                || v.matches("^jss\\d+$")
                || v.matches("^css-[a-z0-9]+$")
                || v.matches(".*(^|[-_])mui-\\d+($|[-_]).*");
    }

    @Override
    public HealingResult tentar(WebDriver driver, Elemento elemento, int timeout) {
        String nomeLogico = elemento.getNomeLogico();
        String descricao = elemento.getDescricao();
        String seletor = elemento.getSeletorTecnico();

        log.info("[healing] Iniciando self-healing para elemento='{}' seletor='{}'",
                nomeLogico, seletor);

        Map<String, String> fp = parsearFingerprint(descricao);

        HealingResult r = tentarHealedSelector(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorId(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorValue(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorName(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorTestId(driver, fp, seletor, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorAriaLabel(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorPlaceholder(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorLabel(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarPorTexto(driver, fp, timeout);
        if (r != null) return log(r, nomeLogico);

        r = tentarViaIA(driver, elemento, timeout);
        if (r != null) return log(r, nomeLogico);

        log.error("[healing] Todas as estratégias falharam para elemento='{}'", nomeLogico);
        return null;
    }

    private HealingResult tentarHealedSelector(WebDriver driver, Map<String, String> fp, int timeout) {
        String sel = fp.get("healed_selector");
        if (sel == null || sel.isBlank()) return null;

        WebElement el = porCSS(driver, sel, timeout);
        return el != null ? new HealingResult(el, "healed_selector", sel) : null;
    }

    private HealingResult tentarPorId(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String id = fp.getOrDefault("id", extrairIdDoSeletor(seletor));
        if (id == null || id.isBlank() || pareceDinamico(id)) return null;

        WebElement el = porCSS(driver, "#" + id, timeout);
        if (el == null) el = porId(driver, id, timeout);

        return el != null ? new HealingResult(el, "id", "#" + id) : null;
    }

    private HealingResult tentarPorValue(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String value = fp.getOrDefault("value", extrairValueDoSeletor(seletor));
        if (value == null || value.isBlank()) return null;

        String type = fp.getOrDefault("type", extrairTypeDoSeletor(seletor));

        String css;
        if (type != null && !type.isBlank()) {
            css = "input[type='" + escaparCssLiteral(type) + "'][value='" + escaparCssLiteral(value) + "']";
        } else {
            css = "input[value='" + escaparCssLiteral(value) + "']";
        }

        WebElement el = porCSS(driver, css, timeout);
        if (el != null) {
            return new HealingResult(el, "value", css);
        }

        if ("radio".equalsIgnoreCase(type) || "checkbox".equalsIgnoreCase(type)) {
            String cssFallback = "[value='" + escaparCssLiteral(value) + "']";
            el = porCSS(driver, cssFallback, timeout);
            if (el != null) {
                return new HealingResult(el, "value-fallback", cssFallback);
            }
        }

        return null;
    }

    private HealingResult tentarPorName(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String name = fp.getOrDefault("name", extrairNameDoSeletor(seletor));
        if (name == null || name.isBlank() || pareceDinamico(name)) return null;

        WebElement el = porCSS(driver, "[name='" + escaparCssLiteral(name) + "']", timeout);
        return el != null ? new HealingResult(el, "name", "[name='" + escaparCssLiteral(name) + "']") : null;
    }

    private HealingResult tentarPorTestId(WebDriver driver, Map<String, String> fp, String seletor, int timeout) {
        String testId = fp.getOrDefault("data-testid", extrairTestIdDoSeletor(seletor));
        if (testId == null || testId.isBlank()) testId = fp.get("data-qa");
        if (testId == null || testId.isBlank()) testId = fp.get("data-cy");
        if (testId == null || testId.isBlank()) return null;

        for (String attr : new String[]{"data-testid", "data-qa", "data-cy"}) {
            WebElement el = porCSS(driver, "[" + attr + "='" + escaparCssLiteral(testId) + "']", timeout);
            if (el != null) {
                return new HealingResult(el, attr, "[" + attr + "='" + escaparCssLiteral(testId) + "']");
            }
        }
        return null;
    }

    private HealingResult tentarPorAriaLabel(WebDriver driver, Map<String, String> fp, int timeout) {
        String aria = fp.getOrDefault("ariaLabel", fp.get("aria-label"));
        if (aria == null || aria.isBlank()) return null;

        WebElement el = porCSS(driver, "[aria-label='" + escaparCssLiteral(aria) + "']", timeout);
        return el != null ? new HealingResult(el, "aria-label", "[aria-label='" + escaparCssLiteral(aria) + "']") : null;
    }

    private HealingResult tentarPorPlaceholder(WebDriver driver, Map<String, String> fp, int timeout) {
        String ph = fp.get("placeholder");
        if (ph == null || ph.isBlank()) return null;

        WebElement el = porCSS(driver, "[placeholder='" + escaparCssLiteral(ph) + "']", timeout);
        return el != null ? new HealingResult(el, "placeholder", "[placeholder='" + escaparCssLiteral(ph) + "']") : null;
    }

    private HealingResult tentarPorLabel(WebDriver driver, Map<String, String> fp, int timeout) {
        String label = fp.get("label");
        if (label == null || label.isBlank()) return null;

        String x = "//label[normalize-space(text())=" + xpathLiteral(label) + "]/following-sibling::*[1] | " +
                   "//label[normalize-space(text())=" + xpathLiteral(label) + "]/..//*[self::input or self::select or self::textarea][1]";

        WebElement el = porXPath(driver, x, timeout);
        return el != null ? new HealingResult(el, "label", "label='" + label + "'") : null;
    }

    private HealingResult tentarPorTexto(WebDriver driver, Map<String, String> fp, int timeout) {
        String texto = fp.get("texto");
        if (texto == null || texto.isBlank() || texto.length() > 80) return null;

        WebElement el = porXPath(driver, "//*[normalize-space(text())=" + xpathLiteral(texto) + "]", timeout);
        if (el != null) {
            return new HealingResult(el, "texto-exato", "text='" + texto + "'");
        }

        String parcial = texto.length() > 30 ? texto.substring(0, 30) : texto;
        el = porXPath(driver, "//*[contains(normalize-space(text())," + xpathLiteral(parcial) + ")]", timeout);
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

            String novoSeletor = extrairSeletorDoOuterHtml(healed);
            return new HealingResult(healed, "ia", novoSeletor);
        } catch (Exception e) {
            log.debug("[healing] IA falhou: {}", e.getMessage());
            return null;
        }
    }

    private WebElement porCSS(WebDriver driver, String css, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(Math.min(timeout, 4)))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
        } catch (Exception e) {
            return null;
        }
    }

    private WebElement porId(WebDriver driver, String id, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(Math.min(timeout, 4)))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
        } catch (Exception e) {
            return null;
        }
    }

    private WebElement porXPath(WebDriver driver, String xpath, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(Math.min(timeout, 4)))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
        } catch (Exception e) {
            return null;
        }
    }

    private String extrairSeletorDoOuterHtml(WebElement element) {
        try {
            String html = element.getAttribute("outerHTML");
            if (html == null) return "";

            Matcher mId = Pattern.compile("\\sid=['\"]([\\w-]+)['\"]").matcher(html);
            if (mId.find() && !pareceDinamico(mId.group(1))) {
                return "#" + mId.group(1);
            }

            Matcher mTestId = Pattern.compile("data-testid=['\"]([\\w-]+)['\"]").matcher(html);
            if (mTestId.find()) {
                return "[data-testid='" + mTestId.group(1) + "']";
            }

            Matcher mType = Pattern.compile("\\stype=['\"]([^'\"]+)['\"]").matcher(html);
            Matcher mValue = Pattern.compile("\\svalue=['\"]([^'\"]+)['\"]").matcher(html);

            String type = mType.find() ? mType.group(1) : null;
            String value = mValue.find() ? mValue.group(1) : null;

            if (value != null && !value.isBlank() && type != null && !type.isBlank()) {
                if ("radio".equalsIgnoreCase(type) || "checkbox".equalsIgnoreCase(type)) {
                    return "input[type='" + type + "'][value='" + value + "']";
                }
            }

            Matcher mName = Pattern.compile("\\sname=['\"]([\\w-]+)['\"]").matcher(html);
            if (mName.find() && !pareceDinamico(mName.group(1))) {
                return "[name='" + mName.group(1) + "']";
            }

            Matcher mAria = Pattern.compile("aria-label=['\"]([^'\"]+)['\"]").matcher(html);
            if (mAria.find()) {
                return "[aria-label='" + mAria.group(1) + "']";
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String escaparCssLiteral(String valor) {
        return valor == null ? "" : valor.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String xpathLiteral(String valor) {
        if (valor == null) return "''";
        if (!valor.contains("'")) return "'" + valor + "'";
        if (!valor.contains("\"")) return "\"" + valor + "\"";

        StringBuilder sb = new StringBuilder("concat(");
        char[] chars = valor.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            String part;
            if (chars[i] == '\'') {
                part = "\"'\"";
            } else if (chars[i] == '"') {
                part = "'\"'";
            } else {
                int j = i;
                StringBuilder normal = new StringBuilder();
                while (j < chars.length && chars[j] != '\'' && chars[j] != '"') {
                    normal.append(chars[j]);
                    j++;
                }
                part = "'" + normal + "'";
                i = j - 1;
            }
            if (sb.length() > 7) sb.append(",");
            sb.append(part);
        }
        sb.append(")");
        return sb.toString();
    }

    private HealingResult log(HealingResult result, String nomeLogico) {
        log.info("[healing] Elemento='{}' recuperado via estrategia='{}' novoSeletor='{}'",
                nomeLogico, result.getEstrategia(), result.getNovoSeletor());
        return result;
    }
}