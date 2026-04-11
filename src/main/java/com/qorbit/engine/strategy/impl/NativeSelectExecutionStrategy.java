package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class NativeSelectExecutionStrategy implements ExecutionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(NativeSelectExecutionStrategy.class);

    @Override public StrategyType type() { return StrategyType.NATIVE_SELECT; }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            throw new IllegalArgumentException("Elemento nao encontrado para selecao");
        }

        String rawValue = step != null && step.getValor() != null ? step.getValor().trim() : "";
        if (rawValue.isBlank()) {
            throw new IllegalArgumentException("Valor nao informado para selecao");
        }

        // Validar que é realmente um <select>
        String tagName = element.getTagName().toLowerCase();
        if (!"select".equals(tagName)) {
            logger.warn("[NativeSelect] AVISO: Elemento nao eh <select>, eh <{}>. Tentando mesmo assim.", tagName);
        }

        Select select = new Select(element);
        String opcoes = listarOpcoes(select);
        
        logger.info("[NativeSelect] ===== INICIANDO SELECAO =====");
        logger.info("[NativeSelect] Tentando selecionar: '{}'", rawValue);
        logger.info("[NativeSelect] Opcoes disponiveis: {}", opcoes);

        if (trySelectByVisibleText(select, rawValue)) {
            logger.info("[NativeSelect] Sucesso com selectByVisibleText");
            validarSelecaoFinal(select, rawValue);
            return;
        }
        if (trySelectByValue(select, rawValue)) {
            logger.info("[NativeSelect] Sucesso com selectByValue");
            validarSelecaoFinal(select, rawValue);
            return;
        }
        if (trySelectByNormalizedMatch(select, rawValue)) {
            logger.info("[NativeSelect] Sucesso com normalizacao");
            validarSelecaoFinal(select, rawValue);
            return;
        }
        if (trySelectByContains(select, rawValue)) {
            logger.info("[NativeSelect] Sucesso com busca parcial (contains)");
            validarSelecaoFinal(select, rawValue);
            return;
        }

        String erro = "Opcao nao encontrada no select: " + rawValue + " | opcoes disponiveis: " + opcoes;
        logger.error("[NativeSelect] FALHA: {}", erro);
        throw new IllegalStateException(erro);
    }

    private boolean trySelectByVisibleText(Select select, String value) {
        try {
            select.selectByVisibleText(value);
            logger.debug("[NativeSelect] selectByVisibleText funcionou para: '{}'", value);
            return true;
        } catch (NoSuchElementException e) {
            logger.debug("[NativeSelect] selectByVisibleText falhou - opcao nao encontrada");
            return false;
        } catch (Exception e) {
            logger.debug("[NativeSelect] selectByVisibleText falhou - {}", e.getMessage());
            return false;
        }
    }

    private boolean trySelectByValue(Select select, String value) {
        try {
            select.selectByValue(value);
            logger.debug("[NativeSelect] selectByValue funcionou para: '{}'", value);
            return true;
        } catch (NoSuchElementException e) {
            logger.debug("[NativeSelect] selectByValue falhou - valor nao encontrado");
            return false;
        } catch (Exception e) {
            logger.debug("[NativeSelect] selectByValue falhou - {}", e.getMessage());
            return false;
        }
    }

    private boolean trySelectByNormalizedMatch(Select select, String value) {
        String expected = normalize(value);
        for (WebElement option : select.getOptions()) {
            String optionText = safe(option.getText());
            String optionValue = safe(option.getAttribute("value"));
            if (normalize(optionText).equals(expected) || normalize(optionValue).equals(expected)) {
                try {
                    option.click();
                    Thread.sleep(100);  // Aguardar processamento
                    logger.debug("[NativeSelect] Opcao selecionada por correspondencia normalizada: '{}'", optionText);
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("[NativeSelect] Selecao interrompida");
                    return false;
                } catch (Exception e) {
                    logger.debug("[NativeSelect] Falha ao clicar na opcao: {}", e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    private boolean trySelectByContains(Select select, String value) {
        String expected = normalize(value);
        for (WebElement option : select.getOptions()) {
            String optionText = normalize(option.getText());
            String optionValue = normalize(option.getAttribute("value"));
            if ((!optionText.isBlank() && optionText.contains(expected))
                    || (!optionValue.isBlank() && optionValue.contains(expected))) {
                try {
                    option.click();
                    Thread.sleep(100);  // Aguardar processamento
                    logger.debug("[NativeSelect] Opcao selecionada por busca parcial: '{}'", safe(option.getText()));
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("[NativeSelect] Selecao interrompida");
                    return false;
                } catch (Exception e) {
                    logger.debug("[NativeSelect] Falha ao clicar na opcao: {}", e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    private void validarSelecaoFinal(Select select, String valorEsperado) {
        try {
            // Aguardar um pouco para o elemento processar
            Thread.sleep(200);
            
            WebElement selectedOption = select.getFirstSelectedOption();
            String selectedText = selectedOption.getText().trim();
            String selectedValue = selectedOption.getAttribute("value");
            
            logger.info("[NativeSelect] Validacao - Opcao selecionada: '{}'", selectedText);
            logger.info("[NativeSelect] Validacao - Valor selecionado: '{}'", selectedValue);
            
            // Verificar se a seleção corresponde ao esperado (permitindo pequenas diferenças)
            if (!normalize(selectedText).contains(normalize(valorEsperado)) && 
                !normalize(valorEsperado).contains(normalize(selectedText))) {
                logger.warn("[NativeSelect] AVISO: Selecionado '{}' mas esperado '{}'", selectedText, valorEsperado);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[NativeSelect] Validacao interrompida");
        } catch (Exception e) {
            logger.warn("[NativeSelect] Nao foi possivel validar selecao: {}", e.getMessage());
        }
    }

    private String listarOpcoes(Select select) {
        List<String> options = select.getOptions().stream()
                .map(option -> safe(option.getText()))
                .filter(text -> !text.isBlank())
                .toList();
        return options.isEmpty() ? "<sem opcoes visiveis>" : options.stream().collect(Collectors.joining(", "));
    }

    private String normalize(String value) {
        String safeValue = safe(value).replace('\u00A0', ' ');
        String normalized = Normalizer.normalize(safeValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
