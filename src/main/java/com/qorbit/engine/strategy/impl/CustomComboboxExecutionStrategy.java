package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class CustomComboboxExecutionStrategy implements ExecutionStrategy {
    @Override public StrategyType type() { return StrategyType.CUSTOM_COMBOBOX; }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) throw new IllegalArgumentException("Elemento não encontrado para combobox");
        String value = step.getValor() != null ? step.getValor() : "";
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        try {
            element.click();
        } catch (Exception ignored) { }

        if (tryTypeAhead(driver, element, value)) {
            return;
        }

        List<By> optionLocators = List.of(
                By.xpath("//*[(@role='option' or self::li or self::div or self::span)][normalize-space(text())='" + value + "']"),
                By.xpath("//*[contains(@class,'option') or contains(@class,'item')][normalize-space(text())='" + value + "']"),
                By.xpath("//*[contains(@class,'select2-results') or contains(@class,'dropdown') or @role='listbox']//*[normalize-space(text())='" + value + "']")
        );

        for (By locator : optionLocators) {
            try {
                WebElement option = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
                option.click();
                if (validateSelection(driver, element, value)) {
                    return;
                }
            } catch (Exception ignored) { }
        }

        throw new IllegalStateException("Falha ao seleccionar opção no combobox: " + value);
    }

    private boolean tryTypeAhead(WebDriver driver, WebElement element, String value) {
        try {
            String tag = element.getTagName();
            String role = safe(element.getAttribute("role"));
            if ("input".equalsIgnoreCase(tag) || role.contains("combobox")) {
                try { element.sendKeys(Keys.chord(Keys.CONTROL, "a")); } catch (Exception ignored) { }
                element.sendKeys(value);
                element.sendKeys(Keys.ENTER);
                return validateSelection(driver, element, value);
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean validateSelection(WebDriver driver, WebElement element, String value) {
        try {
            String current = element.getAttribute("value");
            if (current != null && (current.equalsIgnoreCase(value) || current.contains(value))) {
                return true;
            }
        } catch (Exception ignored) { }
        try {
            String text = element.getText();
            if (text != null && (text.equalsIgnoreCase(value) || text.contains(value))) {
                return true;
            }
        } catch (Exception ignored) { }
        try {
            if (driver instanceof JavascriptExecutor js) {
                Object result = js.executeScript("""
                        const el = arguments[0];
                        const expected = arguments[1].toLowerCase();
                        const txt = ((el.value || '') + ' ' + (el.textContent || '')).toLowerCase();
                        return txt.includes(expected);
                        """, element, value);
                return Boolean.TRUE.equals(result);
            }
        } catch (Exception ignored) { }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
