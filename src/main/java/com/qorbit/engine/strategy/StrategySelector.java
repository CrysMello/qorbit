package com.qorbit.engine.strategy;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.model.StepTeste;

public interface StrategySelector {
    ExecutionPlan select(StepTeste step, ComponentClassification classification);
}
