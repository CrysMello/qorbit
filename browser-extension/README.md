# Qorbit — Extensão do Gravador de Testes

Instala uma vez no navegador e a gravação passa a funcionar automaticamente em
qualquer aba, sem precisar arrastar bookmarklet nem colar código no Console.

## Instalação (Chrome / Edge)

1. Abra `chrome://extensions` (ou `edge://extensions`).
2. Ative o **Modo do desenvolvedor** (canto superior direito).
3. Clique em **Carregar sem compactação / Load unpacked**.
4. Selecione esta pasta (`browser-extension`).

Pronto — o ícone da extensão aparece na barra, e a partir de agora, sempre que
você clicar em "Iniciar gravação" na página do Qorbit, a captura já começa
automaticamente na aba que abrir.

## Como funciona

- `content-bridge.js` roda só nas páginas do Qorbit e repassa o token de sessão
  para a extensão quando você clica em iniciar/parar gravação.
- `background.js` guarda esse token e envia os eventos capturados para o
  backend do Qorbit (`/captura-publica/evento`).
- `content-capture.js` roda em qualquer aba e só ativa a captura de cliques,
  preenchimentos e navegação quando existe uma sessão de gravação ativa.
