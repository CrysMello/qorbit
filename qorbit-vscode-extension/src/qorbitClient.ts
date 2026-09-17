import http from "node:http";
import https from "node:https";
import { URL, URLSearchParams } from "node:url";

/**
 * Cliente HTTP para o backend do Qorbit Engine (endpoints /auth/login e
 * /api/gravacao/*). Não importa nada de "vscode" de propósito — assim dá pra
 * testar essa lógica com um servidor mock, sem precisar abrir o VSCode.
 */

export interface QorbitClientOptions {
  baseUrl: string;
}

export interface ApiPayload {
  [key: string]: unknown;
}

interface RequestOptions {
  method: string;
  path: string;
  headers?: Record<string, string>;
  body?: string;
}

interface RawResponse {
  status: number;
  headers: http.IncomingHttpHeaders;
  body: string;
}

/** Cookie jar simples: guarda os pares nome=valor vistos em Set-Cookie e reenvia em cada request. */
class CookieJar {
  private cookies = new Map<string, string>();

  absorb(setCookieHeaders: string[] | undefined): void {
    if (!setCookieHeaders) {
      return;
    }
    for (const raw of setCookieHeaders) {
      const pair = raw.split(";")[0];
      const idx = pair.indexOf("=");
      if (idx === -1) {
        continue;
      }
      const name = pair.slice(0, idx).trim();
      const value = pair.slice(idx + 1).trim();
      this.cookies.set(name, value);
    }
  }

  header(): string {
    return Array.from(this.cookies.entries())
      .map(([name, value]) => `${name}=${value}`)
      .join("; ");
  }
}

export class QorbitClient {
  private baseUrl: URL;
  private jar = new CookieJar();
  private authenticated = false;

  constructor(options: QorbitClientOptions) {
    this.baseUrl = new URL(options.baseUrl);
  }

  isAuthenticated(): boolean {
    return this.authenticated;
  }

  /** Origem do backend (sem path), usada para montar a URL da página-ponte /captura-publica/bridge. */
  getBaseUrl(): string {
    return this.baseUrl.origin;
  }

  /** Faz o login tradicional do Spring Security: pega o token CSRF na página, depois envia o form. */
  async login(email: string, senha: string): Promise<{ ok: boolean; erro?: string }> {
    const loginPage = await this.request({ method: "GET", path: "/auth/login" });
    const csrf = QorbitClient.extrairCsrf(loginPage.body);
    if (!csrf) {
      return { ok: false, erro: "Não foi possível obter o token CSRF da página de login." };
    }

    const form = new URLSearchParams({ email, password: senha, _csrf: csrf }).toString();
    const resultado = await this.request({
      method: "POST",
      path: "/auth/login",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
    });

    // Spring Security responde com redirect (3xx) tanto no sucesso quanto na
    // falha — a diferença é o destino: falha volta para /auth/login?error.
    const location = String(resultado.headers.location ?? "");
    const sucesso = resultado.status >= 300 && resultado.status < 400 && !location.includes("/auth/login");
    this.authenticated = sucesso;
    return sucesso ? { ok: true } : { ok: false, erro: "Credenciais inválidas." };
  }

  async iniciarGravacao(url: string): Promise<ApiPayload> {
    return this.postJson("/api/gravacao/iniciar", { url });
  }

  /**
   * Inicia a gravação sem Selenium: o backend só valida a URL e devolve um
   * token de sessão — a captura em si roda no navegador real do usuário via
   * a extensão de navegador (browser-extension/), que reporta os eventos a
   * /captura-publica/evento. Ver CapturaPublicaController.
   */
  async iniciarGravacaoExtensao(url: string): Promise<ApiPayload> {
    return this.postJson("/api/gravacao/iniciar-extensao", { url });
  }

  async registrarStep(evento: ApiPayload): Promise<ApiPayload> {
    return this.postJson("/api/gravacao/step", evento);
  }

  async status(): Promise<ApiPayload> {
    const res = await this.request({ method: "GET", path: "/api/gravacao/status" });
    return QorbitClient.parseJson(res);
  }

  async pararGravacao(nomeCaso: string, modulo: string): Promise<ApiPayload> {
    return this.postJson("/api/gravacao/parar", { nomeCaso, modulo });
  }

  async descartarGravacao(): Promise<ApiPayload> {
    return this.postJson("/api/gravacao/descartar", {});
  }

  private async postJson(path: string, payload: ApiPayload): Promise<ApiPayload> {
    const res = await this.request({
      method: "POST",
      path,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return QorbitClient.parseJson(res);
  }

  private static parseJson(res: RawResponse): ApiPayload {
    try {
      return JSON.parse(res.body || "{}") as ApiPayload;
    } catch {
      return { erro: `Resposta inesperada do servidor (status ${res.status}).` };
    }
  }

  private static extrairCsrf(html: string): string | undefined {
    const match = html.match(/name="_csrf"\s+value="([^"]+)"/);
    return match?.[1];
  }

  private request(opts: RequestOptions): Promise<RawResponse> {
    return new Promise((resolve, reject) => {
      const client = this.baseUrl.protocol === "https:" ? https : http;
      const headers: Record<string, string> = { ...opts.headers };
      const cookieHeader = this.jar.header();
      if (cookieHeader) {
        headers["Cookie"] = cookieHeader;
      }
      if (opts.body) {
        headers["Content-Length"] = Buffer.byteLength(opts.body).toString();
      }

      const req = client.request(
        {
          protocol: this.baseUrl.protocol,
          hostname: this.baseUrl.hostname,
          port: this.baseUrl.port || (this.baseUrl.protocol === "https:" ? 443 : 80),
          path: opts.path,
          method: opts.method,
          headers,
        },
        (res) => {
          const chunks: Buffer[] = [];
          res.on("data", (chunk: Buffer) => chunks.push(chunk));
          res.on("end", () => {
            this.jar.absorb(res.headers["set-cookie"]);
            resolve({
              status: res.statusCode ?? 0,
              headers: res.headers,
              body: Buffer.concat(chunks).toString("utf8"),
            });
          });
        },
      );
      req.on("error", reject);
      if (opts.body) {
        req.write(opts.body);
      }
      req.end();
    });
  }
}
