package com.qorbit.engine.classification;

import java.util.List;

public record ComponentClassification(
        ComponentType type,
        double confidence,
        List<String> evidences
) {
    public static ComponentClassification undefined(String reason) {
        return new ComponentClassification(ComponentType.UNDEFINED, 0.0, List.of(reason));
    }
}
