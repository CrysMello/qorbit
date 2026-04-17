package com.qorbit.engine.controller;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.service.CapturaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elementos")
public class ElementoController {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private CapturaService capturaService;
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
    public List<Elemento> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String pagina,
            @RequestParam(required = false) String status) {
        
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return List.of();
        }

        if (busca != null && !busca.isBlank()) {
            return elementoRepo.buscarPorUsuarioETexto(usuario, busca);
        }
        if (pagina != null && !pagina.isBlank()) {
            return elementoRepo.findByUsuarioAndPaginaIgnoreCase(usuario, pagina);
        }
        if (status != null && !status.isBlank()) {
            return elementoRepo.findByUsuarioAndStatus(usuario, status.toUpperCase());
        }
        return elementoRepo.findByUsuarioOrderByPaginaAscNomeLogicoAsc(usuario);
    }

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody Elemento elemento) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        // Valida unicidade nome lógico + página (para este usuário)
        if (elementoRepo.findByNomeLogicoAndPagina(elemento.getNomeLogico(), elemento.getPagina())
                .filter(e -> e.getUsuario() != null && e.getUsuario().getId().equals(usuario.getId()))
                .isPresent()) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Nome lógico '" + elemento.getNomeLogico()
                    + "' já existe na página '" + elemento.getPagina() + "'"));
        }
        elemento.setStatus("ATIVO");
        elemento.setUsuario(usuario);  // ✅ SETAR O USUÁRIO
        return ResponseEntity.ok(elementoRepo.save(elemento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody Elemento dados) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }

        return elementoRepo.findById(id).map(el -> {
            // ✅ Validar que o elemento pertence ao usuário
            if (el.getUsuario() == null || !el.getUsuario().getId().equals(usuario.getId())) {
                return (ResponseEntity<?>) ResponseEntity.status(403).build();
            }

            // Valida unicidade ao renomear
            if (!el.getNomeLogico().equals(dados.getNomeLogico()) &&
                elementoRepo.existsByNomeLogicoAndPaginaAndIdNot(
                    dados.getNomeLogico(), el.getPagina(), id)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Nome lógico já existe nesta página"));
            }
            if (dados.getNomeLogico() != null) el.setNomeLogico(dados.getNomeLogico());
            if (dados.getSeletorTecnico() != null) el.setSeletorTecnico(dados.getSeletorTecnico());
            if (dados.getTipoSeletor() != null) el.setTipoSeletor(dados.getTipoSeletor());
            if (dados.getDescricao() != null) el.setDescricao(dados.getDescricao());
            el.setStatus("ATIVO");
            return ResponseEntity.ok(elementoRepo.save(el));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }

        return elementoRepo.findById(id).map(el -> {
            // ✅ Validar que o elemento pertence ao usuário
            if (el.getUsuario() == null || !el.getUsuario().getId().equals(usuario.getId())) {
                return ResponseEntity.status(403).<Void>build();
            }
            elementoRepo.deleteById(id);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/capturar")
    public ResponseEntity<?> capturar(@RequestBody Map<String, String> body) {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        String url = body.get("url");
        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(Map.of("erro", "URL é obrigatória"));
        try {
            List<Elemento> capturados = capturaService.capturarElementos(url, usuario);
            return ResponseEntity.ok(Map.of(
                "mensagem", capturados.size() + " elementos capturados",
                "elementos", capturados
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("erro", "Falha na captura: " + e.getMessage()));
        }
    }

    @GetMapping("/paginas")
    public List<String> listarPaginas() {
        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return List.of();
        }
        return elementoRepo.findByUsuarioOrderByPaginaAscNomeLogicoAsc(usuario).stream()
            .map(Elemento::getPagina).distinct().sorted().toList();
    }
}
