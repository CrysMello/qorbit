package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

@Component
public class TextInputExecutionStrategy implements ExecutionStrategy {
    @Override public StrategyType type() { return StrategyType.TEXT_INPUT; }
    @Override public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) throw new IllegalArgumentException("Elemento não encontrado para preenchimento");
        String value = step.getValor() != null ? step.getValor() : "";
        try {
            element.click();
        } catch (Exception ignored) { }
        try {
            element.clear();
        } catch (Exception ignored) { }
        try {
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        } catch (Exception ignored) { }
        element.sendKeys(value);
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('input',{bubbles:true})); arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                    element);
        } catch (Exception ignored) { }
        String current = readValue(element);
        if (current != null && !current.isBlank() && !current.equals(value) && !current.contains(value)) {
            throw new IllegalStateException("Valor não aplicado ao input. Esperado=" + value + " actual=" + current);
        }
    }

    private String readValue(WebElement element) {
        try {
            String value = element.getAttribute("value");
            if (value != null) return value;
        } catch (Exception ignored) { }
        try {
            return element.getText();
        } catch (Exception ignored) { }
        return null;
    }
}
