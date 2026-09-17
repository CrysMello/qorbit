import * as vscode from "vscode";
import { QorbitClient } from "./qorbitClient";

let client: QorbitClient | undefined;
let statusBarItem: vscode.StatusBarItem;
let pollTimer: ReturnType<typeof setInterval> | undefined;
let novncPanel: vscode.WebviewPanel | undefined;
let outputChannel: vscode.OutputChannel;

function getConfig() {
  const cfg = vscode.workspace.getConfiguration("qorbit");
  return {
    baseUrl: cfg.get<string>("baseUrl", "http://localhost:8080"),
    novncUrl: cfg.get<string>("novncUrl", "http://localhost:6080/vnc_lite.html?autoconnect=true&resize=scale"),
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

function abrirPainelNovnc(): void {
  const { novncUrl } = getConfig();
  novncPanel = vscode.window.createWebviewPanel(
    "qorbitNovnc",
    "Qorbit — Gravação ao vivo",
    vscode.ViewColumn.Beside,
    { enableScripts: true, retainContextWhenHidden: true },
  );
  novncPanel.webview.html = `<!doctype html>
<html><body style="margin:0">
<iframe src="${novncUrl}" style="border:0;width:100%;height:100vh"></iframe>
</body></html>`;
  novncPanel.onDidDispose(() => {
    novncPanel = undefined;
  });
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

  const resultado = await c.iniciarGravacao(url);
  if (resultado.erro) {
    vscode.window.showErrorMessage(`Qorbit: ${resultado.erro}`);
    return;
  }

  outputChannel.appendLine(`[iniciar] ${JSON.stringify(resultado)}`);
  abrirPainelNovnc();
  iniciarPolling();
  statusBarItem.text = "$(record) Qorbit: gravando (0 passos)";
  statusBarItem.command = "qorbit.pararGravacao";
  vscode.window.showInformationMessage("Qorbit: gravação iniciada. Clique na barra de status para parar e salvar.");
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
  novncPanel?.dispose();
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
  novncPanel?.dispose();
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
