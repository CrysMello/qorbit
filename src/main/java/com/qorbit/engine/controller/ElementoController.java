package com.qorbit.engine.controller;

import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.service.CapturaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elementos")
public class ElementoController {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private CapturaService capturaService;

    @GetMapping
    public List<Elemento> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String pagina,
            @RequestParam(required = false) String status) {

        if (busca != null && !busca.isBlank()) {
            return elementoRepo
                .findByNomeLogicoContainingIgnoreCaseOrSeletorTecnicoContainingIgnoreCase(busca, busca);
        }
        if (pagina != null && !pagina.isBlank()) {
            return elementoRepo.findByPaginaIgnoreCase(pagina);
        }
        if (status != null && !status.isBlank()) {
            return elementoRepo.findByStatus(status.toUpperCase());
        }
        return elementoRepo.findAllByOrderByPaginaAscNomeLogicoAsc();
    }

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody Elemento elemento) {
        // Valida unicidade nome lógico + página
        if (elementoRepo.findByNomeLogicoAndPagina(elemento.getNomeLogico(), elemento.getPagina()).isPresent()) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Nome lógico '" + elemento.getNomeLogico()
                    + "' já existe na página '" + elemento.getPagina() + "'"));
        }
        elemento.setStatus("ATIVO");
        return ResponseEntity.ok(elementoRepo.save(elemento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody Elemento dados) {
        return elementoRepo.findById(id).map(el -> {
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
        if (!elementoRepo.existsById(id)) return ResponseEntity.notFound().build();
        elementoRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/capturar")
    public ResponseEntity<?> capturar(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(Map.of("erro", "URL é obrigatória"));
        try {
            List<Elemento> capturados = capturaService.capturarElementos(url, null);
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
        return elementoRepo.findAll().stream()
            .map(Elemento::getPagina).distinct().sorted().toList();
    }
}
