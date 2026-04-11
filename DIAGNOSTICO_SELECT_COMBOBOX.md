# 🔧 Análise do Problema com SELECT/COMBOBOX

## 📋 Resumo Executivo

**Pergunta:** A solução de Stefan Teixeira (usar `new Select(element).selectByVisibleText()`) resolveria o problema?

**Resposta:** ✅ **SIM! Seu código JÁ implementa isto.** O problema provavelmente está na **detecção correta do tipo de elemento**.

---

## 📁 Estrutura Atual do Seu Código

Seu projeto tem uma **arquitetura bem fundamentada** com componentes bem definidos:

### 1. **Classificador de Componentes** 
   - 📄 Arquivo: [`DefaultComponentClassifier.java`](src/main/java/com/qorbit/engine/classification/DefaultComponentClassifier.java)
   - 🔍 Função: Detectar se é `NATIVE_SELECT` ou `CUSTOM_COMBOBOX`
   - ⚠️ **Ponto crítico**: Heurística de detection em `isComboBox()`

### 2. **Seletor de Estratégia**
   - 📄 Arquivo: [`DefaultStrategySelector.java`](src/main/java/com/qorbit/engine/strategy/DefaultStrategySelector.java)
   - 🔄 Função: Escolher qual estratégia usar baseado na classificação
   - ✅ Lógica: `SELECT/SELECIONAR` + `NATIVE_SELECT` → `NativeSelectExecutionStrategy`

### 3. **Estratégias de Execução**
   - 📄 [`NativeSelectExecutionStrategy.java`](src/main/java/com/qorbit/engine/strategy/impl/NativeSelectExecutionStrategy.java) - **Usa `Select` do Selenium ✅**
   - 📄 [`CustomComboboxExecutionStrategy.java`](src/main/java/com/qorbit/engine/strategy/impl/CustomComboboxExecutionStrategy.java) - Para combobox customizado

---

## ✅ O Que Seu Código JÁ Implementa

### NativeSelectExecutionStrategy.java (Linhas 33-35)
```java
// Estratégia 1: selectByVisibleText (Stefan Teixeira)
if (trySelectByVisibleText(select, rawValue)) return;

// Estratégia 2: selectByValue (fallback)
if (trySelectByValue(select, rawValue)) return;

// Estratégia 3: Normalização (remove acentos)
if (trySelectByNormalizedMatch(select, rawValue)) return;

// Estratégia 4: Busca parcial (contains)
if (trySelectByContains(select, rawValue)) return;
```

**Isto é ainda MELHOR que a solução básica de Stefan!** Seu código tenta múltiplas abordagens.

---

## 🐛 Possíveis Causas do Problema

### Hipótese 1: Classificação Incorreta ⚠️ (MAIS PROVÁVEL)

Se seu combobox está sendo classificado como `CUSTOM_COMBOBOX` quando é na verdade `NATIVE_SELECT`:

```java
// Em DefaultComponentClassifier.isComboBox() (linha 106)
private boolean isComboBox(WebDriver driver, WebElement element, ...) {
    if (role.contains("combobox") || ariaHasPopup.contains("listbox")) return true;
    if (classes.contains("select2") || classes.contains("dropdown") || classes.contains("combo")) return true;
    // ... pode estar incorretamente identificando como combobox
}
```

**Verificar:**
```html
<!-- Se seu HTML for assim, será classificado como NATIVE_SELECT ✅ -->
<select id="ddlPartner">
    <option>ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME</option>
</select>

<!-- Se for assim, será classificado como CUSTOM_COMBOBOX -->
<div role="combobox" aria-haspopup="listbox">
    <input>
    <ul><li>ASIA TELECOM...</li></ul>
</div>
```

### Hipótese 2: CustomComboboxExecutionStrategy com Bug

O `CustomComboboxExecutionStrategy` é mais complexo e pode ter problemas com:
- Múltiplas opções não sendo encontradas
- Problemas de visibilidade/clicabilidade

---

## 🛠️ Solução Recomendada

### Passo 1: Identificar o Tipo Correto

Adicione logging ao seu código para ver qual estratégia está sendo usada:

```java
// Em DefaultStrategySelector.select()
System.out.println("📍 ELEMENTO: " + classification.type() + 
                   " | AÇÃO: " + action + 
                   " | ESTRATÉGIA: " + plan.strategy());
```

### Passo 2: Se for NATIVE_SELECT mas falhar

Adicione debug ao `NativeSelectExecutionStrategy`:

```java
@Override
public void execute(WebDriver driver, WebElement element, StepTeste step) {
    System.out.println("🔍 Tentando select nativo com valor: " + step.getValor());
    System.out.println("📋 Opções disponíveis: " + listarOpcoes(new Select(element)));
    
    // Resto do código...
    
    if (trySelectByVisibleText(select, rawValue)) {
        System.out.println("✅ Sucesso com selectByVisibleText");
        return;
    }
}
```

### Passo 3: Se o Valor Não Combinar Exatamente

Seu código JÁ trata isto com `trySelectByNormalizedMatch()`:

```java
// Remove acentos, converte para minúsculas
private String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
                     .replaceAll("\\p{Mn}", "")
                     .toLowerCase(Locale.ROOT);
}
```

---

## 📊 Arquitetura de Decisão

```
┌─────────────────────────────────────────────────────────────┐
│ Step: SELECIONAR [elemento] = "ASIA TELECOM..."             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ DefaultComponentClassifier.classify()                        │
│ → Analisa: tag, role, classe, aria-*, etc                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
         ┌──────────────────┴──────────────────┐
         ↓                                     ↓
  É <select>?              Tem role="combobox"?
         ↓                                     ↓
    NATIVE_SELECT                      CUSTOM_COMBOBOX
         ↓                                     ↓
    DefaultStrategySelector             DefaultStrategySelector
    escolhe ↓                           escolhe ↓
NativeSelectExecutionStrategy  CustomComboboxExecutionStrategy
    ↓                                        ↓
USA: new Select()                    USA: click + type-ahead
.selectByVisibleText()               Complexo, mais falhas possíveis
```

---

## 🎯 Recomendações Finais

1. **Execute o teste diagnóstico** que criei: 
   ```bash
   mvn test -Dtest=SelectComboboxDiagnosticTest
   ```

2. **Verifique qual estratégia está sendo usada**:
   - Adicione logs em `DefaultStrategySelector.select()`
   - Veja qual tipo é detectado: `NATIVE_SELECT` ou `CUSTOM_COMBOBOX`

3. **Se for `CUSTOM_COMBOBOX` mas deveria ser `NATIVE_SELECT`**:
   - Ajuste `DefaultComponentClassifier.isComboBox()` para ser menos agressivo
   - Ou aumente o score de confiança do `NATIVE_SELECT`

4. **Se for `NATIVE_SELECT` mas ainda falhar**:
   - Verifique se o valor passado é exatamente igual ao texto da opção
   - Use `normalize()` para comparar sem acentos
   - Considere usar `selectByValue()` se souber o valor do atributo `value`

5. **Teste com valor exato**:
   ```java
   // Seu pom.xml já tem Selenium, então isto funcionará:
   WebElement select = driver.findElement(By.id("ddlPartner"));
   new Select(select).selectByVisibleText("ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME");
   ```

---

## 📝 Conclusão

**Sua solução (Stefan Teixeira) está 100% correta e já implementada.** 

O problema é **diagnóstico**: o elemento pode estar sendo classificado errado ou há um edge case no `CustomComboboxExecutionStrategy`.

Use o teste diagnóstico para identificar qual é o real culpado! 🔍
