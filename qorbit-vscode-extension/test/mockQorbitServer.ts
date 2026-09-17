import http from "node:http";
import { URLSearchParams } from "node:url";

/**
 * Servidor mock que reproduz o contrato real do backend Qorbit (mensagens de
 * erro e formato de resposta copiados de GravacaoController/GravacaoService e
 * do fluxo de login do SecurityConfig), para validar o QorbitClient sem
 * precisar do Spring Boot rodando.
 */

const CSRF_TOKEN = "mock-csrf-token";
const PRE_AUTH_COOKIE = "JSESSIONID=preauth-session";
const AUTH_COOKIE = "JSESSIONID=authenticated-session";
export const CREDENCIAL_VALIDA = { email: "qa@empresa.com", senha: "Senha123!" };

interface StepRegistrado {
  numero: number;
  acao: string;
  elemento: string;
  valor: string;
  gherkin: string;
}

interface EstadoGravacao {
  gravando: boolean;
  url: string;
  steps: StepRegistrado[];
}

function readBody(req: http.IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function isAuthenticated(req: http.IncomingMessage): boolean {
  const cookie = req.headers.cookie ?? "";
  return cookie.includes(AUTH_COOKIE);
}

function sendJson(res: http.ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

export function createMockQorbitServer(): http.Server {
  const estado: EstadoGravacao = { gravando: false, url: "", steps: [] };

  return http.createServer(async (req, res) => {
    const url = new URL(req.url ?? "/", "http://localhost");

    // ── /auth/login (mesmo fluxo do Spring Security form login) ──────────
    if (req.method === "GET" && url.pathname === "/auth/login") {
      res.writeHead(200, {
        "Content-Type": "text/html",
        "Set-Cookie": `${PRE_AUTH_COOKIE}; Path=/; HttpOnly`,
      });
      res.end(`<html><body><form>
        <input type="hidden" name="_csrf" value="${CSRF_TOKEN}"/>
      </form></body></html>`);
      return;
    }

    if (req.method === "POST" && url.pathname === "/auth/login") {
      const params = new URLSearchParams(await readBody(req));
      const csrfRecebido = params.get("_csrf");
      const cookieRecebido = req.headers.cookie ?? "";

      if (csrfRecebido !== CSRF_TOKEN || !cookieRecebido.includes(PRE_AUTH_COOKIE)) {
        res.writeHead(403, { "Content-Type": "text/plain" });
        res.end("CSRF inválido ou ausente");
        return;
      }

      const emailOk = params.get("email") === CREDENCIAL_VALIDA.email;
      const senhaOk = params.get("password") === CREDENCIAL_VALIDA.senha;
      if (emailOk && senhaOk) {
        res.writeHead(302, { Location: "/", "Set-Cookie": `${AUTH_COOKIE}; Path=/; HttpOnly` });
      } else {
        res.writeHead(302, { Location: "/auth/login?error" });
      }
      res.end();
      return;
    }

    // ── /api/** exige sessão autenticada (equivalente ao SecurityConfig) ──
    if (url.pathname.startsWith("/api/") && !isAuthenticated(req)) {
      sendJson(res, 401, { erro: "Não autenticado" });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/gravacao/iniciar") {
      const body = JSON.parse((await readBody(req)) || "{}");
      if (estado.gravando) {
        sendJson(res, 400, { erro: "Já existe uma gravação em andamento. Pare primeiro." });
        return;
      }
      if (!body.url) {
        sendJson(res, 400, { erro: "URL é obrigatória" });
        return;
      }
      estado.gravando = true;
      estado.url = body.url;
      estado.steps = [];
      sendJson(res, 200, { mensagem: "Gravação iniciada", url: body.url });
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/gravacao/status") {
      sendJson(res, 200, {
        gravando: estado.gravando,
        url: estado.url,
        totalSteps: estado.steps.length,
        steps: estado.steps,
      });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/gravacao/step") {
      const body = JSON.parse((await readBody(req)) || "{}");
      const numero = estado.steps.length + 1;
      estado.steps.push({
        numero,
        acao: body.acao ?? "",
        elemento: body.elemento ?? "",
        valor: body.valor ?? "",
        gherkin: body.gherkin ?? "",
      });
      sendJson(res, 200, { ok: true, numero });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/gravacao/parar") {
      const body = JSON.parse((await readBody(req)) || "{}");
      estado.gravando = false;
      if (estado.steps.length === 0) {
        sendJson(res, 400, { erro: "Nenhum step gravado. Interaja com a aplicação antes de parar." });
        return;
      }
      const totalSteps = estado.steps.length;
      estado.steps = [];
      sendJson(res, 200, {
        mensagem: `${totalSteps} steps salvos com sucesso`,
        casoId: 1,
        totalSteps,
        totalElementos: totalSteps,
        _nomeCasoRecebido: body.nomeCaso,
      });
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/gravacao/descartar") {
      estado.gravando = false;
      estado.steps = [];
      sendJson(res, 200, { mensagem: "Gravação descartada" });
      return;
    }

    sendJson(res, 404, { erro: "not found" });
  });
}
