package com.qorbit.engine.learning;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(LearningService.class)
public class NoOpLearningService implements LearningService {
    @Override public void onCacheLookup(String signature) { }
    @Override public String recommendStrategy(String signature, String componentType, String defaultStrategy) { return defaultStrategy; }
    @Override public void onSuccess(String signature, String strategy, String componentType) { }
    @Override public void onFailure(String signature, String strategy, String componentType, String reason) { }
    @Override public String buildTextReport() { return "Aprendizado persistente indisponível."; }
    @Override public String buildJsonReport() { return "{}"; }
}
