# Qorbit Engine — Arquitetura Atualizada

## Visão Geral

O Qorbit Engine evolui para uma engine orientada a decisão com memória operacional. O pipeline de step segue a sequência:

`Step -> Resolver elemento -> Classificar componente -> Gerar assinatura -> Consultar aprendizado -> Escolher estratégia -> Executar -> Aplicar fallback -> Validar resultado -> Atualizar aprendizado -> Diagnosticar`

## Módulos

### execution
Coordena o pipeline do step. É o ponto de entrada da decisão de execução.

### selenium
Mantém a integração com o WebDriver, resolução de elementos, screenshots e orquestração de execução da suíte.

### classification
Analisa atributos e comportamento do componente para classificar o tipo funcional. Nesta entrega há heurísticas para `datepicker`, `combobox`, `autocomplete`, `select`, `input`, `checkbox`, `radio`, `button` e `link`.

### signature
Gera a assinatura estável do componente usando dados semânticos, evitando depender apenas do selector bruto.

### strategy
Seleciona a estratégia principal e os fallbacks compatíveis. As estratégias são unidades separadas de execução.

### learning
Persiste o aprendizado em SQLite (`qorbit.db`). Mantém cache por assinatura, histórico de sucesso/falha e estatísticas por tipo.

### diagnostic
Traduz falhas técnicas para categorias mais úteis, como modelagem, estratégia, sincronização e validação.

## Fluxo técnico da Entrega 2

1. `SeleniumWorker` resolve o elemento
2. `StepExecutionPipeline` classifica o componente
3. `ComponentSignatureService` gera a assinatura
4. `LearningService` consulta cache e histórico
5. `StrategySelector` escolhe a estratégia base
6. o aprendizado pode promover estratégia recomendada
7. `StrategyExecutor` executa a estratégia principal
8. se necessário, fallbacks compatíveis são tentados
9. o resultado é persistido no módulo de aprendizado
10. falhas são resumidas pelo diagnóstico

## Segurança

A configuração sensível da IA usa variáveis de ambiente:

- `QORBIT_AI_API_KEY`
- `QORBIT_AI_ENDPOINT`
- `QORBIT_AI_MODEL`
- `QORBIT_AI_ENABLED`

O caminho do banco também pode ser sobrescrito por `QORBIT_DB_PATH`.

## Relatórios

O aprendizado pode ser consultado via CLI com `--report` e `--json`.


## Integração do aprendizado no pipeline (Entrega 2.2)

O `StepExecutionPipeline` agora usa `recommendStrategy()` antes de executar cada step:

1. O selector escolhe a estratégia base com base no tipo classificado
2. O `LearningService` consulta o histórico e o cache por assinatura
3. Se houver estratégia promovida com confiança >= 0.60 e menos de 2 falhas consecutivas, ela substitui a estratégia base
4. O resultado é registado no aprendizado independentemente do resultado

## Suporte a Autocomplete (Entrega 2.2)

A `AutocompleteExecutionStrategy` suporta campos com sugestões dinâmicas:
- Detectado por `aria-autocomplete`, atributo `list` (datalist), ou classes como `typeahead`, `autosuggest`
- Fluxo: digitar → aguardar sugestões → seleccionar opção exacta → fallback com ENTER
- Valida se o valor foi realmente aplicado antes de considerar o step concluído
- Suporte a Google Places, Select2, UI Autocomplete, e implementações custom

## Estratégia de Datepicker (Entrega 2.3)

A `DateInputExecutionStrategy` foi completamente reescrita para lidar com a diversidade de implementações de datepicker encontradas em aplicações modernas. O fluxo interno segue os critérios de aceite definidos para a entrega:

**Detecção de campo:** verifica `readonly` e `aria-readonly` antes de decidir a estratégia. Campos readonly nunca recebem sendKeys.

**Estratégia de digitação:** tenta `sendKeys` seguido de validação do valor aplicado no campo. Um segundo `sendKeys(TAB)` é enviado para acionar eventos de blur quando necessário.

**Abertura do calendário:** clica no campo e, se o calendário não aparecer, busca um trigger associado (ícone irmão, botão no wrapper, aria-controls). Aguarda até 5 segundos pela visibilidade do contêiner.

**Navegação de ano:** tenta via `<select>` de ano, input numérico de ano (Flatpickr), ou visão de anos (Angular Material — clique no botão de período e seleção da célula de ano).

**Navegação de mês:** loop com limite de 36 iterações; detecta o mês/ano atual a partir de selects, cabeçalho de texto (PT/EN) ou padrão numérico `MM/YYYY`. Clica em next ou prev conforme a direção necessária.

**Seleção de dia:** distingue entre dia ausente (DatePickerDateNotFoundException) e dia desabilitado (DatePickerDateDisabledException). Tenta clique direto e fallback via `JavascriptExecutor`.

**Suporte a range:** detecta dois calendários visíveis e resolve qual usar por aria-controls ou posição semântica do campo (classe/nome contendo "start"/"end"/"inicio"/"fim").

**Validação final:** compara o valor do campo com o esperado; se divergir, tenta JS como último recurso antes de lançar exceção.

**Exceções específicas:**
- `DatePickerCalendarNotOpenedException` — calendário não ficou visível
- `DatePickerDateNotFoundException` — dia não encontrado no calendário
- `DatePickerDateDisabledException` — data encontrada mas bloqueada

**Compatibilidade verificada:** jQuery UI Datepicker, Flatpickr, React Datepicker, Angular Material Datepicker, Ant Design DatePicker, Pikaday, Air Datepicker, Bootstrap Datepicker e implementações customizadas com padrões ARIA.

Antes do `SpringApplication.run(...)`, o bootstrap da aplicação prepara a infraestrutura local do banco SQLite. Essa responsabilidade fica na borda de entrada da aplicação e garante o comportamento esperado de primeira execução:

- resolução do caminho do banco via `QORBIT_DB_PATH` ou `db/qorbit.db`
- criação automática da pasta pai
- criação automática do arquivo do banco
- publicação do caminho final para o contexto Spring

Esse hardening evita falhas de arranque causadas por diretório inexistente, como `path to 'db/qorbit.db' does not exist`.
