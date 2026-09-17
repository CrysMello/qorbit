// Service worker: envia ao backend do Qorbit os eventos capturados na aba do
// site sob teste. Rodar aqui (contexto de extensão) evita qualquer bloqueio
// de CORS que existiria se o fetch fosse feito a partir da própria aba —
// os hosts do backend estão em host_permissions (manifest.json).
//
// A sessão ativa (token + eventoUrl) é gravada direto em chrome.storage.local
// por content-bridge.js, não por aqui (ver comentário lá).

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg.tipo === 'evento') {
    enviarEvento(msg.evento);
  }
  return true;
});

async function enviarEvento(evento) {
  const { qorbitSessao } = await chrome.storage.local.get('qorbitSessao');
  if (!qorbitSessao) return;
  try {
    await fetch(qorbitSessao.eventoUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Object.assign({ token: qorbitSessao.token }, evento))
    });
  } catch (e) {}
}
