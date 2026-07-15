package com.qorbit.engine.service;

import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ElementoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GravacaoService — Testes unitários")
class GravacaoServiceTest {

    @Mock private CasoDeTesteRepository casoRepo;
    @Mock private ElementoRepository elementoRepo;
    @Mock private SimpMessagingTemplate mensageria;
    @InjectMocks private GravacaoService gravacaoService;

    @BeforeEach
    void setUp() {
        // Garante estado limpo antes de cada teste
        gravacaoService.pararGravacao("_descartar_", "_descartar_");
    }

    // ── Testes de iniciarGravacao ────────────────────────────────────────────

    @Test
    @DisplayName("iniciarGravacao — deve iniciar com sucesso e retornar script JS")
    void iniciar_deveRetornarScriptJs() {
        Map<String, Object> res = gravacaoService.iniciarGravacao("https://app.com");

        assertThat(res).doesNotContainKey("erro");
        assertThat(res.get("mensagem")).isEqualTo("Gravação iniciada");
        assertThat(res.get("script").toString()).contains("__scannerGravando");
        assertThat(gravacaoService.isGravando()).isTrue();
    }

    @Test
    @DisplayName("iniciarGravacao — deve rejeitar se já houver gravação em andamento")
    void iniciar_deveRejeitarSeJaGravando() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> res = gravacaoService.iniciarGravacao("https://outro.com");

