package com.qorbit.engine.strategy;

import com.qorbit.engine.model.StepTeste;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public interface ExecutionStrategy {
    StrategyType type();
    void execute(WebDriver driver, WebElement element, StepTeste step) throws Exception;
}
