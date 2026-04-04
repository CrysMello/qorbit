package com.qorbit.engine.strategy;

import java.util.List;

public record ExecutionPlan(
        StrategyType strategyType,
        String reason,
        List<StrategyType> fallbacks
) {
    public ExecutionPlan(StrategyType strategyType, String reason) {
        this(strategyType, reason, List.of());
    }
}
