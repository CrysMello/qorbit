# Imagem de desenvolvimento do Qorbit Engine: Java 17 + Maven (para compilar e
# rodar o backend) + Chrome + Xvfb + x11vnc + noVNC (para reproduzir, local ou
# no Codespaces, o mesmo esquema de "Chrome visível sem monitor real" que hoje
# roda em produção — ver commit "volta pro Selenium simples + streaming via
# noVNC em producao").
FROM mcr.microsoft.com/devcontainers/java:17

RUN apt-get update && apt-get install -y --no-install-recommends \
        maven \
        xvfb \
        x11vnc \
        novnc \
        websockify \
        wget \
        gnupg \
    && rm -rf /var/lib/apt/lists/*

# Google Chrome estável — mesmo binário que o WebDriverManager já resolve
# sozinho em produção (não precisa apontar scanner.chrome.bin manualmente).
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub \
        | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y --no-install-recommends google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*
