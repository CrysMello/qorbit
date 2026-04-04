package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

@Component
public class NavigateExecutionStrategy implements ExecutionStrategy {
    @Override public StrategyType type() { return StrategyType.NAVIGATE; }
    @Override public void execute(WebDriver driver, WebElement element, StepTeste step) {
        driver.get(step.getValor() != null ? step.getValor() : "");
        try {
            Thread.sleep(250);
            ((JavascriptExecutor) driver).executeScript("return document.readyState");
        } catch (Exception ignored) { }
    }
}
