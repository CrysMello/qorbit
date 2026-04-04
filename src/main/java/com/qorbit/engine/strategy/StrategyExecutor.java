package com.qorbit.engine.strategy;

import com.qorbit.engine.model.StepTeste;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrategyExecutor {
    private final List<ExecutionStrategy> strategies;

    public StrategyExecutor(List<ExecutionStrategy> strategies) {
        this.strategies = strategies;
    }

    public void execute(ExecutionPlan plan, WebDriver driver, WebElement element, StepTeste step) throws Exception {
        for (ExecutionStrategy strategy : strategies) {
            if (strategy.type() == plan.strategyType()) {
                strategy.execute(driver, element, step);
                return;
            }
        }
        throw new IllegalStateException("Nenhuma estratégia registrada para: " + plan.strategyType());
    }
}
