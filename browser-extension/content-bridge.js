// Roda apenas nas páginas do próprio Qorbit. Faz a ponte entre a página (que não
// tem acesso direto à API de extensões) e o service worker, via CustomEvent no DOM.

window.__qorbitExtensaoPresente = true;
window.dispatchEvent(new CustomEvent('qorbit-extensao-presente'));

window.addEventListener('qorbit-iniciar-gravacao', (e) => {
  chrome.runtime.sendMessage({ tipo: 'iniciar', token: e.detail.token, eventoUrl: e.detail.eventoUrl });
});

window.addEventListener('qorbit-parar-gravacao', () => {
  chrome.runtime.sendMessage({ tipo: 'parar' });
});
