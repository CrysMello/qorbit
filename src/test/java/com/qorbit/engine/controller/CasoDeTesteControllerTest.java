package com.qorbit.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CasoDeTesteController — Testes de integração")
class CasoDeTesteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        casoRepo.deleteAll();
    }

    private CasoDeTeste salvarCaso(String nome, String modulo) {
        CasoDeTeste c = new CasoDeTeste();
        c.setNome(nome);
        c.setModulo(modulo);
        c.setCodigo("CT-" + nome.substring(0, 2).toUpperCase());
        return casoRepo.save(c);
    }

    // ── GET /api/casos ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/casos — deve retornar lista vazia inicialmente")
    void getCasos_deveRetornarListaVazia() throws Exception {
        mockMvc.perform(get("/api/casos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/casos — deve retornar todos os casos")
    void getCasos_deveRetornarTodos() throws Exception {
        salvarCaso("CadastroCliente completo", "Cadastros");
        salvarCaso("Login com credenciais", "Autenticacao");

        mockMvc.perform(get("/api/casos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/casos?busca=Login — deve filtrar por nome")
    void getCasos_deveFiltrarPorNome() throws Exception {
        salvarCaso("Login com credenciais validas", "Auth");
        salvarCaso("Login com senha incorreta",     "Auth");
        salvarCaso("CadastroCliente",               "Cadastro");

        mockMvc.perform(get("/api/casos").param("busca", "Login"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/casos/modulos — deve retornar módulos distintos")
    void getModulos_deveRetornarModulosDistintos() throws Exception {
        salvarCaso("Caso A", "Cadastros");
        salvarCaso("Caso B", "Cadastros");
        salvarCaso("Caso C", "Autenticacao");

        mockMvc.perform(get("/api/casos/modulos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── POST /api/casos ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/casos — deve criar caso com código automático CT-XX")
    void postCaso_deveCriarComCodigoAutomatico() throws Exception {
        CasoDeTeste novo = new CasoDeTeste();
        novo.setNome("Meu novo caso");
        novo.setModulo("Geral");

        mockMvc.perform(post("/api/casos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(novo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.codigo", startsWith("CT-")))
            .andExpect(jsonPath("$.status", is("ATIVO")));
    }

    @Test
    @DisplayName("POST /api/casos — deve rejeitar caso sem nome")
    void postCaso_deveRejeitarSemNome() throws Exception {
        CasoDeTeste semNome = new CasoDeTeste();
        semNome.setModulo("Geral");

        mockMvc.perform(post("/api/casos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(semNome)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/casos — deve criar caso com steps e numerar automaticamente")
    void postCaso_deveCriarComStepsNumerados() throws Exception {
        StepTeste step1 = new StepTeste();
        step1.setAcao("NAVEGAR");
        step1.setValorEntrada("https://app.com");

        StepTeste step2 = new StepTeste();
        step2.setAcao("CLICAR");
        step2.setNomeLogicoElemento("BotaoLogin");

        StepTeste step3 = new StepTeste();
        step3.setAcao("VALIDAR");
        step3.setNomeLogicoElemento("MensagemBoasVindas");

        CasoDeTeste comSteps = new CasoDeTeste();
        comSteps.setNome("Fluxo de login completo");
        comSteps.setModulo("Auth");
        comSteps.setSteps(List.of(step1, step2, step3));

        mockMvc.perform(post("/api/casos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(comSteps)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps", hasSize(3)))
            .andExpect(jsonPath("$.steps[0].numeroStep", is(1)))
            .andExpect(jsonPath("$.steps[1].numeroStep", is(2)))
            .andExpect(jsonPath("$.steps[2].numeroStep", is(3)));
    }

    @Test
    @DisplayName("POST /api/casos — deve gerar Gherkin automático para steps sem descrição")
    void postCaso_deveGerarGherkinAutomatico() throws Exception {
        StepTeste stepNavegar = new StepTeste();
        stepNavegar.setAcao("NAVEGAR");
        stepNavegar.setValorEntrada("https://app.com/login");

        StepTeste stepClicar = new StepTeste();
        stepClicar.setAcao("CLICAR");
        stepClicar.setNomeLogicoElemento("BotaoEntrar");

        CasoDeTeste caso = new CasoDeTeste();
        caso.setNome("Login simples");
        caso.setSteps(List.of(stepNavegar, stepClicar));

        String response = mockMvc.perform(post("/api/casos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(caso)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        CasoDeTeste salvo = objectMapper.readValue(response, CasoDeTeste.class);
        assertThat(salvo.getSteps().get(0).getDescricaoGherkin()).contains("URL");
        assertThat(salvo.getSteps().get(1).getDescricaoGherkin()).contains("BotaoEntrar");
    }

    // ── PUT /api/casos/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/casos/{id} — deve atualizar nome e módulo")
    void putCaso_deveAtualizarDados() throws Exception {
        CasoDeTeste existente = salvarCaso("Nome antigo", "Modulo antigo");

        CasoDeTeste update = new CasoDeTeste();
        update.setNome("Nome novo");
        update.setModulo("Modulo novo");

        mockMvc.perform(put("/api/casos/" + existente.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome", is("Nome novo")))
            .andExpect(jsonPath("$.modulo", is("Modulo novo")));
    }

    @Test
    @DisplayName("PUT /api/casos/{id} — deve retornar 404 para ID inexistente")
    void putCaso_deveRetornar404() throws Exception {
        CasoDeTeste update = new CasoDeTeste();
        update.setNome("Qualquer");

        mockMvc.perform(put("/api/casos/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isNotFound());
    }

    // ── DELETE /api/casos/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/casos/{id} — deve remover caso existente")
    void deleteCaso_deveRemoverCaso() throws Exception {
        CasoDeTeste existente = salvarCaso("Para deletar", "Geral");

        mockMvc.perform(delete("/api/casos/" + existente.getId()))
            .andExpect(status().isNoContent());

        assertThat(casoRepo.findById(existente.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/casos/{id} — deve retornar 404 para ID inexistente")
    void deleteCaso_deveRetornar404() throws Exception {
        mockMvc.perform(delete("/api/casos/99999"))
            .andExpect(status().isNotFound());
    }
}
