package com.qorbit.engine.strategy;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.classification.ComponentType;
import com.qorbit.engine.model.StepTeste;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DefaultStrategySelector implements StrategySelector {
    @Override
    public ExecutionPlan select(StepTeste step, ComponentClassification classification) {
        String action = step != null && step.getAcao() != null ? step.getAcao().trim().toUpperCase(Locale.ROOT) : "";
        ComponentType type = classification != null ? classification.type() : ComponentType.UNDEFINED;

        return switch (action) {
            case "WAIT" -> new ExecutionPlan(StrategyType.WAIT, "step de espera explícita");
            case "OPEN", "NAVEGAR" -> new ExecutionPlan(StrategyType.NAVIGATE, "ação de navegação");
            case "VALIDAR" -> new ExecutionPlan(StrategyType.VALIDATE, "validação explícita");
            case "SELECT", "SELECIONAR" -> {
                if (type == ComponentType.NATIVE_SELECT) {
                    yield new ExecutionPlan(StrategyType.NATIVE_SELECT, "select nativo detectado", List.of());
                }
                if (type == ComponentType.COMBOBOX) {
                    yield new ExecutionPlan(StrategyType.CUSTOM_COMBOBOX, "combobox customizado detectado", List.of(StrategyType.CLICK));
                }
                if (type == ComponentType.DATEPICKER) {
                    yield new ExecutionPlan(StrategyType.DATE_INPUT, "datepicker detectado", List.of(StrategyType.CLICK));
                }
                yield new ExecutionPlan(StrategyType.NATIVE_SELECT, "fallback de seleção", List.of(StrategyType.CUSTOM_COMBOBOX, StrategyType.CLICK));
            }
            case "INPUT", "PREENCHER" -> {
                if (type == ComponentType.DATEPICKER) {
                    yield new ExecutionPlan(StrategyType.DATE_INPUT, "datepicker com preenchimento controlado", List.of(StrategyType.TEXT_INPUT));
                }
                if (type == ComponentType.AUTOCOMPLETE) {
                    yield new ExecutionPlan(StrategyType.AUTOCOMPLETE, "campo autocomplete detectado", List.of(StrategyType.TEXT_INPUT));
                }
                if (type == ComponentType.CHECKBOX || type == ComponentType.RADIO || type == ComponentType.BUTTON || type == ComponentType.LINK) {
                    yield new ExecutionPlan(StrategyType.CLICK, "componente interativo tratado via clique", List.of());
                }
                if (type == ComponentType.COMBOBOX) {
                    yield new ExecutionPlan(StrategyType.CUSTOM_COMBOBOX, "combobox aceita seleção orientada", List.of(StrategyType.TEXT_INPUT));
                }
                yield new ExecutionPlan(StrategyType.TEXT_INPUT, "entrada textual padrão", List.of());
            }
            case "CLICK", "CLICAR" -> new ExecutionPlan(StrategyType.CLICK, "ação de clique", List.of());
            default -> new ExecutionPlan(StrategyType.CLICK, "fallback para clique", List.of());
        };
    }
}