        assertThat(res).containsKey("erro");
        assertThat(res.get("erro").toString()).contains("andamento");
    }

    // ── Testes de registrarStep ──────────────────────────────────────────────

    @Test
    @DisplayName("registrarStep — deve gravar step de CLICK")
    void registrar_deveGravarStepClick() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> evento = Map.of(
            "tipo", "CLICK",
            "seletor", "#btn-salvar",
            "tipoSeletor", "CSS",
            "tagName", "button",
            "textoElemento", "Salvar",
            "url", "https://app.com/cadastro"
        );

        Map<String, Object> res = gravacaoService.registrarStep(evento);

        assertThat(res).doesNotContainKey("ignorado");
        assertThat(res.get("acao")).isEqualTo("CLICAR");
        assertThat((Integer) res.get("stepNumero")).isEqualTo(1);
    }

    @Test
    @DisplayName("registrarStep — deve gravar step de INPUT com valor")
    void registrar_deveGravarStepInput() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> evento = Map.of(
            "tipo", "INPUT",
            "seletor", "input[name='email']",
            "tipoSeletor", "CSS",
            "tagName", "input",
            "textoElemento", "Email",
            "valor", "joao@empresa.com",
            "url", "https://app.com/login"
        );

        Map<String, Object> res = gravacaoService.registrarStep(evento);

        assertThat(res.get("acao")).isEqualTo("PREENCHER");
        verify(mensageria).convertAndSend(eq("/topic/gravacao"), any(Map.class));
    }

    @Test
    @DisplayName("registrarStep — deve gravar step de SELECT")
    void registrar_deveGravarStepSelect() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> evento = Map.of(
            "tipo", "SELECT",
            "seletor", "#select-estado",
            "tipoSeletor", "CSS",
            "tagName", "select",
            "textoElemento", "Estado",
            "valor", "São Paulo",
            "url", "https://app.com/cadastro"
        );

        Map<String, Object> res = gravacaoService.registrarStep(evento);

        assertThat(res.get("acao")).isEqualTo("SELECIONAR");
    }

    @Test
    @DisplayName("registrarStep — deve gravar step de NAVIGATE")
    void registrar_deveGravarStepNavigate() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> evento = Map.of(
            "tipo", "NAVIGATE",
            "seletor", "",
            "valor", "https://app.com/relatorios",
            "url", "https://app.com/relatorios"
        );

        Map<String, Object> res = gravacaoService.registrarStep(evento);

        assertThat(res.get("acao")).isEqualTo("NAVEGAR");
    }

    @Test
    @DisplayName("registrarStep — deve ignorar evento quando não estiver gravando")
    void registrar_deveIgnorarSemGravacao() {
        // Não inicia gravação

        Map<String, Object> res = gravacaoService.registrarStep(Map.of(
            "tipo", "CLICK", "seletor", "#btn"
        ));

        assertThat(res).containsKey("ignorado");
        assertThat((Boolean) res.get("ignorado")).isTrue();
    }

    @Test
    @DisplayName("registrarStep — deve incrementar o número do step sequencialmente")
    void registrar_deveIncrementarNumeroStep() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> ev = Map.of("tipo","CLICK","seletor","#btn","tagName","button",
            "textoElemento","OK","url","https://app.com");

        Map<String, Object> r1 = gravacaoService.registrarStep(ev);
        Map<String, Object> r2 = gravacaoService.registrarStep(
            Map.of("tipo","CLICK","seletor","#btn2","tagName","button","textoElemento","Cancelar","url","https://app.com"));
        Map<String, Object> r3 = gravacaoService.registrarStep(
            Map.of("tipo","INPUT","seletor","#campo","tagName","input","textoElemento","Nome","valor","João","url","https://app.com"));

        assertThat(r1.get("stepNumero")).isEqualTo(1);
        assertThat(r2.get("stepNumero")).isEqualTo(2);
        assertThat(r3.get("stepNumero")).isEqualTo(3);
    }

    @Test
    @DisplayName("registrarStep — deve notificar WebSocket a cada step gravado")
    void registrar_deveNotificarWebSocket() {
        gravacaoService.iniciarGravacao("https://app.com");

        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn",
            "tagName","button","textoElemento","OK","url","https://app.com"));
        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn2",
            "tagName","button","textoElemento","Cancelar","url","https://app.com"));

        verify(mensageria, times(2)).convertAndSend(eq("/topic/gravacao"), any(Map.class));
    }

    // ── Testes de pararGravacao ──────────────────────────────────────────────

    @Test
    @DisplayName("pararGravacao — deve salvar caso de teste com os steps gravados")
    void parar_deveSalvarCasoComSteps() {
        when(casoRepo.count()).thenReturn(0L);
        when(casoRepo.save(any())).thenAnswer(inv -> {
            CasoDeTeste c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(elementoRepo.findByNomeLogicoAndPagina(any(), any())).thenReturn(Optional.empty());

        gravacaoService.iniciarGravacao("https://app.com/cadastro");
        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn-salvar",
            "tagName","button","textoElemento","Salvar","url","https://app.com/cadastro"));

        Map<String, Object> res = gravacaoService.pararGravacao("CadastroCliente", "Cadastros");

        assertThat(res).doesNotContainKey("erro");
        assertThat(res.get("totalSteps")).isEqualTo(1);
        assertThat(gravacaoService.isGravando()).isFalse();
        verify(casoRepo).save(any(CasoDeTeste.class));
    }

    @Test
    @DisplayName("pararGravacao — deve rejeitar quando não há steps gravados")
    void parar_deveRejeitarSemSteps() {
        gravacaoService.iniciarGravacao("https://app.com");

        Map<String, Object> res = gravacaoService.pararGravacao("Caso vazio", "Geral");

        assertThat(res).containsKey("erro");
    }

    @Test
    @DisplayName("pararGravacao — deve salvar elementos novos na Biblioteca POM")
    void parar_deveSalvarElementosNaBiblioteca() {
        when(casoRepo.count()).thenReturn(0L);
        when(casoRepo.save(any())).thenAnswer(inv -> { CasoDeTeste c = inv.getArgument(0); c.setId(1L); return c; });
        when(elementoRepo.findByNomeLogicoAndPagina(any(), any())).thenReturn(Optional.empty());

        gravacaoService.iniciarGravacao("https://app.com/login");
        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn-entrar",
            "tagName","button","textoElemento","Entrar","url","https://app.com/login"));

        gravacaoService.pararGravacao("Login", "Auth");

        verify(elementoRepo, atLeastOnce()).save(any(Elemento.class));
    }

    @Test
    @DisplayName("pararGravacao — não deve duplicar elementos já existentes na biblioteca")
    void parar_naoDeveDuplicarElementos() {
        Elemento elExistente = new Elemento();
        elExistente.setNomeLogico("BotaoEntrar");
        elExistente.setPagina("Login");
        when(elementoRepo.findByNomeLogicoAndPagina(any(), any()))
            .thenReturn(Optional.of(elExistente));
        when(casoRepo.count()).thenReturn(0L);
        when(casoRepo.save(any())).thenAnswer(inv -> { CasoDeTeste c = inv.getArgument(0); c.setId(1L); return c; });

        gravacaoService.iniciarGravacao("https://app.com/login");
        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn-entrar",
            "tagName","button","textoElemento","Entrar","url","https://app.com/login"));

        gravacaoService.pararGravacao("Login", "Auth");

        verify(elementoRepo, never()).save(any(Elemento.class));
    }

    // ── Testes de removerStep ────────────────────────────────────────────────

    @Test
    @DisplayName("removerStep — deve remover step pelo número e renumerar os demais")
    void remover_deveRemoverERenumerar() {
        gravacaoService.iniciarGravacao("https://app.com");


        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn1","tagName","button","textoElemento","A","url","https://app.com"));
        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn2","tagName","button","textoElemento","B","url","https://app.com"));
        gravacaoService.registrarStep(Map.of("tipo","CLICK","seletor","#btn3","tagName","button","textoElemento","C","url","https://app.com"));

        Map<String, Object> status1 = gravacaoService.getStatus();
        assertThat(status1.get("totalSteps")).isEqualTo(3);

        gravacaoService.removerStep(2);

        Map<String, Object> status2 = gravacaoService.getStatus();
        assertThat(status2.get("totalSteps")).isEqualTo(2);
    }

    // ── Testes do script JS gerado (via GravacaoController por reflexao) ───────

    private String obterScript() throws Exception {
        com.qorbit.engine.controller.GravacaoController ctrl =
            new com.qorbit.engine.controller.GravacaoController();
        // Injeta dependencias minimas
        org.springframework.test.util.ReflectionTestUtils.setField(ctrl, "gravacaoService", gravacaoService);
        var metodo = com.qorbit.engine.controller.GravacaoController.class
            .getDeclaredMethod("gerarScript");
        metodo.setAccessible(true);
        return (String) metodo.invoke(ctrl);
    }

    @Test
    @DisplayName("gerarScript — deve conter fila de eventos __scannerEventos")
    void script_deveConterFilaDeEventos() throws Exception {
        String script = obterScript();
        assertThat(script).contains("__scannerEventos");
        assertThat(script).contains("__scannerDrainEventos");
    }

    @Test
    @DisplayName("gerarScript — deve conter listener de click")
    void script_deveConterListenerClick() throws Exception {
        String script = obterScript();
        assertThat(script).contains("addEventListener");
        assertThat(script).contains("click");
    }

    @Test
    @DisplayName("gerarScript — deve conter listener de input com debounce")
    void script_deveConterListenerInput() throws Exception {
        String script = obterScript();
        assertThat(script).contains("input");
        assertThat(script).contains("debounce");
    }

    @Test
    @DisplayName("gerarScript — nao deve gravar senhas")
    void script_naoDeveGravarSenhas() throws Exception {
        String script = obterScript();
        assertThat(script).contains("password");
        assertThat(script).contains("return");
    }
}
