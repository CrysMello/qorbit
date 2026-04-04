package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

@Component
public class WaitExecutionStrategy implements ExecutionStrategy {
    @Override public StrategyType type() { return StrategyType.WAIT; }
    @Override public void execute(WebDriver driver, WebElement element, StepTeste step) throws Exception {
        Thread.sleep(Long.parseLong(step.getValor() != null ? step.getValor() : "1000"));
    }
}
