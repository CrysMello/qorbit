package com.qorbit.engine.controller;

import com.qorbit.engine.service.AIPluginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@Controller
public class AIPluginController {

    @Autowired
    private AIPluginService aiPluginService;

    // ─── Página HTML ──────────────────────────────────────────────────────────

    @GetMapping("/ai-plugin")
    public String pagina() {
        return "ai-plugin";
    }

    // ─── REST API ─────────────────────────────────────────────────────────────

    @GetMapping("/api/ai/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(aiPluginService.status());
    }

    @PostMapping("/api/ai/configurar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> configurar(@RequestBody Map<String, String> body) {
        aiPluginService.configurar(
                body.getOrDefault("endpoint", ""),
                body.getOrDefault("modelo", ""),
                body.getOrDefault("authTipo", "bearer"),
                body.getOrDefault("apiKey", "")
        );
        return ResponseEntity.ok(Map.of("ok", true, "mensagem", "Configuração guardada."));
    }

    @GetMapping("/api/ai/testar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testar() {
        return ResponseEntity.ok(aiPluginService.testarConexao());
    }

    @PostMapping("/api/ai/melhorar-nomes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> melhorarNomes() {
        return ResponseEntity.ok(aiPluginService.melhorarNomesElementos());
    }

    @PostMapping("/api/ai/detectar-abas")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> detectarAbas(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(aiPluginService.detectarAbas(
                body.getOrDefault("html", ""),
                body.getOrDefault("url", "")
        ));
    }

    @PostMapping("/api/ai/diagnosticar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> diagnosticar(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(aiPluginService.diagnosticarFalha(
                body.getOrDefault("seletor", ""),
                body.getOrDefault("erro", ""),
                body.getOrDefault("pagina", "")
        ));
    }
}
