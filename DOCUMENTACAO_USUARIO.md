# Qorbit Engine — Documentação do Usuário

**Versão 2.5** | Plataforma de Automação de Testes com Auto-Cura Inteligente

---

## Sumário

1. [O que é o Qorbit?](#1-o-que-é-o-qorbit)
2. [Primeiros Passos](#2-primeiros-passos)
3. [Navegação](#3-navegação)
4. [Dashboard](#4-dashboard)
5. [Elementos (Biblioteca POM)](#5-elementos-biblioteca-pom)
6. [Casos de Teste](#6-casos-de-teste)
7. [Suites de Teste](#7-suites-de-teste)
8. [Gravar Teste](#8-gravar-teste)
9. [Nova Execução](#9-nova-execução)
10. [Execuções](#10-execuções)
11. [Evidências](#11-evidências)
12. [Gerar Código](#12-gerar-código)
13. [Plugin de IA](#13-plugin-de-ia)
14. [Conceitos Fundamentais](#14-conceitos-fundamentais)
15. [Fluxos de Trabalho Recomendados](#15-fluxos-de-trabalho-recomendados)
16. [Perguntas Frequentes](#16-perguntas-frequentes)

---

## 1. O que é o Qorbit?

O **Qorbit Engine** é uma plataforma de automação de testes de aplicações web. Ele permite que equipes de QA e desenvolvedores criem, organizem e executem testes automatizados sem precisar escrever código manualmente — embora também ofereça exportação de código para quem precisar.

### Principais diferenciais

| Recurso | Descrição |
|---|---|
| **Auto-Cura Inteligente** | Quando um elemento muda na página, o Qorbit tenta se adaptar automaticamente em vez de falhar |
| **Gravação de Testes** | Registre suas ações no navegador e gere casos de teste automaticamente |
| **Suporte a Componentes Complexos** | Lida com datepickers, comboboxes, autocompletar, iframes e Shadow DOM |
| **Evidências Visuais** | Capturas de tela automáticas em cada passo da execução |
| **Exportação de Código** | Gera projetos Selenium + Cucumber prontos para uso em CI/CD |
| **Plugin de IA** | Integração com modelos de IA para diagnóstico e melhoria de testes |

---

## 2. Primeiros Passos

### Acessando a aplicação

Após iniciar o servidor, abra o navegador e acesse:

```
http://localhost:18080
```

Você será direcionado ao **Dashboard**, a página principal do Qorbit.

### Fluxo básico de uso

```
Capturar elementos   →   Criar casos de teste   →   Organizar em suites   →   Executar   →   Analisar resultados
```

Ou, de forma mais rápida:

```
Gravar teste   →   Organizar em suites   →   Executar   →   Analisar resultados
```

---

## 3. Navegação

O menu lateral está sempre visível e organiza todas as funcionalidades:

```
Principal
  ├── Dashboard           — Visão geral e métricas em tempo real
  └── Nova Execução       — Iniciar uma nova execução de testes

Biblioteca
  ├── Elementos (POM)     — Gerenciar elementos da interface capturados
  ├── Casos de Teste      — Criar e gerenciar cenários de teste
  └── Suites              — Agrupar casos de teste para execução em lote

Gravar
  └── Gravar Teste        — Capturar ações no navegador e gerar testes

Resultados
  ├── Execuções           — Histórico de todas as execuções realizadas
  ├── Evidências          — Screenshots capturadas durante os testes
  └── Gerar Código        — Exportar testes como projeto Selenium/Cucumber

Inteligência
  └── Plugin de IA        — Configurar e usar recursos de inteligência artificial
```

---

## 4. Dashboard

O Dashboard é a página inicial e oferece uma visão geral do estado atual dos seus testes.

### Métricas exibidas

- **Total de elementos capturados** — quantos elementos estão na biblioteca
- **Casos de teste criados** — total de cenários cadastrados
- **Percentual de sucesso** — taxa de sucesso nas execuções realizadas

### Execução em andamento

Quando um teste está sendo executado, o Dashboard exibe em tempo real:

- Nome do caso de teste em execução
- Número do passo atual e passo total
- Descrição do que está sendo executado
- Barra de progresso visual

### Histórico de execuções

A tabela no Dashboard lista as execuções mais recentes com:

- Data e hora de início
- URL alvo
- Browser utilizado
- Passos aprovados e reprovados
- Status geral (SUCESSO, FALHA, EM_ANDAMENTO)

---

## 5. Elementos (Biblioteca POM)

A **Biblioteca de Elementos** (também chamada de POM — Page Object Model) é onde ficam registrados todos os elementos de interface que seus testes podem interagir.

### O que é um elemento?

Um elemento representa um componente da página web que você quer automatizar — um botão, campo de texto, dropdown, link, etc. Cada elemento tem:

| Campo | Descrição |
|---|---|
| **Nome lógico** | Nome amigável para identificar o elemento (ex: "Botão Salvar") |
| **Página** | A qual página ou módulo o elemento pertence |
| **Tipo de seletor** | Como o Selenium vai localizar o elemento (CSS, XPATH, ID, NAME, LINK_TEXT) |
| **Seletor técnico** | O valor do seletor (ex: `#btn-salvar`, `//button[@id='save']`) |
| **Descrição** | Texto explicativo opcional |
| **Status** | ATIVO ou PENDENTE |

### Capturando elementos automaticamente

1. Clique em **"Capturar elementos de uma URL"**
2. Informe a URL da página que deseja analisar
3. O Qorbit irá carregar a página e identificar elementos interativos automaticamente
4. Revise e confirme os elementos encontrados

### Adicionando elementos manualmente

1. Clique em **"Novo Elemento"**
2. Preencha o nome lógico, selecione o tipo de seletor e informe o valor
3. Associe a uma página/módulo
4. Salve

### Filtrando elementos

Use os filtros no topo da lista para encontrar elementos por:
- Nome ou trecho do seletor (busca textual)
- Página / módulo
- Status (ATIVO / PENDENTE)

### Editando e excluindo

Cada elemento na lista possui botões de edição (ícone de lápis) e exclusão (ícone de lixeira). Elementos vinculados a casos de teste ativos devem ser editados com cuidado.

---

## 6. Casos de Teste

Um **Caso de Teste** é um cenário composto por uma sequência de passos que simula uma ação do usuário na aplicação.

### Estrutura de um caso de teste

| Campo | Descrição |
|---|---|
| **Código** | Identificador único gerado automaticamente |
| **Nome** | Nome descritivo do cenário |
| **Módulo** | Agrupamento funcional (ex: Login, Cadastro, Relatórios) |
| **URL Alvo** | URL inicial para a execução do teste |
| **Status** | ATIVO ou INATIVO |
| **Passos** | Sequência de ações a executar |

### Ações disponíveis nos passos

| Ação | O que faz |
|---|---|
| **NAVEGAR** | Abre uma URL no browser |
| **CLICAR** | Clica em um elemento da página |
| **PREENCHER** | Digita um texto em um campo |
| **SELECIONAR** | Escolhe uma opção em um select/combobox |
| **VALIDAR** | Verifica se um elemento contém determinado texto |
| **LIMPAR** | Apaga o conteúdo de um campo |
| **WAIT** | Aguarda um tempo determinado (em segundos) |
| **OPEN** | Abre o navegador na URL configurada |

### Criando um caso de teste

1. Acesse **Casos de Teste** no menu lateral
2. Clique em **"Novo Caso de Teste"**
3. Preencha nome, módulo e URL alvo
4. Adicione passos clicando em **"+ Adicionar Passo"**
5. Para cada passo, selecione a ação, o elemento (da biblioteca) e o valor quando necessário
6. Observe a descrição em **Gherkin** sendo gerada automaticamente
7. Clique em **"Salvar"**

### Gherkin automático

O Qorbit gera automaticamente a descrição de cada passo no formato **Gherkin** (linguagem usada no Cucumber), facilitando a comunicação entre técnicos e não-técnicos:

```gherkin
Dado que navego para "https://meusite.com/login"
Quando preencho o campo "Campo Email" com "usuario@email.com"
E preencho o campo "Campo Senha" com "senha123"
E clico no elemento "Botão Entrar"
Então valido que "Mensagem Boas Vindas" contém "Bem-vindo"
```

### Editando e excluindo passos

Na tela de edição, cada passo pode ser reordenado, editado ou removido individualmente antes de salvar o caso de teste.

---

## 7. Suites de Teste

Uma **Suite de Teste** é um conjunto de casos de teste agrupados para execução em lote. Ideal para organizar testes por funcionalidade, sprint ou ambiente.

### Criando uma suite

1. Acesse **Suites** no menu lateral
2. Clique em **"Nova Suite"**
3. Informe um nome e descrição
4. Escolha uma cor para identificação visual
5. Adicione casos de teste à suite
6. Salve

### Cores disponíveis

As suites possuem identificação visual por cor:

- Verde
- Azul
- Âmbar
- Roxo
- Coral

### Executando uma suite

Na lista de suites, cada card exibe:
- Nome e descrição
- Quantidade de casos de teste vinculados
- Data e resultado da última execução

Para executar, clique no botão **"Executar"** no card da suite desejada. Você será direcionado para a tela de configuração de execução.

### Exportando uma suite

Cada suite pode ser exportada como arquivo `.zip` contendo todos os dados dos casos de teste vinculados. Útil para backup ou migração.

### Histórico por suite

Dentro de cada suite é possível visualizar o histórico completo de execuções realizadas com aquela suite, incluindo:
- Data/hora
- Percentual de sucesso
- Passos aprovados e reprovados

---

## 8. Gravar Teste

O **Gravador de Testes** permite criar casos de teste simplesmente usando a aplicação normalmente. O Qorbit registra suas ações e as converte em passos automaticamente.

### Como gravar um teste

1. Acesse **Gravar Teste** no menu
2. Informe a **URL** da página inicial do teste
3. Clique em **"Iniciar Gravação"**
4. O navegador será aberto na URL informada
5. Execute as ações que deseja automatizar:
   - Cliques em botões e links
   - Preenchimento de campos
   - Seleção em dropdowns
6. As ações aparecem em tempo real na lista de passos
7. Quando terminar, clique em **"Parar Gravação"**
8. Dê um nome ao caso de teste e escolha o módulo
9. Salve como **Caso de Teste**

### Durante a gravação

- Cada ação capturada aparece na lista com tipo de ação, elemento e valor
- É possível **excluir passos indesejados** clicando no ícone de remoção ao lado do passo
- Ações suportadas: cliques, preenchimento de texto, seleção em listas

### Dicas para uma boa gravação

- Faça o fluxo completo sem interrupções para capturar todos os passos necessários
- Evite ações desnecessárias, como cliques acidentais
- Após salvar, revise os passos na tela de Casos de Teste e ajuste se necessário

---

## 9. Nova Execução

A tela de **Nova Execução** permite configurar e iniciar a execução de casos de teste de forma individualizada ou em grupo.

### Configurações de execução

| Campo | Descrição |
|---|---|
| **URL Alvo** | Endereço base da aplicação a ser testada |
| **Browser** | Navegador a usar: Chrome, Firefox ou Edge |
| **Timeout** | Tempo máximo (em segundos) para aguardar cada elemento |
| **Autenticação** | Método de autenticação: NONE ou COOKIE |

### Selecionando casos de teste

Após preencher as configurações básicas:
1. Escolha os **casos de teste** que deseja executar
2. É possível selecionar múltiplos casos para execução sequencial
3. Clique em **"Executar agora"**

### Acompanhamento em tempo real

Após iniciar, você será redirecionado ao **Dashboard**, onde poderá acompanhar:
- Caso de teste sendo executado no momento
- Passo atual e total de passos
- Status de cada passo (aprovado/reprovado)
- Progresso geral

---

## 10. Execuções

A tela de **Execuções** exibe o histórico completo de todas as execuções realizadas no Qorbit.

### Informações exibidas por execução

| Campo | Descrição |
|---|---|
| **ID** | Identificador único da execução |
| **Data/Hora** | Quando a execução foi iniciada |
| **URL** | Endereço da aplicação testada |
| **Browser** | Navegador utilizado |
| **Passos** | Quantidade de passos aprovados vs. reprovados |
| **Sucesso %** | Taxa de sucesso calculada |
| **Status** | SUCESSO, FALHA ou EM_ANDAMENTO |
| **Duração** | Tempo total da execução |

### Filtrando execuções

Utilize os filtros disponíveis para encontrar execuções por:
- Status
- Data
- URL
- Browser

### Limpando execuções pendentes

Se houver execuções travadas com status **EM_ANDAMENTO**, use o botão **"Limpar pendentes"** para resetá-las.

### Detalhes da execução

Clique em uma execução para ver os detalhes de cada passo:
- Descrição do passo
- Status (aprovado/reprovado)
- Motivo da falha (quando houver)
- Link para evidências (screenshots)

---

## 11. Evidências

A tela de **Evidências** exibe as capturas de tela tiradas automaticamente durante as execuções.

### O que são as evidências?

A cada passo executado, o Qorbit captura uma screenshot da tela do navegador. Essas imagens servem como comprovação visual do que ocorreu durante o teste.

### Visualizando evidências

1. Acesse **Evidências** no menu
2. Use os filtros para localizar evidências por:
   - Execução específica
   - Status do passo (APROVADO / REPROVADO)
3. Clique em uma imagem para abrir em tamanho ampliado (lightbox)

### Informações de cada evidência

- Número e nome do passo
- Status (aprovado/reprovado)
- Motivo da falha (quando reprovado)
- Imagem da tela no momento do passo

### Baixando evidências

Clique em **"Baixar como ZIP"** para baixar todas as evidências de uma execução em um arquivo compactado. Útil para compartilhar com a equipe ou incluir em relatórios de bug.

---

## 12. Gerar Código

A funcionalidade de **Gerar Código** exporta seus testes do Qorbit como um projeto Selenium + Cucumber completo, pronto para ser integrado ao pipeline de CI/CD.

### O que é gerado

- **Arquivos `.feature`** — cenários de teste escritos em Gherkin
- **Step Definitions em Java** — implementação dos passos para o Selenium
- **Estrutura de projeto Maven** — `pom.xml` com todas as dependências
- **Pipeline CI/CD** — arquivo de configuração para GitHub Actions

### Como gerar o código

1. Acesse **Gerar Código** no menu
2. Selecione os **casos de teste** que deseja exportar
3. Configure as opções de geração (se disponíveis)
4. Clique em **"Gerar"**
5. Faça o download do projeto `.zip`

### Estrutura do projeto exportado

```
projeto-selenium/
├── src/
│   ├── test/
│   │   ├── java/
│   │   │   └── steps/
│   │   │       └── StepDefinitions.java
│   │   └── resources/
│   │       └── features/
│   │           └── testes.feature
├── .github/
│   └── workflows/
│       └── ci.yml
└── pom.xml
```

### Pré-requisitos para rodar o projeto exportado

- Java 11 ou superior
- Maven 3.6+
- Chrome/Firefox instalado com o driver correspondente

---

## 13. Plugin de IA

O **Plugin de IA** permite integrar o Qorbit com modelos de inteligência artificial externos para aprimorar a qualidade dos testes.

### Funcionalidades disponíveis

| Funcionalidade | Descrição |
|---|---|
| **Melhorar nomes de elementos** | A IA analisa seletores técnicos e sugere nomes lógicos mais descritivos |
| **Detectar abas/páginas** | A IA identifica automaticamente padrões de navegação na aplicação |
| **Diagnosticar falhas** | A IA explica em linguagem natural por que um teste falhou |

### Configurando o Plugin de IA

1. Acesse **Plugin de IA** no menu
2. Configure:
   - **Endpoint da API** — URL do modelo de IA (ex: API do Claude, OpenAI)
   - **Modelo** — nome do modelo a utilizar
   - **API Key** — configurada via variável de ambiente `QORBIT_AI_API_KEY`
3. Teste a conexão antes de usar

> **Segurança:** A chave de API nunca deve ser inserida diretamente no formulário. Configure a variável de ambiente `QORBIT_AI_API_KEY` no servidor antes de usar esta funcionalidade.

### Usando o diagnóstico de falhas

Após uma execução com falhas:
1. Acesse os detalhes da execução
2. Clique em **"Diagnosticar com IA"** no passo que falhou
3. O Qorbit envia o contexto para a IA e exibe uma explicação em português

---

## 14. Conceitos Fundamentais

### Elemento vs. Caso de Teste vs. Suite

```
Elemento        →   representa UM componente da página (botão, campo, etc.)
Caso de Teste   →   representa UM cenário de teste (sequência de passos)
Suite           →   representa um GRUPO de casos de teste
```

### Tipos de seletor

| Tipo | Exemplo | Quando usar |
|---|---|---|
| **ID** | `login-btn` | Quando o elemento tem `id` único |
| **CSS** | `#login-btn`, `.btn-primary` | Seleção flexível por classe ou atributo |
| **XPATH** | `//button[@type='submit']` | Quando CSS não é suficiente |
| **NAME** | `username` | Campos de formulário com atributo `name` |
| **LINK_TEXT** | `Entrar` | Links com texto específico |

### Status dos elementos

- **ATIVO** — elemento disponível para uso nos testes
- **PENDENTE** — elemento capturado mas ainda não validado

### Status das execuções

- **EM_ANDAMENTO** — execução em curso
- **SUCESSO** — todos os passos foram aprovados
- **FALHA** — um ou mais passos foram reprovados

### Auto-Cura (Self-Healing)

Quando um elemento não é encontrado pelo seletor primário, o Qorbit tenta estratégias alternativas automaticamente:

1. Tenta seletores alternativos conhecidos
2. Busca por similaridade de atributos
3. Usa estratégias específicas por tipo de componente (datepicker, combobox, etc.)
4. Registra o resultado para aprendizado futuro

Isso reduz a necessidade de manutenção constante dos testes quando a aplicação muda.

---

## 15. Fluxos de Trabalho Recomendados

### Fluxo 1 — Criar teste via gravação (mais rápido)

```
1. Gravar Teste
   → Informe a URL e inicie a gravação
   → Execute o fluxo normalmente no navegador
   → Pare a gravação e nomeie o teste

2. Suites
   → Crie uma suite e adicione o caso de teste gravado

3. Nova Execução (ou execute pela suite)
   → Configure browser e timeout
   → Execute e acompanhe no Dashboard

4. Evidências / Execuções
   → Revise os resultados e capturas de tela
```

### Fluxo 2 — Criar teste manualmente (mais controle)

```
1. Elementos
   → Capture elementos da URL alvo
   → Revise e ative os elementos necessários

2. Casos de Teste
   → Crie um novo caso de teste
   → Adicione passos selecionando ação + elemento + valor
   → Revise o Gherkin gerado

3. Suites
   → Agrupe casos de teste relacionados

4. Nova Execução
   → Execute e monitore

5. Evidências
   → Analise screenshots e diagnósticos de falha
```

### Fluxo 3 — Exportar para CI/CD

```
1. Certifique-se que os testes passam localmente

2. Gerar Código
   → Selecione os casos de teste desejados
   → Baixe o projeto ZIP

3. Extraia e configure o projeto
   → Ajuste as URLs de ambiente no pom.xml
   → Adicione as credenciais necessárias

4. Integre o GitHub Actions
   → Faça commit do projeto no repositório
   → O pipeline executa automaticamente a cada push
```

---

## 16. Perguntas Frequentes

**Por que meu teste falhou mesmo com os seletores corretos?**
Verifique se o elemento estava visível no momento da execução. Elementos dentro de iframes ou com carregamento assíncrono podem precisar de um tempo de espera (passo WAIT) antes da interação.

**O que fazer quando um elemento não é encontrado?**
1. Verifique se o seletor ainda está válido na página atual
2. Inspecione o elemento no navegador e atualize o seletor
3. Considere usar XPATH como alternativa ao CSS se a estrutura da página mudou
4. Ative o Plugin de IA para diagnóstico automático

**Como organizar testes por ambiente (homologação, produção)?**
Crie suites separadas para cada ambiente e configure a URL alvo adequada na tela de Nova Execução. Assim, o mesmo conjunto de casos de teste pode ser executado em ambientes diferentes.

**Posso executar o Qorbit em modo headless (sem abrir o navegador)?**
Sim. Configure a opção `headless=true` nas propriedades da aplicação. Útil para execuções em servidores CI/CD sem interface gráfica.

**Como faço backup dos meus testes?**
Exporte as suites desejadas como arquivo ZIP pela funcionalidade de exportação na tela de Suites. Para backup completo, faça cópia do arquivo `qorbit.db` (banco de dados SQLite).

**O Plugin de IA é obrigatório?**
Não. Todas as funcionalidades principais do Qorbit funcionam sem o Plugin de IA. A IA é um recurso adicional para melhorar a qualidade e facilitar o diagnóstico.

**Qual browser é recomendado para execução?**
O **Chrome** é o browser mais testado e recomendado. Firefox e Edge também são suportados, mas podem ter comportamentos ligeiramente diferentes em componentes complexos como datepickers.

**Por que alguns passos aparecem como "EM_ANDAMENTO" após uma falha?**
Quando uma execução é interrompida inesperadamente, alguns registros podem ficar com status inconsistente. Use o botão **"Limpar pendentes"** na tela de Execuções para corrigir isso.

---

*Documentação gerada para Qorbit Engine v2.5*
