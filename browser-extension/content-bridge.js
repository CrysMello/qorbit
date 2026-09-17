// Roda apenas nas páginas do próprio Qorbit (inclui a página-ponte
// /captura-publica/bridge, usada pelo fluxo da extensão do VSCode). Faz a
// ponte entre a página (que não tem acesso direto à API de extensões) e o
// armazenamento da extensão, via CustomEvent no DOM.
//
// Grava direto em chrome.storage.local (em vez de pedir pro service worker
// gravar) porque a página-ponte navega para a URL alvo poucos milissegundos
// depois de disparar o evento — repassar pro service worker e esperar a
// resposta dependeria do ciclo de vida dele (pode estar dormindo), o que
// tornaria essa corrida real. Gravando aqui mesmo, a confirmação
// (qorbit-gravacao-pronta) só chega depois que o storage.set já resolveu.

window.__qorbitExtensaoPresente = true;
window.dispatchEvent(new CustomEvent('qorbit-extensao-presente'));

window.addEventListener('qorbit-iniciar-gravacao', async (e) => {
  const { token, eventoUrl } = e.detail;
  try {
    await chrome.storage.local.set({ qorbitSessao: { token, eventoUrl } });
  } catch (err) {
    // sem storage não tem captura — ainda assim avisa a página pra não
    // travar esperando a confirmação indefinidamente.
  }
  window.dispatchEvent(new CustomEvent('qorbit-gravacao-pronta'));
});

window.addEventListener('qorbit-parar-gravacao', async () => {
  try {
    await chrome.storage.local.remove('qorbitSessao');
  } catch (err) {}
});
