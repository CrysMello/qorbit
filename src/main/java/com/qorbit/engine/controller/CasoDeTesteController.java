package com.qorbit.engine.controller;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/casos")
public class CasoDeTesteController {

    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private QorbitUserRepository userRepo;

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
    @Transactional(readOnly = true)
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String modulo) {
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
            }

            List<CasoDeTeste> casos;
            if (busca != null && !busca.isBlank())
                casos = casoRepo.findByUsuarioAndNomeContainingIgnoreCase(usuario, busca);
            else if (modulo != null && !modulo.isBlank())
                casos = casoRepo.findByUsuarioAndModuloIgnoreCase(usuario, modulo);
            else
                casos = casoRepo.findByUsuario(usuario);

            List<Map<String, Object>> resultado = new ArrayList<>();
            for (CasoDeTeste c : casos) {
                resultado.add(toMap(c));
            }
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            System.err.println("Erro ao listar casos: " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Erro interno ao listar casos de teste"));
        }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> buscar(@PathVariable Long id) {
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
            }

            return casoRepo.findById(id)
                .filter(c -> c.getUsuario() != null && c.getUsuario().getId().equals(usuario.getId()))
                .map(c -> ResponseEntity.ok(toMap(c)))
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("Erro ao buscar caso " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Erro interno ao buscar caso de teste"));
        }
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody CasoDeTeste caso) {
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
            }

            if (caso.getNome() == null || caso.getNome().isBlank())
                return ResponseEntity.badRequest().body(Map.of("erro", "Nome é obrigatório"));

            long total = casoRepo.count();
            caso.setCodigo("CT-" + String.format("%02d", total + 1));
            caso.setStatus("ATIVO");
            caso.setUsuario(usuario);

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
            System.err.println("Erro ao criar caso: " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Erro interno ao criar caso de teste"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody CasoDeTeste dados) {
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
            }

            return casoRepo.findById(id).flatMap(caso -> {
                if (!caso.getUsuario().getId().equals(usuario.getId())) {
                    return java.util.Optional.empty();
                }
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
                return java.util.Optional.of(ResponseEntity.ok(toMap(casoRepo.save(caso))));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("Erro ao atualizar caso " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Erro interno ao atualizar caso de teste"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                return ResponseEntity.status(401).build();
            }

            return casoRepo.findById(id).map(caso -> {
                if (!caso.getUsuario().getId().equals(usuario.getId())) {
                    return ResponseEntity.status(403).<Void>build();
                }
                casoRepo.deleteById(id);
                return ResponseEntity.noContent().<Void>build();
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("Erro ao deletar caso " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/modulos")
    public List<String> listarModulos() {
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                return new ArrayList<>();
            }

            return casoRepo.findByUsuario(usuario).stream()
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
