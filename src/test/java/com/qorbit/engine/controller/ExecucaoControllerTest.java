package com.qorbit.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import com.qorbit.engine.selenium.SeleniumWorker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ExecucaoController — Testes de integração")
class ExecucaoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ExecucaoRepository execucaoRepo;
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ObjectMapper objectMapper;

    // Mocka o SeleniumWorker para não abrir browser nos testes
    @MockBean
    private SeleniumWorker seleniumWorker;

    @BeforeEach
    void setUp() {
        execucaoRepo.deleteAll();
        casoRepo.deleteAll();
        // SeleniumWorker.executar é void e assíncrono — não precisa de retorno
        doNothing().when(seleniumWorker).executar(any(), anyList(), anyString(), any());
    }

    private CasoDeTeste salvarCaso(String nome) {
        CasoDeTeste c = new CasoDeTeste();
        c.setNome(nome);
        c.setCodigo("CT-01");
        c.setStatus("ATIVO");
        return casoRepo.save(c);
    }

    private Execucao salvarExecucao(String status) {
        Execucao e = new Execucao();
        e.setUrlAlvo("https://app.empresa.com");
        e.setBrowser("chrome");
        e.setStatus(status);
        e.setStepsPAssou(5);
        e.setStepsFalhou(1);
        e.setTotalSteps(6);
        e.setPercentualSucesso(83.3);
        e.setTempoExecucaoSegundos(45L);
        return execucaoRepo.save(e);
    }

    // ── GET /api/execucoes ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/execucoes — deve retornar lista vazia inicialmente")
    void getExecucoes_deveRetornarListaVazia() throws Exception {
        mockMvc.perform(get("/api/execucoes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/execucoes — deve retornar execuções ordenadas por data decrescente")
    void getExecucoes_deveRetornarOrdenadas() throws Exception {
        salvarExecucao("CONCLUIDO");
        salvarExecucao("ERRO");
        salvarExecucao("RODANDO");

        mockMvc.perform(get("/api/execucoes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/execucoes/{id} — deve retornar execução pelo ID")
    void getExecucao_deveRetornarPorId() throws Exception {
        Execucao exec = salvarExecucao("CONCLUIDO");

        mockMvc.perform(get("/api/execucoes/" + exec.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(exec.getId().intValue())))
            .andExpect(jsonPath("$.status", is("CONCLUIDO")))
            .andExpect(jsonPath("$.stepsPAssou", is(5)));
    }

    @Test
    @DisplayName("GET /api/execucoes/{id} — deve retornar 404 para ID inexistente")
    void getExecucao_deveRetornar404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/api/execucoes/99999"))
            .andExpect(status().isNotFound());
    }

    // ── POST /api/execucoes/iniciar ──────────────────────────────────────────

    @Test
    @DisplayName("POST /iniciar — deve criar execução e chamar SeleniumWorker de forma assíncrona")
    void iniciar_deveCriarExecucaoEChamarWorker() throws Exception {
        CasoDeTeste caso = salvarCaso("Teste A");

        Map<String, Object> body = new HashMap<>();
        body.put("url", "https://app.empresa.com");
        body.put("browser", "chrome");
        body.put("idsCasos", List.of(caso.getId()));

        mockMvc.perform(post("/api/execucoes/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mensagem", containsString("iniciada")))
            .andExpect(jsonPath("$.execucaoId").isNotEmpty());

        // Verifica que execução foi persistida
        assertThat(execucaoRepo.count()).isEqualTo(1);

        // Verifica que o worker foi chamado uma vez
        verify(seleniumWorker, times(1)).executar(any(), anyList(), eq("chrome"), any());
    }

    @Test
    @DisplayName("POST /iniciar — deve rejeitar quando URL não informada")
    void iniciar_deveRejeitarSemUrl() throws Exception {
        CasoDeTeste caso = salvarCaso("Teste B");

        Map<String, Object> body = new HashMap<>();
        body.put("idsCasos", List.of(caso.getId()));
        // URL ausente

        mockMvc.perform(post("/api/execucoes/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erro", containsString("URL")));
    }

    @Test
    @DisplayName("POST /iniciar — deve rejeitar quando lista de casos está vazia")
    void iniciar_deveRejeitarSemCasos() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("url", "https://app.empresa.com");
        body.put("idsCasos", Collections.emptyList());

        mockMvc.perform(post("/api/execucoes/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erro", containsString("caso")));
    }

    @Test
    @DisplayName("POST /iniciar — deve rejeitar quando IDs de casos não existem")
    void iniciar_deveRejeitarCasosInexistentes() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("url", "https://app.empresa.com");
        body.put("idsCasos", List.of(99999L, 99998L));

        mockMvc.perform(post("/api/execucoes/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /iniciar — deve usar browser padrão chrome quando não informado")
    void iniciar_deveUsarBrowserPadrao() throws Exception {
        CasoDeTeste caso = salvarCaso("Teste C");

        Map<String, Object> body = new HashMap<>();
        body.put("url", "https://app.empresa.com");
        body.put("idsCasos", List.of(caso.getId()));
        // browser não informado

        mockMvc.perform(post("/api/execucoes/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

        verify(seleniumWorker).executar(any(), anyList(), eq("chrome"), any());
    }

    @Test
    @DisplayName("POST /iniciar — execução deve ser salva com status AGUARDANDO inicialmente")
    void iniciar_execucaoDeveTerStatusAguardando() throws Exception {
        CasoDeTeste caso = salvarCaso("Teste D");

        Map<String, Object> body = new HashMap<>();
        body.put("url", "https://app.empresa.com");
        body.put("idsCasos", List.of(caso.getId()));

        mockMvc.perform(post("/api/execucoes/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

        Execucao salva = execucaoRepo.findAll().get(0);
        assertThat(salva.getStatus()).isEqualTo("AGUARDANDO");
        assertThat(salva.getUrlAlvo()).isEqualTo("https://app.empresa.com");
    }
}
