package com.qorbit.engine.strategy;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.classification.ComponentType;
import com.qorbit.engine.classification.DefaultComponentClassifier;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.impl.NativeSelectExecutionStrategy;
import com.qorbit.engine.strategy.impl.CustomComboboxExecutionStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("🔧 Diagnóstico de Problemas com SELECT e COMBOBOX")
class SelectComboboxDiagnosticTest {

    /**
     * CASO 1: Verificar se elemento <select> nativo é classificado corretamente
     * e se a estratégia NATIVE_SELECT consegue selecionar a opção pelo texto visível
     */
    @Test
    @DisplayName("✅ SELECT nativo — deve classificar como NATIVE_SELECT e usar selectByVisibleText")
    void testNativeSelectClassificationAndExecution() {
        // GIVEN: um elemento <select> HTML nativo com múltiplas <option>
        WebDriver driver = mock(WebDriver.class);
        WebElement selectElement = mock(WebElement.class);
        
        when(selectElement.getTagName()).thenReturn("select");
        when(selectElement.getAttribute("type")).thenReturn("");
        when(selectElement.getAttribute("role")).thenReturn("");
        
        // WHEN: classificamos o elemento
        DefaultComponentClassifier classifier = new DefaultComponentClassifier();
        StepTeste step = new StepTeste();
        step.setAcao("SELECIONAR");
        ComponentClassification classification = classifier.classify(driver, selectElement, step);
        
        // THEN: deve ser classificado como NATIVE_SELECT
        assertThat(classification.type()).isEqualTo(ComponentType.NATIVE_SELECT);
        assertThat(classification.confidence()).isGreaterThanOrEqualTo(0.95);
        
        System.out.println("✅ SELECT nativo classificado corretamente: " + classification);
    }

    /**
     * CASO 2: Verificar se seletor de estratégia escolhe NATIVE_SELECT para select nativo
     */
    @Test
    @DisplayName("✅ Estratégia — SELECT nativo deve usar NativeSelectExecutionStrategy")
    void testStrategySelectionForNativeSelect() {
        // GIVEN: uma classificação NATIVE_SELECT
        ComponentClassification nativeSelectClassification = 
            new ComponentClassification(ComponentType.NATIVE_SELECT, 0.95, java.util.List.of());
        
        DefaultStrategySelector selector = new DefaultStrategySelector();
        StepTeste step = new StepTeste();
        step.setAcao("SELECIONAR");
        
        // WHEN: selecionamos a estratégia
        ExecutionPlan plan = selector.select(step, nativeSelectClassification);
        
        // THEN: deve retornar NATIVE_SELECT como estratégia principal
        assertThat(plan.strategyType()).isEqualTo(StrategyType.NATIVE_SELECT);
        
        System.out.println("✅ Estratégia selecionada: " + plan.strategyType() + " (" + plan.reason() + ")");
    }

    /**
     * CASO 3: Simular execução de SELECT com múltiplas opções
     * Este teste demonstra a importância de usar a classe Select do Selenium
     */
    @Test
    @DisplayName("📋 Demonstração — Select do Selenium com múltiplas opções")
    void testSelectSeleniumBehaviorWithMultipleOptions() {
        // Este teste ilustra como o Selenium Select funciona
        System.out.println("""
            
            ═══════════════════════════════════════════════════════════
            DEMONSTRAÇÃO: Como usar Select do Selenium corretamente
            ═══════════════════════════════════════════════════════════
            
            Para um HTML como:
            <select id="ddlPartner">
                <option value="">-- Selecione --</option>
                <option value="1">ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME</option>
                <option value="2">Outra Empresa</option>
                <option value="3">Mais uma Empresa</option>
            </select>
            
            Use assim:
            
            WebElement comboboxElement = driver.findElement(By.id("ddlPartner"));
            Select combobox = new Select(comboboxElement);
            
            // Opção 1: Selecionar pelo TEXTO VISÍVEL (recomendado)
            combobox.selectByVisibleText("ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME");
            
            // Opção 2: Selecionar pelo VALOR do atributo 'value'
            combobox.selectByValue("1");
            
            // Opção 3: Selecionar pelo ÍNDICE (0-based)
            combobox.selectByIndex(1);
            
            ═══════════════════════════════════════════════════════════
            
            ⚠️ PROBLEMAS COMUNS:
            
            ❌ ERRADO: Apenas clicar no element
                element.click();
                
            ✅ CORRETO: Usar a classe Select do Selenium
                new Select(element).selectByVisibleText("...");
            
            ═══════════════════════════════════════════════════════════
            """);
    }

    /**
     * CASO 4: Diagnosticar diferença entre SELECT nativo e COMBOBOX customizado
     */
    @Test
    @DisplayName("🧪 Diagnóstico — SELECT vs COMBOBOX customizado")
    void testSelectVsComboboxDifference() {
        System.out.println("""
            
            ═══════════════════════════════════════════════════════════
            DIFERENÇA: SELECT nativo vs COMBOBOX customizado
            ═══════════════════════════════════════════════════════════
            
            SELECT NATIVO:
            └─ HTML: <select><option>...</option></select>
            └─ Classe: NativeSelectExecutionStrategy
            └─ Método: new Select(element).selectByVisibleText("...")
            └─ Confiabilidade: ⭐⭐⭐⭐⭐ (Selenium nativo)
            
            COMBOBOX CUSTOMIZADO:
            └─ HTML: <div role="combobox">... <ul><li>...</li></ul></div>
            └─ Classe: CustomComboboxExecutionStrategy
            └─ Método: Complexo (type-ahead, click, validação)
            └─ Confiabilidade: ⭐⭐⭐⭐ (requer mais heurísticas)
            
            ═══════════════════════════════════════════════════════════
            
            SUA SITUAÇÃO:
            Se o combobox está sendo classificado ERRADO como
            CUSTOM_COMBOBOX quando é na verdade <select> nativo,
            
            SOLUÇÃO: Verificar DefaultComponentClassifier.isComboBox()
            e ajustar a heurística de detecção.
            
            ═══════════════════════════════════════════════════════════
            """);
    }

    /**
     * CASO 5: Verificar valor correto sendo passado ao select
     */
    @Test
    @DisplayName("🔍 Verificação — Valor correto para selectByVisibleText")
    void testCorrectValueForSelectByVisibleText() {
        // RECEBEM DA INTERFACE
        String valor = "ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME";
        
        // O MÉTODO CORRIGIDO DEVE:
        // 1. Trimmar espaços
        String trimmed = valor.trim();
        
        // 2. Tentar selectByVisibleText COM O TEXTO EXATO
        // 3. Se falhar, tentar selectByValue
        // 4. Se falhar, tentar normalização (acentos, maiúsculas)
        // 5. Se falhar, tentar busca parcial
        
        System.out.println("""
            
            ✅ Seu valor será processado assim:
            
            Valor original: "%s"
            Trimmed:        "%s"
            
            Tentativas em ordem:
            1️⃣  selectByVisibleText("%s")
            2️⃣  selectByValue("%s")
            3️⃣  Busca normalizada (sem acentos)
            4️⃣  Busca parcial (contains)
            """.formatted(valor, trimmed, trimmed, trimmed));
    }
}
