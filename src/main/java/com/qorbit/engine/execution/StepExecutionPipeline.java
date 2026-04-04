package com.qorbit.engine.execution;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.classification.ComponentClassifier;
import com.qorbit.engine.diagnostic.DiagnosticService;
import com.qorbit.engine.learning.LearningService;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.signature.ComponentSignatureService;
import com.qorbit.engine.strategy.ExecutionPlan;
import com.qorbit.engine.strategy.StrategyExecutor;
import com.qorbit.engine.strategy.StrategySelector;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

@Service
public class StepExecutionPipeline {
    private final ComponentClassifier classifier;
    private final ComponentSignatureService signatureService;
    private final StrategySelector strategySelector;
    private final StrategyExecutor strategyExecutor;
    private final LearningService learningService;
    private final DiagnosticService diagnosticService;

    public StepExecutionPipeline(ComponentClassifier classifier,
                                 ComponentSignatureService signatureService,
                                 StrategySelector strategySelector,
                                 StrategyExecutor strategyExecutor,
                                 LearningService learningService,
                                 DiagnosticService diagnosticService) {
        this.classifier = classifier;
        this.signatureService = signatureService;
        this.strategySelector = strategySelector;
        this.strategyExecutor = strategyExecutor;
        this.learningService = learningService;
        this.diagnosticService = diagnosticService;
    }

    public StepExecutionTrace execute(WebDriver driver, WebElement element, StepTeste step) throws Exception {
        ComponentClassification classification = classifier.classify(driver, element, step);
        String signature = signatureService.generate(element, step, classification);
        learningService.onCacheLookup(signature);

        ExecutionPlan initialPlan = strategySelector.select(step, classification);
        String recommended = learningService.recommendStrategy(signature, classification.type().name(), initialPlan.strategyType().name());
        ExecutionPlan selectedPlan = recommended.equalsIgnoreCase(initialPlan.strategyType().name())
                ? initialPlan
                : new ExecutionPlan(StrategyType.valueOf(recommended), "estratégia promovida por aprendizado", initialPlan.fallbacks());

        try {
            strategyExecutor.execute(selectedPlan, driver, element, step);
            learningService.onSuccess(signature, selectedPlan.strategyType().name(), classification.type().name());
            return new StepExecutionTrace(classification, selectedPlan, signature);
        } catch (Exception primaryError) {
            for (StrategyType fallback : selectedPlan.fallbacks()) {
                try {
                    strategyExecutor.execute(new ExecutionPlan(fallback, "fallback compatível após falha da estratégia principal"), driver, element, step);
                    learningService.onSuccess(signature, fallback.name(), classification.type().name());
                    return new StepExecutionTrace(classification,
                            new ExecutionPlan(fallback, "fallback compatível executado com sucesso"), signature);
                } catch (Exception fallbackError) {
                    learningService.onFailure(signature, fallback.name(), classification.type().name(), fallbackError.getMessage());
                }
            }
            learningService.onFailure(signature, selectedPlan.strategyType().name(), classification.type().name(), primaryError.getMessage());
            throw primaryError;
        }
    }

    public String classifyFailure(WebElement element, StepTeste step, Throwable throwable) {
        var classification = classifier.classify(null, element, step);
        String signature = signatureService.generate(element, step, classification);
        learningService.onFailure(signature, "UNKNOWN", classification.type().name(), throwable != null ? throwable.getMessage() : null);
        return diagnosticService.summarize(throwable);
    }
}
