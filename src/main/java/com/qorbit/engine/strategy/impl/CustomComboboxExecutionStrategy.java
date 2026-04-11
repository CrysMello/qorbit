package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CustomComboboxExecutionStrategy implements ExecutionStrategy {
    @Override public StrategyType type() { return StrategyType.CUSTOM_COMBOBOX; }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) throw new IllegalArgumentException("Elemento nao encontrado para combobox");

        String value = step != null && step.getValor() != null ? step.getValor().trim() : "";
        if (value.isBlank()) {
            throw new IllegalArgumentException("Valor nao informado para combobox");
        }

        abrirCombobox(driver, element);
        if (tryTypeAhead(driver, element, value)) {
            return;
        }

        List<By> optionLocators = List.of(
                By.xpath(".//*[@role='option']"),
                By.xpath(".//li"),
                By.xpath(".//button"),
                By.xpath(".//*[contains(@class,'option') or contains(@class,'item') or contains(@class,'menu-item') or contains(@class,'select2-results__option')]"),
                By.xpath(".//*[self::div or self::span][normalize-space()]")
        );

        WebElement container = localizarContainerOpcoes(driver, element);
        List<WebElement> candidates = coletarCandidatos(container, optionLocators);
        if (candidates.isEmpty()) {
            candidates = coletarCandidatos(driver, optionLocators);
        }

        for (WebElement option : candidates) {
            if (!isMatchingOption(option, value) || !isInteractable(driver, option)) {
                continue;
            }
            scrollIntoView(driver, option);
            clickWithRetry(driver, option);
            pause();
            if (validateSelection(driver, element, value)) {
                return;
            }
        }

        if (tryTypeAheadWithNavigation(driver, element, value)) {
            return;
        }

        throw new IllegalStateException("Falha ao selecionar opcao no combobox: " + value
                + " | candidatos encontrados: " + descreverCandidatos(candidates));
    }

    private boolean tryTypeAhead(WebDriver driver, WebElement element, String value) {
        try {
            WebElement input = resolveTypingElement(element);
            if (input == null) {
                return false;
            }
            selectAll(input);
            input.sendKeys(value);
            pause();
            if (validateSelection(driver, element, value) || validateSelection(driver, input, value)) {
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean tryTypeAheadWithNavigation(WebDriver driver, WebElement element, String value) {
        try {
            WebElement input = resolveTypingElement(element);
            if (input == null) {
                return false;
            }
            selectAll(input);
            input.sendKeys(value);
            pause();
            input.sendKeys(Keys.ARROW_DOWN);
            input.sendKeys(Keys.ENTER);
            pause();
            if (validateSelection(driver, element, value) || validateSelection(driver, input, value)) {
                return true;
            }
            input.sendKeys(Keys.TAB);
            pause();
            return validateSelection(driver, element, value) || validateSelection(driver, input, value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private WebElement resolveTypingElement(WebElement element) {
        try {
            String tag = safe(element.getTagName()).toLowerCase(Locale.ROOT);
            String role = safe(element.getAttribute("role")).toLowerCase(Locale.ROOT);
            if ("input".equals(tag) || "textarea".equals(tag) || role.contains("combobox")) {
                return element;
            }
        } catch (Exception ignored) { }
        try {
            return element.findElement(By.cssSelector("input, textarea"));
        } catch (NoSuchElementException ignored) {
            return null;
        }
    }

    private boolean validateSelection(WebDriver driver, WebElement element, String value) {
        String expected = normalize(value);
        try {
            if (matches(element.getAttribute("value"), expected)) return true;
        } catch (Exception ignored) { }
        try {
            if (matches(element.getText(), expected)) return true;
        } catch (Exception ignored) { }
        try {
            if (matches(element.getAttribute("aria-label"), expected)) return true;
        } catch (Exception ignored) { }
        try {
            if (driver instanceof JavascriptExecutor js) {
                Object result = js.executeScript("return [arguments[0].value || '', arguments[0].textContent || '', arguments[0].getAttribute('aria-label') || '', arguments[0].getAttribute('title') || ''].join(' ');", element);
                if (result instanceof String text && matches(text, expected)) {
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void abrirCombobox(WebDriver driver, WebElement element) {
        scrollIntoView(driver, element);
        clickWithRetry(driver, element);
        pause();
    }

    private WebElement localizarContainerOpcoes(WebDriver driver, WebElement element) {
        for (String attr : List.of("aria-controls", "aria-owns")) {
            String ref = safe(element.getAttribute(attr)).trim();
            if (!ref.isBlank()) {
                try {
                    WebElement found = driver.findElement(By.id(ref));
                    if (found.isDisplayed()) return found;
                } catch (Exception ignored) { }
            }
        }

        List<By> localLocators = List.of(
                By.xpath("./ancestor::*[@role='combobox' or @role='listbox' or contains(@class,'select2') or contains(@class,'dropdown') or contains(@class,'combo')][1]"),
                By.xpath("./following-sibling::*[@role='listbox' or contains(@class,'dropdown') or contains(@class,'menu') or contains(@class,'popup')][1]")
        );
        for (By locator : localLocators) {
            try {
                WebElement found = element.findElement(locator);
                if (found.isDisplayed()) return found;
            } catch (Exception ignored) { }
        }

        List<By> globalLocators = List.of(
                By.cssSelector("[role='listbox']"),
                By.cssSelector(".select2-results, .select2-dropdown, .choices__list--dropdown, .dropdown-menu.show, .menu.show, .MuiAutocomplete-popper, .mat-mdc-autocomplete-panel, .ng-dropdown-panel, .vs__dropdown-menu")
        );
        for (By locator : globalLocators) {
            try {
                for (WebElement candidate : driver.findElements(locator)) {
                    if (candidate.isDisplayed()) return candidate;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private List<WebElement> coletarCandidatos(SearchContext context, List<By> locators) {
        if (context == null) return List.of();
        List<WebElement> candidates = new ArrayList<>();
        for (By locator : locators) {
            try {
                for (WebElement found : context.findElements(locator)) {
                    if (!candidates.contains(found)) {
                        candidates.add(found);
                    }
                }
            } catch (Exception ignored) { }
        }
        return candidates;
    }

    private boolean isMatchingOption(WebElement option, String value) {
        try {
            String combined = String.join(" ",
                    safe(option.getText()),
                    safe(option.getAttribute("value")),
                    safe(option.getAttribute("aria-label")),
                    safe(option.getAttribute("title")),
                    safe(option.getAttribute("data-value")));
            return matches(combined, normalize(value));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isInteractable(WebDriver driver, WebElement option) {
        try {
            if (!option.isDisplayed() || !option.isEnabled()) return false;
            Object result = ((JavascriptExecutor) driver).executeScript("const rect = arguments[0].getBoundingClientRect(); return rect.width > 0 && rect.height > 0;", option);
            return Boolean.TRUE.equals(result);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void scrollIntoView(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
        } catch (Exception ignored) { }
    }

    private void clickWithRetry(WebDriver driver, WebElement option) {
        try {
            option.click();
        } catch (Exception ignored) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", option);
            } catch (Exception ignoredAgain) { }
        }
    }

    private void selectAll(WebElement input) {
        try {
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        } catch (Exception ignored) { }
    }

    private boolean matches(String actual, String expectedNormalized) {
        String normalizedActual = normalize(actual);
        return !normalizedActual.isBlank()
                && (normalizedActual.equals(expectedNormalized)
                || normalizedActual.contains(expectedNormalized)
                || expectedNormalized.contains(normalizedActual));
    }

    private String normalize(String value) {
        String safeValue = safe(value).replace('\u00A0', ' ');
        String normalized = Normalizer.normalize(safeValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String descreverCandidatos(List<WebElement> candidates) {
        List<String> values = new ArrayList<>();
        for (WebElement candidate : candidates) {
            try {
                String text = safe(candidate.getText()).trim();
                if (!text.isBlank()) {
                    values.add(text);
                }
            } catch (Exception ignored) { }
            if (values.size() >= 8) break;
        }
        return values.isEmpty() ? "<nenhum texto visivel>" : String.join(", ", values);
    }

    private void pause() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
