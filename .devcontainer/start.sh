#!/usr/bin/env bash
# Sobe, nessa ordem, a mesma infra usada em produção (Xvfb + x11vnc + noVNC) e
# depois o backend. Roda toda vez que o Dev Container inicia (postStartCommand).
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "[qorbit] Subindo Xvfb (:99, 1280x800)..."
Xvfb :99 -screen 0 1280x800x24 >/tmp/xvfb.log 2>&1 &
export DISPLAY=:99
sleep 1

echo "[qorbit] Subindo x11vnc..."
x11vnc -display :99 -nopw -forever -shared -quiet >/tmp/x11vnc.log 2>&1 &
sleep 1

echo "[qorbit] Subindo noVNC (http://localhost:6080/vnc_lite.html)..."
websockify --web=/usr/share/novnc 6080 localhost:5900 >/tmp/novnc.log 2>&1 &

echo "[qorbit] Subindo o backend (mvn spring-boot:run)..."
cd "$REPO_ROOT"
exec mvn spring-boot:run
