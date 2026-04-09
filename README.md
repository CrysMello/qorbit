 Qorbit Engine

Qorbit Engine é uma engine de automação web com foco em execução orientada por estratégia, auto-healing controlado e aprendizado persistente. Nesta versão, a arquitetura deixa de ser centrada em um executor linear e passa a seguir um pipeline: resolver elemento, classificar componente, gerar assinatura, consultar aprendizado, escolher estratégia, executar, aplicar fallback compatível, validar resultado e atualizar o diagnóstico.

## O que entrou na Entrega 2

- classificação funcional de componentes com heurísticas para `input`, `textarea`, `select`, `combobox`, `datepicker`, `checkbox`, `radio`, `button` e `link`
- seleção de estratégia por tipo de componente
- fallback compatível por estratégia
- aprendizado persistente em SQLite no arquivo `qorbit.db`
- cache por assinatura de componente
- estatísticas por tipo de componente e estratégia
- relatório CLI em texto ou JSON
- reforço de segurança para configuração da API key via variável de ambiente

## Suporte opcional de CI/CD no projeto exportado

A exportação de projetos agora pode incluir uma configuração pronta de CI/CD. Quando a opção **Incluir configuração de CI/CD** estiver ativa, o pacote exportado passa a gerar automaticamente:

- arquivo `.github/workflows/testes.yml`
- `DriverFactory.java` preparado para execução headless por padrão em CI/CD
- configuração de headless sobrescrevível por propriedade
- README do projeto exportado com instruções de uso em ambiente local e pipeline

Quando a opção não estiver ativa, o comportamento atual de exportação é preservado, sem arquivos ou configurações adicionais de pipeline.

### O que é gerado com CI/CD ativado

#### 1. Workflow GitHub Actions

Arquivo incluído no projeto exportado:

```text
.github/workflows/testes.yml
```

Conteúdo esperado do workflow:

- execução em `push` e `pull_request`
- Java 17
- cache Maven habilitado
- execução de `mvn clean test`
- publicação de artefatos mesmo em caso de falha com `if: always()`

#### 2. DriverFactory preparado para CI/CD

Quando CI/CD estiver habilitado, o `DriverFactory.java` exportado deve:

- usar modo headless por padrão
- incluir `--no-sandbox`
- incluir `--disable-dev-shm-usage`
- incluir `--window-size=1920,1080`

Exemplo de comportamento configurável:

```java
boolean headless = !"false".equalsIgnoreCase(System.getProperty("headless", "true"));
```

Isso permite sobrescrever a execução localmente com:

```bash
mvn test -Dheadless=false
```

#### 3. Compatibilidade com execução local

Mesmo com a configuração de pipeline incluída, o projeto exportado continua executável localmente.

Execução local com navegador visível:

```bash
mvn test -Dheadless=false
```

Execução headless:

```bash
mvn clean test
```

#### 4. README do export atualizado

Quando a opção de CI/CD estiver ativa, o README do projeto exportado deve explicar:

- como rodar localmente
- como rodar em headless
- como funciona a execução via GitHub Actions

#### 5. Estrutura esperada no ZIP

Quando CI/CD estiver ativado, o `.zip` exportado deve conter:

- `.github/workflows/testes.yml`
- código exportado com `DriverFactory` compatível com CI/CD
- documentação atualizada

#### 6. Regras de negócio

- a inclusão de pipeline é opcional
- o workflow inicial suportado é GitHub Actions
- o navegador padrão do pipeline é Chrome
- o modo headless é padrão para contexto de CI/CD, mas com possibilidade de override
- a funcionalidade prioriza zero configuração manual após a exportação

## Arquitetura resumida

Fluxo de execução:

`Step -> Resolver elemento -> Classificar componente -> Gerar assinatura -> Consultar aprendizado -> Escolher estratégia -> Executar -> Fallback -> Validar -> Aprender -> Diagnosticar`

Módulos principais:

- `execution`: orquestração do pipeline de step
- `classification`: classificação funcional do componente
- `signature`: identidade estável para cache e aprendizado
- `strategy`: seleção e execução das estratégias
- `learning`: memória operacional em SQLite
- `diagnostic`: tradução de falhas técnicas em causa útil
- `service`: integração com captura, logs, IA e relatórios gerais

## Banco SQLite

Por padrão o projeto usa um arquivo único:

`db/qorbit.db`

Você pode sobrescrever o caminho via variável de ambiente:

```bash
export QORBIT_DB_PATH=/meu/caminho/qorbit.db
```

Comportamento esperado:

- primeira execução: cria pasta `db/` se necessário e passa a usar `qorbit.db`
- execuções seguintes: reaproveitam o aprendizado persistido
- se o banco estiver corrompido, o sistema registra erro de inicialização; o reset é manual por backup/restauração ou exclusão do arquivo

## Segurança da API key

A chave da IA **não deve** ficar em `application.properties` com valor fixo.

