package com.qorbit.engine.controller;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import com.qorbit.engine.selenium.SeleniumWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/execucoes")
public class ExecucaoController {

    @Autowired private ExecucaoRepository    execucaoRepo;
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ElementoRepository    elementoRepo;
    @Autowired private SeleniumWorker        worker;
    @Autowired private QorbitUserRepository  userRepo;

    /**
     * Obtém o usuário autenticado do contexto de segurança
     */
    private QorbitUser getUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            return userRepo.findByEmailIgnoreCase(email).orElse(null);
        }
        return null;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return new ArrayList<>();
        }
        return execucaoRepo.findByUsuarioOrderByIdDesc(usuario)
                .stream().map(this::toMap).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> buscar(@PathVariable Long id) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }

        return execucaoRepo.findById(id)
                .filter(e -> e.getUsuario() != null && e.getUsuario().getId().equals(usuario.getId()))
                .map(e -> ResponseEntity.ok(toMap(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciar(@RequestBody Map<String, Object> body) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        String url        = (String) body.get("url");
        String browser    = (String) body.getOrDefault("browser", "chrome");
        String metodoAuth = (String) body.getOrDefault("metodoAuth", "COOKIE");

        @SuppressWarnings("unchecked")
        List<Integer> idsCasos = (List<Integer>) body.get("idsCasos");

        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(Map.of("erro", "URL é obrigatória"));
        if (idsCasos == null || idsCasos.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("erro", "Selecione ao menos um caso de teste"));

        List<Long> ids = idsCasos.stream().map(Integer::longValue).toList();
        List<CasoDeTeste> casos = casoRepo.findAllById(ids);
        
        // ✅ VALIDAR: Todos os casos pertencem ao usuário autenticado
        for (CasoDeTeste caso : casos) {
            if (caso.getUsuario() == null || !caso.getUsuario().getId().equals(usuario.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Você não tem permissão para executar esse caso"));
            }
        }

        if (casos.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("erro", "Nenhum caso de teste encontrado"));

        Execucao exec = new Execucao();
        exec.setUrlAlvo(url);
        exec.setBrowser(browser);
        exec.setMetodoAuth(metodoAuth);
        exec.setStatus("AGUARDANDO");
        exec.setUsuario(usuario);  // ✅ SETAR O USUÁRIO
        exec = execucaoRepo.save(exec);

        worker.executar(exec, casos, browser, null);

        return ResponseEntity.ok(Map.of(
                "mensagem",   "Execução iniciada",
                "execucaoId", exec.getId()
        ));
    }

    /** Deleta uma execução específica pelo ID */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }

        return execucaoRepo.findById(id).map(exec -> {
            if (exec.getUsuario() == null || !exec.getUsuario().getId().equals(usuario.getId())) {
                return ResponseEntity.status(403).<ResponseEntity<?>>build();
            }
            execucaoRepo.deleteById(id);
            return ResponseEntity.ok(Map.of("mensagem", "Execução #" + id + " removida"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Deleta todas as execuções com status AGUARDANDO */
    @DeleteMapping("/limpar/aguardando")
    public ResponseEntity<?> limparAguardando() {
        long totalExecucoes = execucaoRepo.count();
        long totalElementos = elementoRepo.count();
        long totalCasos     = casoRepo.count();

        execucaoRepo.deleteAll();
        elementoRepo.deleteAll();
        casoRepo.deleteAll();

        return ResponseEntity.ok(Map.of(
                "mensagem", "Dados limpos: " + totalExecucoes + " execução(ões), "
                          + totalElementos + " elemento(s), "
                          + totalCasos + " caso(s) de teste removido(s)",
                "total", totalExecucoes + totalElementos + totalCasos
        ));
    }

    /** Deleta TODAS as execuções */
    @DeleteMapping("/limpar/todas")
    public ResponseEntity<?> limparTodas() {
        long total = execucaoRepo.count();
        execucaoRepo.deleteAll();
        return ResponseEntity.ok(Map.of(
                "mensagem", total + " execução(ões) removida(s)",
                "total",    total
        ));
    }

    // ── DTO ──────────────────────────────────────────────────────────────────
    private Map<String, Object> toMap(Execucao e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                    e.getId());
        m.put("urlAlvo",               e.getUrlAlvo());
        m.put("browser",               e.getBrowser());
        m.put("metodoAuth",            e.getMetodoAuth());
        m.put("status",                e.getStatus());
        m.put("totalSteps",            e.getTotalSteps());
        m.put("stepsPAssou",           e.getStepsPAssou());
        m.put("stepsFalhou",           e.getStepsFalhou());
        m.put("percentualSucesso",     e.getPercentualSucesso());
        m.put("tempoExecucaoSegundos", e.getTempoExecucaoSegundos());
        m.put("iniciadoEm",            e.getIniciadoEm());
        m.put("finalizadoEm",          e.getFinalizadoEm());
        return m;
    }
}

@RestController
@RequestMapping("/api/codigo")
class CodigoController {

    @Autowired private com.qorbit.engine.service.GeradorCodigoService geradorService;
    @Autowired private CasoDeTesteRepository casoRepo;

    @PostMapping("/gerar")
public ResponseEntity<?> gerar(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Integer> idsCasos = (List<Integer>) body.get("idsCasos");
    if (idsCasos == null || idsCasos.isEmpty())
        return ResponseEntity.badRequest().body(Map.of("erro", "Selecione os casos de teste"));

    boolean includeCiCd = Boolean.TRUE.equals(body.get("includeCiCd"));
    String formato = body.getOrDefault("formato", "CUCUMBER").toString();

    List<Long> ids = idsCasos.stream().map(Integer::longValue).toList();
    List<CasoDeTeste> casos = casoRepo.findAllById(ids);
    try {
        byte[] zip = geradorService.gerarZip(casos, includeCiCd, formato);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"scanner-tests-export.zip\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Falha ao gerar código: " + e.getMessage()));
    }
}
}
