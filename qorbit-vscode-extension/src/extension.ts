import * as vscode from "vscode";
import { QorbitClient } from "./qorbitClient";

let client: QorbitClient | undefined;
let statusBarItem: vscode.StatusBarItem;
let pollTimer: ReturnType<typeof setInterval> | undefined;
let outputChannel: vscode.OutputChannel;

function getConfig() {
  const cfg = vscode.workspace.getConfiguration("qorbit");
  return {
    baseUrl: cfg.get<string>("baseUrl", "http://localhost:18080"),
    email: cfg.get<string>("email", "user@qorbit.local"),
    password: cfg.get<string>("password", "Qorbit@2025"),
  };
}

async function ensureAutenticado(): Promise<QorbitClient | undefined> {
  const { baseUrl, email, password } = getConfig();

  if (!client) {
    client = new QorbitClient({ baseUrl });
  }
  if (client.isAuthenticated()) {
    return client;
  }

  if (!email || !password) {
    vscode.window.showErrorMessage("Qorbit: configure qorbit.email e qorbit.password para iniciar a gravação.");
    return undefined;
  }

  const resultado = await client.login(email, password);
  if (!resultado.ok) {
    vscode.window.showErrorMessage(`Qorbit: falha no login — ${resultado.erro ?? "erro desconhecido"}`);
    return undefined;
  }
  outputChannel.appendLine(`[login] autenticado como ${email}`);
  return client;
}

function iniciarPolling(): void {
  pararPolling();
  pollTimer = setInterval(async () => {
    if (!client) {
      return;
    }
    const status = await client.status();
    if (status.erro) {
      return;
    }
    statusBarItem.text = `$(record) Qorbit: gravando (${status.totalSteps ?? 0} passos)`;
  }, 1000);
}

function pararPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function statusBarIdle(): void {
  statusBarItem.text = "$(record) Qorbit: Gravar teste";
  statusBarItem.command = "qorbit.iniciarGravacao";
}

async function comandoIniciarGravacao(): Promise<void> {
  const c = await ensureAutenticado();
  if (!c) {
    return;
  }

  const url = await vscode.window.showInputBox({
    prompt: "URL a gravar (ambiente de dev/staging — nunca produção)",
    placeHolder: "https://staging.empresa.com/login",
    ignoreFocusOut: true,
  });
  if (!url) {
    return;
  }

  const resultado = await c.iniciarGravacaoExtensao(url);
  if (resultado.erro) {
    vscode.window.showErrorMessage(`Qorbit: ${resultado.erro}`);
    return;
  }

  outputChannel.appendLine(`[iniciar-extensao] ${JSON.stringify(resultado)}`);

  // A captura roda no navegador real do usuário (extensão de navegador
  // Qorbit), não num Chrome controlado pelo servidor — por isso é a página-
  // ponte que abre, e não um painel de noVNC. O token resolve a URL alvo no
  // servidor (evita open-redirect via query param).
  const bridgeUrl = `${c.getBaseUrl()}/captura-publica/bridge?token=${encodeURIComponent(String(resultado.token ?? ""))}`;
  await vscode.env.openExternal(vscode.Uri.parse(bridgeUrl));

  iniciarPolling();
  statusBarItem.text = "$(record) Qorbit: gravando (0 passos)";
  statusBarItem.command = "qorbit.pararGravacao";
  vscode.window.showInformationMessage(
    "Qorbit: gravação iniciada no navegador. Instale a extensão Qorbit no navegador se ainda não tiver — clique na barra de status para parar e salvar.",
  );
}

async function comandoPararGravacao(): Promise<void> {
  if (!client) {
    return;
  }

  const nomeCaso = await vscode.window.showInputBox({ prompt: "Nome do caso de teste", ignoreFocusOut: true });
  if (nomeCaso === undefined) {
    return;
  }
  const modulo = await vscode.window.showInputBox({ prompt: "Módulo", ignoreFocusOut: true });
  if (modulo === undefined) {
    return;
  }

  const resultado = await client.pararGravacao(nomeCaso, modulo);
  pararPolling();
  statusBarIdle();

  if (resultado.erro) {
    vscode.window.showErrorMessage(`Qorbit: ${resultado.erro}`);
    return;
  }

  outputChannel.appendLine(`[parar] ${JSON.stringify(resultado)}`);
  vscode.window.showInformationMessage(
    `Qorbit: ${resultado.mensagem} (caso #${resultado.casoId}). Abra o Qorbit no navegador para revisar/exportar o código gerado.`,
  );
}

async function comandoDescartarGravacao(): Promise<void> {
  if (!client) {
    return;
  }
  const resultado = await client.descartarGravacao();
  pararPolling();
  statusBarIdle();
  outputChannel.appendLine(`[descartar] ${JSON.stringify(resultado)}`);
  vscode.window.showInformationMessage("Qorbit: gravação descartada.");
}

export function activate(context: vscode.ExtensionContext): void {
  outputChannel = vscode.window.createOutputChannel("Qorbit");

  statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  statusBarIdle();
  statusBarItem.show();

  context.subscriptions.push(
    statusBarItem,
    outputChannel,
    vscode.commands.registerCommand("qorbit.iniciarGravacao", comandoIniciarGravacao),
    vscode.commands.registerCommand("qorbit.pararGravacao", comandoPararGravacao),
    vscode.commands.registerCommand("qorbit.descartarGravacao", comandoDescartarGravacao),
  );
}

export function deactivate(): void {
  pararPolling();
}
