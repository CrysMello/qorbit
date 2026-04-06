package com.qorbit.engine.controller;

import org.springframework.transaction.annotation.Transactional;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.model.SuiteTeste;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import com.qorbit.engine.repository.SuiteTesteRepository;
import com.qorbit.engine.service.GeradorCodigoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class SuiteTesteController {

    @Autowired private SuiteTesteRepository suiteRepo;
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ExecucaoRepository execucaoRepo;
    @Autowired private GeradorCodigoService geradorCodigoService;

    // ── Página HTML ───────────────────────────────────────────────────────────

    /*@GetMapping("/suites")
    public String pagina(Model model) {
    try {
        List<SuiteTeste> suites = suiteRepo.findAllByOrderByNomeAsc();
        model.addAttribute("suites", suites != null ? suites : List.of());
        model.addAttribute("totalSuites", suites != null ? suites.size() : 0);
    } catch (Exception e) {
        model.addAttribute("suites", List.of());
        model.addAttribute("totalSuites", 0);
    }
    return "suites";*/
    @GetMapping("/suites")
    @Transactional(readOnly = true)
public String pagina(Model model) {
    try {
        List<SuiteTeste> suites = suiteRepo.findAllByOrderByNomeAsc();
        model.addAttribute("suites", suites != null ? suites : List.of());
        model.addAttribute("totalSuites", suites != null ? suites.size() : 0);
    } catch (Exception e) {
        model.addAttribute("suites", List.of());
        model.addAttribute("totalSuites", 0);
    }
    return "suites";
}
    @GetMapping("/suites")
public String pagina(Model model) {
    try {
        List<SuiteTeste> suites = suiteRepo.findAllByOrderByNomeAsc();
        model.addAttribute("suites", suites != null ? suites : List.of());
        model.addAttribute("totalSuites", suites != null ? suites.size() : 0);
    } catch (Exception e) {
        System.err.println("ERRO SUITES PAGINA: " + e.getClass().getName() + " — " + e.getMessage());
        e.printStackTrace();
        model.addAttribute("suites", List.of());
        model.addAttribute("totalSuites", 0);
    }
    return "suites";
}

    // ── REST API ──────────────────────────────────────────────────────────────

    @GetMapping("/api/suites")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> listar() {
        try {
            List<SuiteTeste> suites = suiteRepo.findAllByOrderByNomeAsc();
            List<Map<String, Object>> resultado = new ArrayList<>();
            for (SuiteTeste s : suites) resultado.add(toMap(s));
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/api/suites/{id}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> buscar(@PathVariable Long id) {
        return suiteRepo.findById(id)
                .map(s -> ResponseEntity.ok(toMapDetalhado(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/suites")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> criar(@RequestBody Map<String, String> body) {
        try {
            String nome = body.getOrDefault("nome", "").trim();
            if (nome.isBlank()) return ResponseEntity.badRequest().body(Map.of("erro", "Nome obrigatório."));
            if (suiteRepo.existsByNome(nome)) return ResponseEntity.badRequest().body(Map.of("erro", "Já existe uma suite com este nome."));

            SuiteTeste suite = new SuiteTeste();
            suite.setNome(nome);
            suite.setDescricao(body.getOrDefault("descricao", ""));
            suite.setCor(body.getOrDefault("cor", "green"));
            suiteRepo.save(suite);
            return ResponseEntity.ok(Map.of("ok", true, "id", suite.getId(), "mensagem", "Suite criada com sucesso."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    @PutMapping("/api/suites/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            SuiteTeste suite = suiteRepo.findById(id).orElse(null);
            if (suite == null) return ResponseEntity.notFound().build();

            if (body.containsKey("nome") && !body.get("nome").isBlank()) suite.setNome(body.get("nome").trim());
            if (body.containsKey("descricao")) suite.setDescricao(body.get("descricao"));
            if (body.containsKey("cor")) suite.setCor(body.get("cor"));
            suiteRepo.save(suite);
            return ResponseEntity.ok(Map.of("ok", true, "mensagem", "Suite actualizada."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    @DeleteMapping("/api/suites/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> excluir(@PathVariable Long id) {
        try {
            if (!suiteRepo.existsById(id)) return ResponseEntity.notFound().build();
            SuiteTeste suite = suiteRepo.findById(id).get();
            suite.getCasos().clear(); // remove relacionamentos antes de excluir
            suiteRepo.save(suite);
            suiteRepo.deleteById(id);
            return ResponseEntity.ok(Map.of("ok", true, "mensagem", "Suite excluída. Os casos de teste não foram afectados."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    // ── Adicionar casos à suite ───────────────────────────────────────────────

    @PostMapping("/api/suites/{id}/casos")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> adicionarCasos(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            SuiteTeste suite = suiteRepo.findById(id).orElse(null);
            if (suite == null) return ResponseEntity.notFound().build();

            @SuppressWarnings("unchecked")
            List<Integer> casoIds = (List<Integer>) body.get("casoIds");
            if (casoIds == null || casoIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("erro", "Nenhum caso informado."));

            int adicionados = 0;
            for (Integer casoId : casoIds) {
                CasoDeTeste caso = casoRepo.findById(casoId.longValue()).orElse(null);
                if (caso != null && !suite.getCasos().contains(caso)) {
                    suite.getCasos().add(caso);
                    adicionados++;
                }
            }
            suiteRepo.save(suite);
            return ResponseEntity.ok(Map.of("ok", true, "adicionados", adicionados,
                    "mensagem", adicionados + " caso(s) adicionado(s) à suite."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    // ── Adicionar todos os casos de uma execução bem sucedida ─────────────────

    @PostMapping("/api/suites/{id}/execucao/{execId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> adicionarDaExecucao(@PathVariable Long id, @PathVariable Long execId) {
        try {
            SuiteTeste suite = suiteRepo.findById(id).orElse(null);
            if (suite == null) return ResponseEntity.notFound().build();

            Execucao exec = execucaoRepo.findById(execId).orElse(null);
            if (exec == null) return ResponseEntity.notFound().build();

            // Só permite adicionar se a execução passou 100%
            int passou = exec.getStepsPAssou() != null ? exec.getStepsPAssou() : 0;
            int falhou = exec.getStepsFalhou() != null ? exec.getStepsFalhou() : 0;
            if (falhou > 0) {
                return ResponseEntity.badRequest().body(Map.of("erro",
                        "Só é possível adicionar execuções onde todos os casos passaram."));
            }

            // Busca casos associados à execução pelo URL alvo
            List<CasoDeTeste> casos = casoRepo.findByUrlAlvoContainingIgnoreCase(
                    exec.getUrlAlvo() != null ? exec.getUrlAlvo() : "");

            int adicionados = 0;
            for (CasoDeTeste caso : casos) {
                if (!suite.getCasos().contains(caso)) {
                    suite.getCasos().add(caso);
                    adicionados++;
                }
            }

            suite.setUltimaExecucao(LocalDateTime.now().toString());
            suite.setUltimoResultado(passou + "/" + (passou + falhou) + " passou");
            suiteRepo.save(suite);

            return ResponseEntity.ok(Map.of("ok", true, "adicionados", adicionados,
                    "mensagem", adicionados + " caso(s) adicionado(s) à suite '" + suite.getNome() + "'."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    // ── Remover caso da suite ─────────────────────────────────────────────────

    @DeleteMapping("/api/suites/{id}/casos/{casoId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> removerCaso(@PathVariable Long id, @PathVariable Long casoId) {
        try {
            SuiteTeste suite = suiteRepo.findById(id).orElse(null);
            if (suite == null) return ResponseEntity.notFound().build();

            suite.getCasos().removeIf(c -> c.getId().equals(casoId));
            suiteRepo.save(suite);
            return ResponseEntity.ok(Map.of("ok", true, "mensagem", "Caso removido da suite."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    // ── Gerar ZIP da suite ────────────────────────────────────────────────────

    @GetMapping("/api/suites/{id}/exportar")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportar(@PathVariable Long id) {
        try {
            SuiteTeste suite = suiteRepo.findById(id).orElse(null);
            if (suite == null) return ResponseEntity.notFound().build();
            if (suite.getCasos().isEmpty())
                return ResponseEntity.badRequest().build();

            byte[] zip = geradorCodigoService.gerarZip(suite.getCasos());
            String filename = "qorbit-suite-" + suite.getNome().toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-") + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zip);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(SuiteTeste s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("nome", s.getNome());
        m.put("descricao", s.getDescricao());
        m.put("cor", s.getCor());
        m.put("totalCasos", s.getCasos().size());
        m.put("ultimaExecucao", s.getUltimaExecucao());
        m.put("ultimoResultado", s.getUltimoResultado());
        m.put("criadoEm", s.getCriadoEm());
        return m;
    }

    private Map<String, Object> toMapDetalhado(SuiteTeste s) {
        Map<String, Object> m = toMap(s);
        List<Map<String, Object>> casos = new ArrayList<>();
        for (CasoDeTeste c : s.getCasos()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("nome", c.getNome());
            cm.put("modulo", c.getModulo());
            cm.put("urlAlvo", c.getUrlAlvo());
            cm.put("status", c.getStatus());
            cm.put("totalSteps", c.getSteps() != null ? c.getSteps().size() : 0);
            casos.add(cm);
        }
        m.put("casos", casos);
        return m;
    }
}
