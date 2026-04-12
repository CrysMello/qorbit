package com.qorbit.engine.strategy;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.classification.ComponentType;
import com.qorbit.engine.model.StepTeste;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DefaultStrategySelector implements StrategySelector {
    private static final Logger logger = LoggerFactory.getLogger(DefaultStrategySelector.class);

    @Override
    public ExecutionPlan select(StepTeste step, ComponentClassification classification) {
        String action = step != null && step.getAcao() != null ? step.getAcao().trim().toUpperCase(Locale.ROOT) : "";
        ComponentType type = classification != null ? classification.type() : ComponentType.UNDEFINED;
        
        logger.debug("[StrategySelector] Acao: '{}' | Tipo detectado: {} (confianca: {}%)", 
                     action, type, classification != null ? classification.confidence() * 100 : 0);

        return switch (action) {
            case "WAIT" -> new ExecutionPlan(StrategyType.WAIT, "step de espera explicita");
            case "OPEN", "NAVEGAR" -> new ExecutionPlan(StrategyType.NAVIGATE, "acao de navegacao");
            case "VALIDAR" -> new ExecutionPlan(StrategyType.VALIDATE, "validacao explicita");
            case "SELECT", "SELECIONAR" -> {
                ExecutionPlan plan;
                if (type == ComponentType.NATIVE_SELECT) {
                    plan = new ExecutionPlan(StrategyType.NATIVE_SELECT, "select nativo detectado", List.of());
                    logger.info("[StrategySelector] SELECIONAR -> NATIVE_SELECT (usar Select do Selenium)");
                } else if (type == ComponentType.COMBOBOX) {
                    plan = new ExecutionPlan(StrategyType.CUSTOM_COMBOBOX, "combobox customizado detectado", List.of(StrategyType.CLICK));
                    logger.info("[StrategySelector] SELECIONAR -> CUSTOM_COMBOBOX (estrategia complexa)");
                } else if (type == ComponentType.DATEPICKER) {
                    plan = new ExecutionPlan(StrategyType.DATE_INPUT, "datepicker detectado", List.of(StrategyType.CLICK));
                    logger.info("[StrategySelector] SELECIONAR -> DATE_INPUT");
                } else {
                    plan = new ExecutionPlan(StrategyType.NATIVE_SELECT, "fallback de selecao", List.of(StrategyType.CUSTOM_COMBOBOX, StrategyType.CLICK));
                    logger.warn("[StrategySelector] Tipo desconhecido ({}), tentando NATIVE_SELECT como fallback", type);
                }
                yield plan;
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
                    yield new ExecutionPlan(StrategyType.CUSTOM_COMBOBOX, "combobox aceita selecao orientada", List.of(StrategyType.TEXT_INPUT));
                }
                String valor = step != null && step.getValor() != null ? step.getValor().trim() : "";
                if (pareceFormatoData(valor)) {
                    logger.info("[StrategySelector] Valor '{}' tem formato de data — usando DATE_INPUT (fallback: TEXT_INPUT)", valor);
                    yield new ExecutionPlan(StrategyType.DATE_INPUT, "valor com formato de data detectado", List.of(StrategyType.TEXT_INPUT));
                }
                yield new ExecutionPlan(StrategyType.TEXT_INPUT, "entrada textual padrao", List.of());
            }
            case "CLICK", "CLICAR" -> new ExecutionPlan(StrategyType.CLICK, "acao de clique", List.of());
            default -> new ExecutionPlan(StrategyType.CLICK, "fallback para clique", List.of());
        };
    }

    private boolean pareceFormatoData(String valor) {
        if (valor == null || valor.isBlank()) return false;
        return valor.matches("\\d{1,2}/\\d{1,2}/\\d{4}")
            || valor.matches("\\d{4}-\\d{2}-\\d{2}")
            || valor.matches("\\d{1,2}-\\d{1,2}-\\d{4}")
            || valor.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}");
    }
}
