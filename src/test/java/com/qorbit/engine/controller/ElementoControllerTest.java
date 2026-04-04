package com.qorbit.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ElementoController — Testes de integração")
class ElementoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ElementoRepository elementoRepo;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        elementoRepo.deleteAll();
    }

    private Elemento salvarElemento(String nomeLogico, String pagina, String seletor, String status) {
        Elemento e = new Elemento();
        e.setNomeLogico(nomeLogico);
        e.setPagina(pagina);
        e.setSeletorTecnico(seletor);
        e.setTipoSeletor("CSS");
        e.setStatus(status);
        return elementoRepo.save(e);
    }

    // ── GET /api/elementos ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/elementos — deve retornar lista vazia quando não há elementos")
    void getElementos_deveRetornarListaVazia() throws Exception {
        mockMvc.perform(get("/api/elementos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/elementos — deve retornar todos os elementos cadastrados")
    void getElementos_deveRetornarTodosOsElementos() throws Exception {
        salvarElemento("BotaoSalvar", "Cadastro", "#btn-salvar", "ATIVO");
        salvarElemento("CampoNome",   "Cadastro", "input[name=n]", "ATIVO");

        mockMvc.perform(get("/api/elementos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].nomeLogico", containsInAnyOrder("BotaoSalvar", "CampoNome")));
    }

    @Test
    @DisplayName("GET /api/elementos?busca=Botao — deve filtrar por nome lógico")
    void getElementos_deveFiltrarPorBusca() throws Exception {
        salvarElemento("BotaoSalvar",  "Cadastro", "#btn1", "ATIVO");
        salvarElemento("BotaoCancelar","Cadastro", "#btn2", "ATIVO");
        salvarElemento("CampoEmail",   "Cadastro", "#inp",  "ATIVO");

        mockMvc.perform(get("/api/elementos").param("busca", "Botao"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/elementos?pagina=Cadastro — deve filtrar por página")
    void getElementos_deveFiltrarPorPagina() throws Exception {
        salvarElemento("BotaoSalvar", "Cadastro",  "#btn1", "ATIVO");
        salvarElemento("MenuPrincipal","Navegacao", "#nav",  "ATIVO");

        mockMvc.perform(get("/api/elementos").param("pagina", "Cadastro"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].nomeLogico", is("BotaoSalvar")));
    }

    @Test
    @DisplayName("GET /api/elementos/paginas — deve retornar lista de páginas distintas")
    void getPaginas_deveRetornarPaginasDistintas() throws Exception {
        salvarElemento("El1", "Cadastro",   "#e1", "ATIVO");
        salvarElemento("El2", "Cadastro",   "#e2", "ATIVO");
        salvarElemento("El3", "Navegacao",  "#e3", "ATIVO");

        mockMvc.perform(get("/api/elementos/paginas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$", containsInAnyOrder("Cadastro", "Navegacao")));
    }

    // ── POST /api/elementos ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/elementos — deve criar elemento com dados válidos")
    void postElemento_deveCriarComDadosValidos() throws Exception {
        Elemento novo = new Elemento();
        novo.setNomeLogico("NovoBotao");
        novo.setPagina("PaginaTeste");
        novo.setSeletorTecnico("#novo-btn");
        novo.setTipoSeletor("CSS");

        mockMvc.perform(post("/api/elementos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(novo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.nomeLogico", is("NovoBotao")))
            .andExpect(jsonPath("$.status", is("ATIVO")));

        assertThat(elementoRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/elementos — deve rejeitar nome lógico duplicado na mesma página")
    void postElemento_deveRejeitarDuplicata() throws Exception {
        salvarElemento("BotaoSalvar", "Cadastro", "#btn1", "ATIVO");

        Elemento duplicado = new Elemento();
        duplicado.setNomeLogico("BotaoSalvar");
        duplicado.setPagina("Cadastro");
        duplicado.setSeletorTecnico("#outro-btn");
        duplicado.setTipoSeletor("CSS");

        mockMvc.perform(post("/api/elementos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicado)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erro", containsString("já existe")));
    }

    @Test
    @DisplayName("POST /api/elementos — deve permitir mesmo nome lógico em páginas diferentes")
    void postElemento_devePermitirMesmoNomeEmPaginasDiferentes() throws Exception {
        salvarElemento("BotaoSalvar", "PaginaA", "#btn1", "ATIVO");

        Elemento outraPagina = new Elemento();
        outraPagina.setNomeLogico("BotaoSalvar");
        outraPagina.setPagina("PaginaB");   // página diferente — deve ser permitido
        outraPagina.setSeletorTecnico("#btn2");
        outraPagina.setTipoSeletor("CSS");

        mockMvc.perform(post("/api/elementos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(outraPagina)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // ── PUT /api/elementos/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/elementos/{id} — deve atualizar nome lógico")
    void putElemento_deveAtualizarNomeLogico() throws Exception {
        Elemento existente = salvarElemento("NomeAntigo", "Cadastro", "#btn", "PENDENTE");

        Elemento update = new Elemento();
        update.setNomeLogico("NomeNovo");

        mockMvc.perform(put("/api/elementos/" + existente.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nomeLogico", is("NomeNovo")))
            .andExpect(jsonPath("$.status", is("ATIVO")));  // status vira ATIVO ao editar
    }

    @Test
    @DisplayName("PUT /api/elementos/{id} — deve retornar 404 para ID inexistente")
    void putElemento_deveRetornar404ParaIdInexistente() throws Exception {
        Elemento update = new Elemento();
        update.setNomeLogico("Qualquer");

        mockMvc.perform(put("/api/elementos/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/elementos/{id} — deve rejeitar nome duplicado ao renomear")
    void putElemento_deveRejeitarDuplicataAoRenomear() throws Exception {
        salvarElemento("BotaoSalvar",  "Cadastro", "#btn1", "ATIVO");
        Elemento alvo = salvarElemento("BotaoCancelar", "Cadastro", "#btn2", "ATIVO");

        Elemento update = new Elemento();
        update.setNomeLogico("BotaoSalvar");  // tenta renomear para nome já existente

        mockMvc.perform(put("/api/elementos/" + alvo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/elementos/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/elementos/{id} — deve remover elemento existente")
    void deleteElemento_deveRemoverElemento() throws Exception {
        Elemento existente = salvarElemento("ParaRemover", "Cadastro", "#rm", "ATIVO");

        mockMvc.perform(delete("/api/elementos/" + existente.getId()))
            .andExpect(status().isNoContent());

        assertThat(elementoRepo.findById(existente.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/elementos/{id} — deve retornar 404 para ID inexistente")
    void deleteElemento_deveRetornar404ParaIdInexistente() throws Exception {
        mockMvc.perform(delete("/api/elementos/99999"))
            .andExpect(status().isNotFound());
    }
}
