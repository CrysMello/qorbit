# Qorbit Recorder (extensão VSCode)

Cliente de VSCode para o fluxo de gravação do Qorbit Engine (`/api/gravacao/*`)
— grava, acompanha e salva um teste web sem sair do editor.

## O que instalar para rodar (checklist, na ordem)

A extensão em si só precisa do VSCode. Mas ela é um controle remoto — sem um
backend Qorbit rodando em algum lugar, os comandos não têm pra quem ligar (ver
seção "Limitações" mais abaixo). Pra rodar esse backend via Dev Container
(recomendado — não precisa instalar Java/Maven/Chrome à mão), a ordem é:

### 1. WSL2 — só no Windows, pré-requisito do Docker Desktop

O Docker Desktop no Windows roda em cima do WSL2 (um Linux de verdade dentro
do Windows) — não é opcional pra quem está nesse SO.

1. Abra o **PowerShell como Administrador**
2. Rode:
   ```powershell
   wsl --install
   ```
   Isso habilita os recursos do Windows necessários, instala o WSL2 e baixa
   uma distro padrão (Ubuntu) — não precisa mexer em nada dentro dela depois.
3. **Reinicie o computador** quando for pedido
4. Confirme que ficou na versão 2:
   ```powershell
   wsl --status
   ```
   Se aparecer "Versão padrão: 1", rode `wsl --set-default-version 2`.

*(Se já tiver WSL2 instalado — comum em Windows 10/11 atualizados — pule este passo.)*

### 2. Docker Desktop

1. Baixe em https://www.docker.com/products/docker-desktop/ e instale
2. O instalador detecta o WSL2 sozinho e já configura o "WSL 2 based engine"
   (opção padrão, não precisa mexer)
3. Abra o Docker Desktop uma vez e espere o ícone da baleia ficar "Running"
4. Opcional (recomendado): Settings → General → **"Start Docker Desktop when
   you log in"** — evita ter que abrir manualmente toda vez (trade-off:
   consome RAM/CPU em segundo plano mesmo sem usar)

### 3. Extensão "Dev Containers" no VSCode

`Ctrl+Shift+X` → busque **Dev Containers** (`ms-vscode-remote.remote-containers`,
da Microsoft) → Instalar.

### 4. Extensão Qorbit Recorder (esta aqui)

Instale o `.vsix` já gerado (`qorbit-recorder-0.1.0.vsix`, ver "Build e
empacotamento" abaixo) via `Ctrl+Shift+P` → **Extensions: Install from
VSIX...**. Não depende dos itens 1-3 pra instalar, só pra funcionar de ponta
a ponta.

### 5. Abrir o projeto no Dev Container

1. Clone o repo, ou use `Ctrl+Shift+P` → **Dev Containers: Clone Repository
   in Container Volume** direto da URL do GitHub
2. VSCode pergunta **"Reopen in Container?"** → confirme
3. Primeira vez demora alguns minutos (baixa a imagem, instala Java, Maven,
   Chrome, Xvfb, noVNC); da segunda vez em diante abre em segundos

## Extensão de navegador (obrigatória para gravar)

A captura roda no seu navegador de verdade, não num Chrome controlado pelo
servidor — por isso, além desta extensão de VSCode, é preciso instalar a
extensão de navegador do Qorbit (`browser-extension/` na raiz do repo) como
extensão "unpacked":

- **Chrome/Edge**: `chrome://extensions` → ative "Modo do desenvolvedor" →
  "Carregar sem compactação" → selecione a pasta `browser-extension/`.

Sem ela instalada, `Qorbit: Gravar novo teste` ainda abre o navegador na
página-ponte do Qorbit, mas nenhum clique/preenchimento é capturado.

## Comandos

| Comando | O que faz |
|---|---|
| `Qorbit: Gravar novo teste` | Loga (se preciso), pede a URL, chama `/api/gravacao/iniciar-extensao` e abre seu navegador padrão na página-ponte do Qorbit, que repassa a sessão para a extensão de navegador e navega até a URL alvo |
| `Qorbit: Parar e salvar` | Pede nome do caso/módulo, chama `/api/gravacao/parar` |
| `Qorbit: Descartar gravação` | Chama `/api/gravacao/descartar` |

Também aparecem na barra de status (canto inferior esquerdo).

## Configuração (`Ctrl+,` → busque "qorbit")

- `qorbit.baseUrl` — URL do backend (padrão `http://localhost:18080`)
- `qorbit.email` — padrão `user@qorbit.local`
- `qorbit.password` — padrão `Qorbit@2025` para uso local/controlado; não use essa configuração em produção

Com os valores padrão, a extensão faz login automaticamente e não solicita
usuário ou senha. O backend precisa estar configurado com a mesma conta.

## Build e empacotamento

```bash
npm install
npm run compile        # gera out/extension.js
npx @vscode/vsce package   # gera qorbit-recorder-0.1.0.vsix
```

Instale o `.vsix` gerado via `code --install-extension qorbit-recorder-0.1.0.vsix`, ou
pelo menu "Extensions → ... → Install from VSIX" do próprio VSCode.

## Testes

```bash
npm test
```

Roda `test/e2e.test.ts` contra um servidor mock (`test/mockQorbitServer.ts`) que
reproduz o contrato real dos endpoints (mensagens de erro e formato de resposta
copiados de `GravacaoController`/`GravacaoService`), sem precisar do Spring
Boot rodando. Usa o suporte nativo de TypeScript do Node (`--experimental-strip-types`),
sem dependências externas de teste.

## Limitações desta primeira versão (o que ficou de fora de propósito)

- **Login não é persistido**: a sessão vive só enquanto a janela do VSCode
  está aberta; fechar e abrir o VSCode pede login de novo. Persistir via
  `SecretStorage` é uma extensão natural, não incluída aqui para manter o
  escopo mínimo.
- **`/api/gravacao/parar` não devolve código Java pronto** — só salva o caso
  gravado no banco do Qorbit e retorna `casoId`/`totalSteps`. A geração do
  projeto de teste exportável é outro fluxo (`GeradorCodigoService.gerarZip`,
  um ZIP de projeto completo) e não foi ligado à extensão nesta versão; hoje
  ela só informa o `casoId` salvo.
- **Auto-instalação da extensão no Dev Container**: o `devcontainer.json` não
  lista `qorbit-recorder` em `customizations.vscode.extensions` porque esse
  campo só funciona para extensões publicadas em um marketplace. Para
  instalar automaticamente ao criar o container, adicione ao
  `postCreateCommand` algo como `code --install-extension /caminho/para/qorbit-recorder-0.1.0.vsix`,
  gerando o `.vsix` como parte do próprio Dockerfile ou copiando um já
  empacotado para dentro do repositório.
