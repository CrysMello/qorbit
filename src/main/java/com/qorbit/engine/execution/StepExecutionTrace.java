package com.qorbit.engine.execution;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.strategy.ExecutionPlan;

public record StepExecutionTrace(
        ComponentClassification classification,
        ExecutionPlan plan,
        String signature
) {}
