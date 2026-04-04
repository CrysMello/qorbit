package com.qorbit.engine.learning;

public interface LearningService {
    void onCacheLookup(String signature);
    String recommendStrategy(String signature, String componentType, String defaultStrategy);
    void onSuccess(String signature, String strategy, String componentType);
    void onFailure(String signature, String strategy, String componentType, String reason);
    String buildTextReport();
    String buildJsonReport();
}
