package com.qorbit.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qorbit.engine.service.GravacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint público (fora de /api, sem sessão autenticada) usado pela extensão de
 * navegador do Qorbit para reportar os eventos capturados na aba do usuário —
 * caminho usado pela extensão do VSCode, como alternativa ao Chrome controlado
 * por Selenium no servidor (esse continua existindo, para o fluxo web).
 *
 * O acesso é protegido por um token de sessão de uso único, gerado em
 * GravacaoController#iniciarExtensao e validado a cada evento recebido.
 */
@RestController
@RequestMapping("/captura-publica")
public class CapturaPublicaController {

    @Autowired private GravacaoService gravacaoService;
    @Autowired private ObjectMapper objectMapper;

    @PostMapping("/evento")
    public ResponseEntity<?> evento(@RequestBody Map<String, Object> evento) {
        Object token = evento.get("token");
        if (token == null || !gravacaoService.tokenValido(token.toString())) {
            return ResponseEntity.status(403).body(Map.of("erro", "Token inválido ou gravação encerrada"));
        }
        return ResponseEntity.ok(gravacaoService.registrarStep(evento));
    }

    /**
     * Página-ponte: avisa a extensão de navegador (via CustomEvent, repassado
     * pelo content-bridge.js dela) qual token/URL de eventos usar, e só então
     * navega para a URL alvo da gravação — resolvida sempre pelo token no
     * servidor, nunca aceita via query param (evitaria open-redirect).
     */
    @GetMapping(value = "/bridge", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> bridge(@RequestParam String token) {
        String url = gravacaoService.getUrlSeTokenValido(token);
        if (url == null) {
            return ResponseEntity.status(403).body(
                "<!doctype html><html><body>" +
                "Token inválido ou gravação encerrada. Volte ao VSCode e tente novamente." +
                "</body></html>");
        }

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("token", token);
        dados.put("target", url);
        String json;
        try {
            json = objectMapper.writeValueAsString(dados).replace("</", "<\\/");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("<!doctype html><html><body>Erro interno.</body></html>");
        }

        String html = """
            <!doctype html>
            <html>
            <head>
              <meta name="referrer" content="no-referrer">
              <meta charset="utf-8">
              <title>Qorbit — iniciando gravação…</title>
            </head>
            <body>
              <p>Iniciando gravação, aguarde…</p>
              <script>
                (function () {
                  var dados = %s;
                  var eventoUrl = window.location.origin + '/captura-publica/evento';
                  var navegou = false;
                  function ir() {
                    if (navegou) return;
                    navegou = true;
                    window.location.replace(dados.target);
                  }
                  window.addEventListener('qorbit-gravacao-pronta', ir, { once: true });
                  window.dispatchEvent(new CustomEvent('qorbit-iniciar-gravacao', {
                    detail: { token: dados.token, eventoUrl: eventoUrl }
                  }));
                  // Extensão pode não estar instalada — navega mesmo assim depois de um tempo.
                  setTimeout(ir, 800);
                })();
              </script>
            </body>
            </html>
            """.formatted(json);

        return ResponseEntity.ok(html);
    }
}
