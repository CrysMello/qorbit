package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class TextInputExecutionStrategy implements ExecutionStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.TEXT_INPUT;
    }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            throw new IllegalArgumentException("Elemento não encontrado para preenchimento");
        }

        String value = step.getValor() != null ? step.getValor() : "";

        aguardarTelaLivre(driver);
        scrollCentralizado(driver, element);
        aguardarElementoVisivelEPronto(driver, element);

        // tentativa 1: fluxo padrão selenium
        try {
            focarElemento(driver, element);
            limparCampo(driver, element);
            element.sendKeys(value);
            dispararEventos(driver, element);

            if (valorAplicado(element, value)) {
                aguardarTelaLivre(driver);
                return;
            }
        } catch (Exception ignored) { }

        // tentativa 2: CTRL+A + DELETE + sendKeys
        try {
            focarElemento(driver, element);
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            element.sendKeys(Keys.DELETE);
            element.sendKeys(value);
            dispararEventos(driver, element);

            if (valorAplicado(element, value)) {
                aguardarTelaLivre(driver);
                return;
            }
        } catch (Exception ignored) { }

        // tentativa 3: JS com focus + value + eventos
        try {
            setValueViaJs(driver, element, value);

            if (valorAplicado(element, value)) {
                aguardarTelaLivre(driver);
                return;
            }
        } catch (Exception ignored) { }

        // tentativa 4: para inputs mascarados, digita caractere por caractere
        try {
            focarElemento(driver, element);
            limparCampo(driver, element);
            for (char c : value.toCharArray()) {
                element.sendKeys(String.valueOf(c));
                sleep(40);
            }
            dispararEventos(driver, element);

            if (valorAplicado(element, value)) {
                aguardarTelaLivre(driver);
                return;
            }
        } catch (Exception ignored) { }

        String current = readValue(element);
        throw new IllegalStateException(
                "Valor não aplicado ao input. Esperado=" + value + " actual=" + current
        );
    }

    private void aguardarTelaLivre(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(d -> {
            try {
                List<WebElement> overlays = d.findElements(org.openqa.selenium.By.cssSelector(
                        ".MuiBackdrop-root, " +
                        ".MuiPopover-root, " +
                        ".MuiPickersPopper-root, " +
                        ".MuiDialog-root, " +
                        ".MuiModal-root, " +
                        "[role='dialog'], " +
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

    private void scrollCentralizado(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
                    element
            );
            sleep(200);
        } catch (Exception ignored) { }
    }

    private void aguardarElementoVisivelEPronto(WebDriver driver, WebElement element) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(d -> {
            try {
                return element.isDisplayed() && element.isEnabled();
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void focarElemento(WebDriver driver, WebElement element) {
        try {
            element.click();
            return;
        } catch (Exception ignored) { }

        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].focus();", element);
        } catch (Exception ignored) { }
    }

    private void limparCampo(WebDriver driver, WebElement element) {
        try {
            element.clear();
        } catch (Exception ignored) { }

        try {
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            element.sendKeys(Keys.DELETE);
        } catch (Exception ignored) { }

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value='';" +
                    "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                    element
            );
        } catch (Exception ignored) { }
    }

    private void setValueViaJs(WebDriver driver, WebElement element, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].focus();" +
                "arguments[0].value = arguments[1];" +
                "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
                element, value
        );
    }

    private void dispararEventos(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
                    element
            );
        } catch (Exception ignored) { }
    }

    private boolean valorAplicado(WebElement element, String expected) {
        String current = readValue(element);
        if (current == null) return false;

        String actualNorm = normalizar(current);
        String expectedNorm = normalizar(expected);

        if (actualNorm.equals(expectedNorm)) return true;
        return actualNorm.contains(expectedNorm) || expectedNorm.contains(actualNorm);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String readValue(WebElement element) {
        try {
            String value = element.getAttribute("value");
            if (value != null) return value;
        } catch (Exception ignored) { }

        try {
            String text = element.getText();
            if (text != null) return text;
        } catch (Exception ignored) { }

        return null;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}