A configuração correta é por variável de ambiente:

```bash
export QORBIT_AI_API_KEY="sua-chave"
export QORBIT_AI_ENDPOINT="https://seu-endpoint"
export QORBIT_AI_MODEL="seu-modelo"
export QORBIT_AI_ENABLED=true
```

Também é aceitável usar um ficheiro `.env` fora do repositório. O projeto deve manter `.gitignore` impedindo commit acidental de segredos.

## Relatórios de aprendizado

Gerar relatório textual:

```bash
java -jar qorbit-engine.jar --report
```

Gerar relatório JSON:

```bash
java -jar qorbit-engine.jar --report --json
```

O relatório inclui:

- taxa de sucesso por tipo de componente
- total de execuções, sucessos e falhas
- estratégias mais usadas e sua taxa de sucesso
- visão do cache recente

## Estratégia de Datepicker (Entrega 2.3)

A `DateInputExecutionStrategy` foi completamente reescrita para atender às exigências dos datepickers modernos.

### Fluxo de decisão

```text
Campo readonly?
  Sim -> Abre calendário diretamente
  Não -> Tenta sendKeys + validação
           Falhou? -> Abre calendário

Calendário aberto:
  Range? -> Resolve qual calendário usar (aria-controls ou posição)
  Navega ano (select / input numérico / year-view)
  Navega mês (next/prev até mês alvo)
  Seleciona dia (enabled only)
  Valida campo -> JS fallback se necessário
```

### Exceções específicas

| Exceção | Quando |
|---|---|
| `DatePickerCalendarNotOpenedException` | Calendário não ficou visível após 5s |
| `DatePickerDateNotFoundException` | Dia não encontrado no calendário visível |
| `DatePickerDateDisabledException` | Data encontrada mas marcada como desabilitada |

### Formatos de data suportados

`dd/MM/yyyy`, `d/M/yyyy`, `MM/dd/yyyy`, `yyyy-MM-dd`, `dd-MM-yyyy`, `yyyy/MM/dd`, `dd.MM.yyyy` e variantes sem zero à esquerda.

### Bibliotecas compatíveis

jQuery UI Datepicker, Flatpickr, React Datepicker, Angular Material Datepicker, Ant Design DatePicker, Pikaday, Air Datepicker, Bootstrap Datepicker e implementações customizadas com atributos ARIA.

### Logs estruturados

Cada passo emite log com `execId`, estratégia usada, navegação realizada e resultado:

```text
[datepicker][execId=42] Iniciando preenchimento — valor alvo: '15/08/2026'
[datepicker][execId=42] Campo readonly=false | Data parseada=2026-08-15
[datepicker][execId=42] estrategia=digitacao resultado=FALHA — acionando fallback visual
[datepicker][execId=42] estrategia=calendario alvo=15/8/2026
[datepicker][execId=42] navegacao=ano via=select valor=2026
[datepicker][execId=42] navegacao=mes resultado=OK mes=8 ano=2026
[datepicker][execId=42] Selecionando dia=15
[datepicker][execId=42] Dia 15 clicado
[datepicker][execId=42] estrategia=calendario resultado=SUCESSO
```

## Estratégias implementadas nesta entrega

- `TEXT_INPUT`: preenchimento com validação de valor aplicado
- `DATE_INPUT`: tentativa por digitação, fallback por JavaScript e abertura de datepicker
- `NATIVE_SELECT`: seleção por texto visível em `<select>` real
- `CUSTOM_COMBOBOX`: clique, typeahead e seleção contextual em dropdown customizado
- `CLICK`, `WAIT`, `NAVIGATE`, `VALIDATE`

## Roadmap posterior

- ampliar heurísticas de classificação por framework UI
- melhorar navegação de calendário visual para datepickers complexos
- heurísticas mais profundas para autocomplete assíncrono
- exportação avançada de relatórios
- painéis internos para análise de acurácia

## Observação importante

Esta entrega foi preparada para evoluir a base arquitetural e o comportamento central da engine. Mesmo com a implementação do aprendizado e da estratégia, componentes altamente customizados ainda podem exigir refinamento adicional das heurísticas de classificação e fallback.

## Hardening de runtime (Entrega 2.1)

O arranque agora prepara automaticamente a infraestrutura mínima do SQLite antes do Spring subir o DataSource:

- cria a pasta pai do banco quando ela não existir
- cria o arquivo `qorbit.db` na primeira execução
- respeita a variável de ambiente `QORBIT_DB_PATH`
- grava no log o caminho absoluto efetivamente usado

Exemplos:

```bash
# padrão
mvn spring-boot:run

# caminho personalizado
set QORBIT_DB_PATH=C:\meu-diretorio\qorbit.db
mvn spring-boot:run
```

Se o caminho informado não puder ser criado, o sistema falha cedo com mensagem explícita de infraestrutura, antes da inicialização do JPA.
