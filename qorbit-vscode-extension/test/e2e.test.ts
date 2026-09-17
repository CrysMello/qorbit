import test from "node:test";
import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import type http from "node:http";
import { createMockQorbitServer, CREDENCIAL_VALIDA } from "./mockQorbitServer.ts";
import { QorbitClient } from "../src/qorbitClient.ts";

async function withServer(fn: (baseUrl: string) => Promise<void>): Promise<void> {
  const server: http.Server = createMockQorbitServer();
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address() as AddressInfo;
  try {
    await fn(`http://127.0.0.1:${port}`);
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

test("fluxo completo: login -> iniciar -> steps -> status -> parar", async () => {
  await withServer(async (baseUrl) => {
    const client = new QorbitClient({ baseUrl });

    assert.equal(client.isAuthenticated(), false);
    const login = await client.login(CREDENCIAL_VALIDA.email, CREDENCIAL_VALIDA.senha);
    assert.equal(login.ok, true);
    assert.equal(client.isAuthenticated(), true);

    const iniciar = await client.iniciarGravacao("https://staging.empresa.com/login");
    assert.equal(iniciar.mensagem, "Gravação iniciada");

    await client.registrarStep({ acao: "click", elemento: "botaoEntrar" });
    await client.registrarStep({ acao: "type", elemento: "campoEmail", valor: "teste@empresa.com" });

    const status = await client.status();
    assert.equal(status.gravando, true);
    assert.equal(status.totalSteps, 2);

    const parar = await client.pararGravacao("Login válido", "Autenticação");
    assert.equal(parar.totalSteps, 2);
    assert.equal(typeof parar.casoId, "number");
    assert.equal(parar.mensagem, "2 steps salvos com sucesso");

    const statusFinal = await client.status();
    assert.equal(statusFinal.gravando, false);
    assert.equal(statusFinal.totalSteps, 0);
  });
});

test("login falha com credencial inválida", async () => {
  await withServer(async (baseUrl) => {
    const client = new QorbitClient({ baseUrl });
    const login = await client.login("errado@empresa.com", "senhaErrada");
    assert.equal(login.ok, false);
    assert.equal(client.isAuthenticated(), false);
  });
});

test("erro: iniciar segunda gravação enquanto a primeira está ativa", async () => {
  await withServer(async (baseUrl) => {
    const client = new QorbitClient({ baseUrl });
    await client.login(CREDENCIAL_VALIDA.email, CREDENCIAL_VALIDA.senha);
    await client.iniciarGravacao("https://staging.empresa.com/a");
    const segunda = await client.iniciarGravacao("https://staging.empresa.com/b");
    assert.equal(segunda.erro, "Já existe uma gravação em andamento. Pare primeiro.");
  });
});

test("erro: parar sem nenhum step gravado", async () => {
  await withServer(async (baseUrl) => {
    const client = new QorbitClient({ baseUrl });
    await client.login(CREDENCIAL_VALIDA.email, CREDENCIAL_VALIDA.senha);
    await client.iniciarGravacao("https://staging.empresa.com/a");
    const parar = await client.pararGravacao("Caso vazio", "Modulo");
    assert.equal(parar.erro, "Nenhum step gravado. Interaja com a aplicação antes de parar.");
  });
});

test("erro: chamadas na api sem login prévio", async () => {
  await withServer(async (baseUrl) => {
    const client = new QorbitClient({ baseUrl });
    const iniciar = await client.iniciarGravacao("https://staging.empresa.com/a");
    assert.equal(iniciar.erro, "Não autenticado");
  });
});

test("descartar limpa o estado da gravação", async () => {
  await withServer(async (baseUrl) => {
    const client = new QorbitClient({ baseUrl });
    await client.login(CREDENCIAL_VALIDA.email, CREDENCIAL_VALIDA.senha);
    await client.iniciarGravacao("https://staging.empresa.com/a");
    await client.registrarStep({ acao: "click", elemento: "x" });

    const descartar = await client.descartarGravacao();
    assert.equal(descartar.mensagem, "Gravação descartada");

    const status = await client.status();
    assert.equal(status.gravando, false);
    assert.equal(status.totalSteps, 0);
  });
});
