package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
public class ClickExecutionStrategy implements ExecutionStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.CLICK;
    }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            throw new IllegalArgumentException("Elemento não encontrado para clique");
        }

        esconderWidgetsFlutuantesSeNaoForTesteDeChat(driver, step);
        aguardarTelaLivre(driver);
        scrollCentralizado(driver, element);

        // Tentativa 1: clique normal com espera de clicável
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(element));
            element.click();
            posClique(driver, step);
            return;
        } catch (ElementClickInterceptedException intercepted) {
            tentarLiberarTela(driver);
        } catch (Exception ignored) { }

        // Tentativa 2: tenta de novo após liberar overlay
        try {
            scrollCentralizado(driver, element);
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(element));
            element.click();
            posClique(driver, step);
            return;
        } catch (Exception ignored) { }

        // Tentativa 3: radio/checkbox customizado — label associado via for=id
        try {
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                WebElement label = driver.findElement(By.cssSelector("label[for='" + cssEscape(id) + "']"));
                scrollCentralizado(driver, label);
                label.click();
                posClique(driver, step);
                return;
            }
        } catch (Exception ignored) { }

        // Para datepicker/componente sensível, evita fallback agressivo em pai/irmão
        boolean componenteSensivel = pareceComponenteSensivel(element, step);

        // Tentativa 4: clique no pai imediato
        if (!componenteSensivel) {
            try {
                WebElement parent = element.findElement(By.xpath("./.."));
                scrollCentralizado(driver, parent);
                parent.click();
                posClique(driver, step);
                return;
            } catch (Exception ignored) { }
        }

        // Tentativa 5: clique em irmão visual
        if (!componenteSensivel) {
            try {
                WebElement sibling = element.findElement(
                        By.xpath("./following-sibling::span | ./following-sibling::div | " +
                                "./preceding-sibling::span | ./preceding-sibling::div"));
                scrollCentralizado(driver, sibling);
                sibling.click();
                posClique(driver, step);
                return;
            } catch (Exception ignored) { }
        }

        // Tentativa 6: JavaScript click
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            posClique(driver, step);
            return;
        } catch (Exception ignored) { }

        throw new IllegalStateException(
                "Falha ao clicar no elemento '" +
                        (step.getNomeLogicoElemento() != null ? step.getNomeLogicoElemento() : "?") +
                        "' após múltiplas tentativas"
        );
    }

    private boolean isStepDeChat(StepTeste step) {
        if (step == null) return false;

        String nome = step.getNomeLogicoElemento();
        String valor = step.getValor();
        String base = ((nome == null ? "" : nome) + " " + (valor == null ? "" : valor))
                .toLowerCase(Locale.ROOT);

        return base.contains("chat")
                || base.contains("whatsapp")
                || base.contains("atendimento")
                || base.contains("contato")
                || base.contains("canal");
    }

    private void esconderWidgetsFlutuantesSeNaoForTesteDeChat(WebDriver driver, StepTeste step) {
        if (isStepDeChat(step)) return;

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "Array.from(document.querySelectorAll('button, a, div, span, iframe')).forEach(function(el) {" +
                    "  try {" +
                    "    const txt = (el.innerText || el.textContent || '').toLowerCase();" +
                    "    const aria = (el.getAttribute('aria-label') || '').toLowerCase();" +
                    "    const cls = (el.getAttribute('class') || '').toLowerCase();" +
                    "    if (" +
                    "      txt.includes('whatsapp') || " +
                    "      txt.includes('canais de contato') || " +
                    "      txt.includes('fale sobre seguros') || " +
                    "      txt.includes('central de atendimento') || " +
                    "      aria.includes('chat') || " +
                    "      cls.includes('chat') || " +
                    "      cls.includes('whatsapp')" +
                    "    ) {" +
                    "      el.style.setProperty('display', 'none', 'important');" +
                    "      el.style.setProperty('visibility', 'hidden', 'important');" +
                    "      el.style.setProperty('pointer-events', 'none', 'important');" +
                    "    }" +
                    "  } catch(e) {}" +
                    "});"
            );
        } catch (Exception ignored) { }
    }

    private boolean pareceComponenteSensivel(WebElement element, StepTeste step) {
        try {
            StringBuilder sb = new StringBuilder();

            if (step != null) {
                if (step.getNomeLogicoElemento() != null) sb.append(step.getNomeLogicoElemento()).append(" ");
                if (step.getValor() != null) sb.append(step.getValor()).append(" ");
            }

            sb.append(nullToEmpty(element.getAttribute("class"))).append(" ");
            sb.append(nullToEmpty(element.getAttribute("aria-label"))).append(" ");
            sb.append(nullToEmpty(element.getAttribute("role"))).append(" ");
            sb.append(nullToEmpty(element.getTagName())).append(" ");
            sb.append(nullToEmpty(element.getText()));

            String base = sb.toString().toLowerCase(Locale.ROOT);

            return base.contains("date")
                    || base.contains("calendar")
                    || base.contains("picker")
                    || base.contains("ano")
                    || base.contains("year")
                    || base.contains("mes")
                    || base.contains("month")
                    || base.contains("mat-calendar")
                    || base.contains("muipickers")
                    || base.contains("datepicker");
        } catch (Exception e) {
            return false;
        }
    }

    private void aguardarTelaLivre(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(d -> {
            try {
                List<WebElement> overlays = d.findElements(By.cssSelector(
                        ".MuiBackdrop-root, " +
                        ".MuiPopover-root, " +
                        ".MuiPickersPopper-root, " +
                        ".MuiDialog-root, " +
                        ".MuiModal-root, " +
                        ".loading, .spinner"
                ));

                for (WebElement overlay : overlays) {
                    try {
                        if (overlay.isDisplayed()) {
                            String clazz = overlay.getAttribute("class");
                            if (clazz != null &&
                                    (clazz.contains("MuiBackdrop-root")
                                            || clazz.contains("MuiPickersPopper-root")
                                            || clazz.contains("MuiPopover-root")
                                            || clazz.contains("MuiDialog-root")
                                            || clazz.contains("MuiModal-root"))) {
                                return false;
                            }
                        }
                    } catch (Exception ignored) { }
                }
                return true;
            } catch (Exception e) {
                return true;
            }
        });
    }

    private void tentarLiberarTela(WebDriver driver) {
        try {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        } catch (Exception ignored) { }

        try {
            ((JavascriptExecutor) driver).executeScript("document.body.click();");
        } catch (Exception ignored) { }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.invisibilityOfElementLocated(
                            By.cssSelector(
                                    ".MuiBackdrop-root, " +
                                            ".MuiPopover-root, " +
                                            ".MuiPickersPopper-root, " +
                                            ".MuiDialog-root, " +
                                            ".MuiModal-root"
                            )
                    ));
        } catch (Exception ignored) { }

        sleep(200);
    }

    private void posClique(WebDriver driver, StepTeste step) {
        sleep(150);

        // Não fecha popup residual quando o clique é de chat
        if (!isStepDeChat(step)) {
            tentarFecharPopupResidual(driver);
        }

        sleep(100);
    }

    private void tentarFecharPopupResidual(WebDriver driver) {
        try {
            List<WebElement> popups = driver.findElements(By.cssSelector(
                    ".MuiPopover-root, .MuiPickersPopper-root, .MuiDialog-root, .MuiModal-root"
            ));

            boolean algumAberto = false;
            for (WebElement popup : popups) {
                try {
                    if (popup.isDisplayed()) {
                        algumAberto = true;
                        break;
                    }
                } catch (Exception ignored) { }
            }

            if (algumAberto) {
                try {
                    driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private void scrollCentralizado(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
                    element
            );
            sleep(200);
        } catch (Exception ignored) { }
    }

    private String cssEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}