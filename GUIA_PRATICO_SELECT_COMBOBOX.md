# 🚀 GUIA PRÁTICO: Resolver Problema com SELECT/COMBOBOX

## ⏱️ Tempo estimado: 5-10 minutos

---

## 📌 Resumo do Problema

**Sintoma:** Quando você seleciona um combobox com múltiplas opções, o elemento é clicado  mas a opção específica não é selecionada.

**Razão provável:** O elemento está sendo classificado como `CUSTOM_COMBOBOX` quando é na verdade um `NATIVE_SELECT` HTML.

**Solução:** Usar `new Select(element).selectByVisibleText()` (seu código **JÁ FAZ ISTO**)

---

## 🔍 PASSO 1: Adicionar Logging para Diagnosticar

### 1.1 - Configurar nível de log do aplicação

Edite `src/main/resources/application.properties`:

```properties
# Adicione isso:
logging.level.com.qorbit.engine.strategy=DEBUG
logging.level.com.qorbit.engine.classification=DEBUG
```

### 1.2 - Executar um teste com logging

Execute seu teste/cenário normalmente. Você verá logs assim:

```
[StrategySelector] Acao: 'SELECIONAR' | Tipo detectado: NATIVE_SELECT (confianca: 95%)
[StrategySelector] SELECIONAR -> NATIVE_SELECT (usar Select do Selenium)
[NativeSelect] Tentando selecionar: 'ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME'
[NativeSelect] Opcoes disponiveis (3): [-- Selecione --, ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME, Outra Empresa]
[NativeSelect] Sucesso com selectByVisibleText
```

---

## 📊 PASSO 2: Interpretar os Logs

### Cenário A: ✅ SUCESSO (Sem mudanças necessárias)

```
[NativeSelect] Optcoes disponiveis (3): [...nossa-opcao...]
[NativeSelect] Sucesso com selectByVisibleText
```

→ Seu `NATIVE_SELECT` está funcionando! Verifique outra coisa.

---

### Cenário B: ❌ FALHA com NATIVE_SELECT

```
[NativeSelect] Tentando selecionar: 'ASIA TELECOM TELEFONIA E COMUNICACAO LTDA ME'
[NativeSelect] Opcoes disponiveis (3): [ASIA TELECOM..., outra, mais uma]
[NativeSelect] Exception: Opcao nao encontrada no select: ...
```

**Problema:** A opção não está sendo encontrada exatamente.

**Solução:**
1. Verifique se o texto tem espaços extras: `"  ASIA  TELECOM  "` vs `"ASIA TELECOM"`
2. Verifique acentuação: `"TELECOM"` vs `"TELËCOM"`
3. Se mesmo assim falhar, use `selectByValue()` em vez de `selectByVisibleText()`

---

### Cenário C: ⚠️ COMBOBOX CUSTOMIZADO

```
[StrategySelector] Tipo detectado: CUSTOM_COMBOBOX (confianca: 84%)
[StrategySelector] SELECIONAR -> CUSTOM_COMBOBOX (estrategia complexa)
```

**Seu HTML é assim:**
```html
<div role="combobox" aria-haspopup="listbox">
  <input type="text">
  <ul><li>ASIA TELECOM...</li></ul>
</div>
```

→ Isso é normal para comboboxes customizados. O log vai mostrar mais informações do `CustomComboboxExecutionStrategy`.

---

## 🔧 PASSO 3: Se Precisar Corrigir

### Opção A: Corrigir a Detecção do Tipo

Se o HTML é `<select>` mas está sendo classificado como `CUSTOM_COMBOBOX`:

Edite `src/main/java/com/qorbit/engine/classification/DefaultComponentClassifier.java`, linhas 106-124:

```java
private boolean isComboBox(...) {
    // IMPORTANTE: esta funcao deve retornar FALSE para <select> nativo!
    
    // Linhas atuais:
    if (role.contains("combobox") || ariaHasPopup.contains("listbox")) return true;
    if (classes.contains("select2") || classes.contains("dropdown") || classes.contains("combo")) return true;
    
    // ADICIONE esta verificacao NO INICIO:
    if ("select".equalsIgnoreCase(tag)) return false;  // ← ADICIONE ISTO
    
    // ... resto do codigo
}
```

**Por quê?** Garantir que `<select>` nativo nunca seja classificado como combobox.

---

### Opção B: Forçar a Estratégia Correta

Se o problema persistir, edite `DefaultStrategySelector.java` para prioritizar `NATIVE_SELECT`:

```java
case "SELECT", "SELECIONAR" -> {
    // Tente NATIVE_SELECT PRIMEIRO, sem importar o tipo detectado
    yield new ExecutionPlan(
        StrategyType.NATIVE_SELECT, 
        "forcar NATIVE_SELECT como estrategia primaria",
        List.of(StrategyType.CUSTOM_COMBOBOX, StrategyType.CLICK)  // fallbacks
    );
}
```

---

### Opção C: Verificar o Valor Exato da Opção

Se o valor está correto, mas ainda falha:

1. Adicione este código em um teste:

```java
@Test
void verificarOpcoes() {
    WebElement select = driver.findElement(By.id("ddlPartner"));
    Select s = new Select(select);
    
    System.out.println("Opcoes no <select>:");
    for (WebElement option : s.getOptions()) {
        System.out.println("  - Texto: [" + option.getText() + "]");
        System.out.println("    Valor: [" + option.getAttribute("value") + "]");
        System.out.println();
    }
}
```

2. Compare a saída com o valor que está tentando selecionar

3. Copie exatamente como aparece no HTML

---

## ✅ RESUMO: O Que Seu Código Já Faz

Seu projeto tem **múltiplas estratégias** de seleção, em ordem:

```
1. selectByVisibleText()  ← A sugestão de Stefan Teixeira
2. selectByValue()        ← Fallback por valor
3. Normalizacao          ← Remove acentos/espacos
4. Busca parcial         ← Contains
```

Se a primeira falhar, tenta a segunda, depois a terceira, etc.

---

## 🐛 Próximas Ações Recomendadas

1. **Ative o logging** conforme PASSO 1
2. **Rode um teste** e copie os logs
3. **Identifique qual é o cenário** (A, B ou C)
4. **Implemente a solução** correspondente se necessário

---

## 📞 Dúvidas? Verifique:

- O arquivo: [NativeSelectExecutionStrategy.java](src/main/java/com/qorbit/engine/strategy/impl/NativeSelectExecutionStrategy.java)
- O seletor: [DefaultStrategySelector.java](src/main/java/com/qorbit/engine/strategy/DefaultStrategySelector.java)
- O classificador: [DefaultComponentClassifier.java](src/main/java/com/qorbit/engine/classification/DefaultComponentClassifier.java)

---

**Data:** 11/04/2026
**Status:** ✅ Instruções criadas com sucesso
