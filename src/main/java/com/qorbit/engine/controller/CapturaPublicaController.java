package com.qorbit.engine.controller;

import com.qorbit.engine.service.GravacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint público (fora de /api, sem sessão autenticada) usado pela extensão de
 * navegador do Qorbit para reportar os eventos capturados na aba do usuário —
 * em vez de um Chrome controlado por Selenium no servidor (que em produção abria
 * headless e invisível, pois o servidor não tem display).
 *
 * O acesso é protegido por um token de sessão de uso único, gerado em
 * GravacaoController#iniciar e validado a cada evento recebido.
 */
@RestController
@RequestMapping("/captura-publica")
public class CapturaPublicaController {

    @Autowired private GravacaoService gravacaoService;

    @PostMapping("/evento")
    public ResponseEntity<?> evento(@RequestBody Map<String, Object> evento) {
        Object token = evento.get("token");
        if (token == null || !gravacaoService.tokenValido(token.toString())) {
            return ResponseEntity.status(403).body(Map.of("erro", "Token inválido ou gravação encerrada"));
        }
        return ResponseEntity.ok(gravacaoService.registrarStep(evento));
    }
}
