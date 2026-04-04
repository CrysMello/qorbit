package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.springframework.stereotype.Component;

@Component
public class NativeSelectExecutionStrategy implements ExecutionStrategy {
    @Override public StrategyType type() { return StrategyType.NATIVE_SELECT; }
    @Override public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) throw new IllegalArgumentException("Elemento não encontrado para seleção");
        new Select(element).selectByVisibleText(step.getValor() != null ? step.getValor() : "");
    }
}
