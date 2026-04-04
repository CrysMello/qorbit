package com.qorbit.engine.controller;

import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/casos")
public class CasoDeTesteController {

    @Autowired private CasoDeTesteRepository casoRepo;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String modulo) {
        try {
            List<CasoDeTeste> casos;
            if (busca != null && !busca.isBlank())
                casos = casoRepo.findByNomeContainingIgnoreCase(busca);
            else if (modulo != null && !modulo.isBlank())
                casos = casoRepo.findByModuloIgnoreCase(modulo);
            else
                casos = casoRepo.findAll();

            List<Map<String, Object>> resultado = new ArrayList<>();
            for (CasoDeTeste c : casos) {
                resultado.add(toMap(c));
            }
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", e.getMessage() != null ? e.getMessage() : "Erro interno"));
        }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> buscar(@PathVariable Long id) {
        try {
            return casoRepo.findById(id)
                .map(c -> ResponseEntity.ok(toMap(c)))
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody CasoDeTeste caso) {
        try {
            if (caso.getNome() == null || caso.getNome().isBlank())
                return ResponseEntity.badRequest().body(Map.of("erro", "Nome é obrigatório"));

            long total = casoRepo.count();
            caso.setCodigo("CT-" + String.format("%02d", total + 1));
            caso.setStatus("ATIVO");

            if (caso.getSteps() != null) {
                AtomicInteger num = new AtomicInteger(1);
                caso.getSteps().forEach(s -> {
                    s.setCasoDeTeste(caso);
                    s.setNumeroStep(num.getAndIncrement());
                    if (s.getDescricaoGherkin() == null || s.getDescricaoGherkin().isBlank())
                        s.setDescricaoGherkin(gerarGherkin(s));
                });
            }

            CasoDeTeste salvo = casoRepo.save(caso);
            return ResponseEntity.ok(toMap(salvo));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", e.getMessage() != null ? e.getMessage() : "Erro ao criar"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody CasoDeTeste dados) {
        try {
            return casoRepo.findById(id).map(caso -> {
                if (dados.getNome() != null)      caso.setNome(dados.getNome());
                if (dados.getModulo() != null)    caso.setModulo(dados.getModulo());
                if (dados.getDescricao() != null) caso.setDescricao(dados.getDescricao());
                if (dados.getUrlAlvo() != null)   caso.setUrlAlvo(dados.getUrlAlvo());
                if (dados.getStatus() != null)    caso.setStatus(dados.getStatus());

                if (dados.getSteps() != null) {
                    caso.getSteps().clear();
                    AtomicInteger num = new AtomicInteger(1);
                    dados.getSteps().forEach(s -> {
                        s.setCasoDeTeste(caso);
                        s.setNumeroStep(num.getAndIncrement());
                        if (s.getDescricaoGherkin() == null || s.getDescricaoGherkin().isBlank())
                            s.setDescricaoGherkin(gerarGherkin(s));
                        caso.getSteps().add(s);
                    });
                }
                return ResponseEntity.ok(toMap(casoRepo.save(caso)));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", e.getMessage() != null ? e.getMessage() : "Erro ao atualizar"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        if (!casoRepo.existsById(id)) return ResponseEntity.notFound().build();
        casoRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/modulos")
    public List<String> listarModulos() {
        try {
            return casoRepo.findAll().stream()
                .map(CasoDeTeste::getModulo)
                .filter(m -> m != null && !m.isBlank())
                .distinct().sorted().toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── DTO sem referência circular ───────────────────────────────────────────
    private Map<String, Object> toMap(CasoDeTeste c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",        c.getId());
        m.put("codigo",    safe(c.getCodigo()));
        m.put("nome",      safe(c.getNome()));
        m.put("modulo",    safe(c.getModulo()));
        m.put("descricao", safe(c.getDescricao()));
        m.put("urlAlvo",   safe(c.getUrlAlvo()));
        m.put("status",    safe(c.getStatus()));
        try {
            m.put("criadoEm", c.getCriadoEm() != null ? c.getCriadoEm().toString() : null);
        } catch (Exception e) {
            m.put("criadoEm", null);
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        try {
            if (c.getSteps() != null) {
                c.getSteps().stream()
                    .sorted(Comparator.comparingInt(s -> s.getNumeroStep() != null ? s.getNumeroStep() : 0))
                    .forEach(s -> {
                        Map<String, Object> sm = new LinkedHashMap<>();
                        sm.put("id",                 s.getId());
                        sm.put("numeroStep",          s.getNumeroStep());
                        sm.put("acao",                safe(s.getAcao()));
                        sm.put("nomeLogicoElemento",  safe(s.getNomeLogicoElemento()));
                        sm.put("valorEntrada",         safe(s.getValorEntrada()));
                        sm.put("resultadoEsperado",    safe(s.getResultadoEsperado()));
                        sm.put("descricaoGherkin",     safe(s.getDescricaoGherkin()));
                        steps.add(sm);
                    });
            }
        } catch (Exception e) {
            // steps permanecem vazios se houver erro ao carregar
            System.err.println("Erro ao carregar steps do caso " + c.getId() + ": " + e.getMessage());
        }
        m.put("steps", steps);
        return m;
    }

    private String safe(String s) { return s != null ? s : ""; }

    private String gerarGherkin(StepTeste s) {
        if (s.getAcao() == null) return "";
        return switch (s.getAcao().toUpperCase()) {
            case "NAVEGAR"   -> "acesso a URL \"" + safe(s.getValorEntrada()) + "\"";
            case "CLICAR"    -> "clico em " + safe(s.getNomeLogicoElemento());
            case "PREENCHER" -> "preencho " + safe(s.getNomeLogicoElemento()) + " com \"" + safe(s.getValorEntrada()) + "\"";
            case "VALIDAR"   -> safe(s.getNomeLogicoElemento()) + " esta visivel";
            case "SELECIONAR"-> "seleciono \"" + safe(s.getValorEntrada()) + "\" em " + safe(s.getNomeLogicoElemento());
            case "LIMPAR"    -> "limpo o campo " + safe(s.getNomeLogicoElemento());
            default          -> s.getAcao() + " " + safe(s.getNomeLogicoElemento());
        };
    }
}
