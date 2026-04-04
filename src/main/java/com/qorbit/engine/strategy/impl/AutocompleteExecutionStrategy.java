package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * AutocompleteExecutionStrategy — estratégia para campos com sugestões dinâmicas.
 *
 * Fluxo:
 *   1. Digita o valor no campo
 *   2. Aguarda lista de sugestões aparecer
 *   3. Tenta seleccionar a opção mais próxima
 *   4. Fallback: aceita o valor digitado com ENTER
 *   5. Valida se o valor foi aplicado
 */
@Component
public class AutocompleteExecutionStrategy implements ExecutionStrategy {

    @Override
    public StrategyType type() { return StrategyType.AUTOCOMPLETE; }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) throw new IllegalArgumentException("Elemento não encontrado para autocomplete");

        String value = step.getValor() != null ? step.getValor() : "";
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        // Passo 1 — limpa e digita o valor
        try { element.clear(); } catch (Exception ignored) { }
        element.sendKeys(value);

        // Passo 2 — aguarda lista de sugestões aparecer
        boolean sugestoesVisiveis = aguardarSugestoes(driver, wait);

        if (sugestoesVisiveis) {
            // Passo 3 — tenta seleccionar a opção mais próxima do valor
            if (trySelectSuggestion(driver, wait, value)) {
                return;
            }
        }

        // Passo 4 — fallback: aceita com ENTER ou TAB
        try {
            element.sendKeys(Keys.ENTER);
            if (validate(element, value)) return;
            element.sendKeys(Keys.TAB);
            if (validate(element, value)) return;
        } catch (Exception ignored) { }

        // Passo 5 — se nada funcionou mas o campo tem o valor, aceita
        if (validate(element, value)) return;

        throw new IllegalStateException("Falha ao aplicar valor no autocomplete: " + value);
    }

    private boolean aguardarSugestoes(WebDriver driver, WebDriverWait wait) {
        // Lista de selectores comuns para dropdowns de autocomplete
        List<String> seletoresSugestoes = List.of(
            "[role='listbox']",
            "[role='option']",
            ".autocomplete-suggestions",
            ".typeahead-suggestions",
            ".ui-autocomplete",
            ".pac-container",           // Google Places
            "[aria-label*='suggestion']",
            ".autosuggest-container",
            "datalist option",
            ".dropdown-menu li",
            "[role='combobox'] + ul"
        );

        for (String seletor : seletoresSugestoes) {
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(seletor)));
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private boolean trySelectSuggestion(WebDriver driver, WebDriverWait wait, String value) {
        // Selectores para opções individuais dentro da lista
        List<By> locatores = List.of(
            By.xpath("//*[@role='option'][normalize-space(text())='" + value + "']"),
            By.xpath("//*[@role='option'][contains(normalize-space(text()),'" + value + "')]"),
            By.xpath("//*[contains(@class,'suggestion') or contains(@class,'option') or contains(@class,'item')][normalize-space(text())='" + value + "']"),
            By.xpath("//*[contains(@class,'suggestion') or contains(@class,'option') or contains(@class,'item')][contains(normalize-space(text()),'" + value + "')]"),
            By.cssSelector(".autocomplete-suggestion:first-child"),
            By.cssSelector("[role='option']:first-child")
        );

        for (By locator : locatores) {
            try {
                WebElement option = driver.findElement(locator);
                if (option.isDisplayed()) {
                    option.click();
                    return true;
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private boolean validate(WebElement element, String value) {
        try {
            String current = element.getAttribute("value");
            if (current != null && !current.isBlank()) {
                return current.equalsIgnoreCase(value)
                        || current.toLowerCase().contains(value.toLowerCase())
                        || value.toLowerCase().contains(current.toLowerCase());
            }
        } catch (Exception ignored) { }
        return false;
    }
}
