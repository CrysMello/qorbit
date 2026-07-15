package com.qorbit.engine.controller;

import com.qorbit.engine.service.GravacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/gravacao")
public class GravacaoController {

    @Autowired private GravacaoService gravacaoService;

    /**
     * Inicia uma sessão de gravação. A captura roda na própria aba do usuário
     * (via bookmarklet/script gerado em CapturaPublicaController), não mais num
     * Chrome controlado por Selenium no servidor — em produção esse Chrome abria
     * headless e invisível no servidor remoto, impossível de interagir.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciar(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "URL é obrigatória"));
        }
        String erroUrl = validarUrlSegura(url);
        if (erroUrl != null) {
            return ResponseEntity.badRequest().body(Map.of("erro", erroUrl));
        }
        if (gravacaoService.isGravando()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Já existe uma gravação em andamento. Pare primeiro."));
        }

        Map<String, Object> resultado = gravacaoService.iniciarGravacao(url);
        if (resultado.containsKey("erro")) {
            return ResponseEntity.badRequest().body(resultado);
        }
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/parar")
    public ResponseEntity<?> parar(@RequestBody Map<String, String> body) {
        String nomeCaso = body.getOrDefault("nomeCaso", "");
        String modulo = body.getOrDefault("modulo", "");
        Map<String, Object> resultado = gravacaoService.pararGravacao(nomeCaso, modulo);
        if (resultado.containsKey("erro")) {
            return ResponseEntity.badRequest().body(resultado);
        }
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/step")
    public ResponseEntity<?> receberStep(@RequestBody Map<String, Object> evento) {
        return ResponseEntity.ok(gravacaoService.registrarStep(evento));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(gravacaoService.getStatus());
    }

    @DeleteMapping("/step/{numero}")
    public ResponseEntity<Void> removerStep(@PathVariable int numero) {
        gravacaoService.removerStep(numero);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/descartar")
    public ResponseEntity<?> descartar() {
        gravacaoService.pararGravacao("_descartar_", "_descartar_");
        return ResponseEntity.ok(Map.of("mensagem", "Gravação descartada"));
    }

    /** Valida URL para prevenir SSRF: permite apenas http/https para hosts públicos. */
    private String validarUrlSegura(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return "Apenas URLs http:// e https:// são permitidas";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "Host inválido na URL";
            }
            // Bloqueia endpoints de metadados de cloud (AWS/GCP/Azure)
            String hostLower = host.toLowerCase();
            if (hostLower.equals("169.254.169.254") || hostLower.contains("metadata.google.internal")) {
                return "URL bloqueada por política de segurança";
            }
            // Resolve o host e bloqueia endereços privados/loopback
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                return "URL bloqueada por política de segurança";
            }
            return null; // OK
        } catch (Exception e) {
            return "URL inválida";
        }
    }
}
