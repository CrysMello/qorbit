package com.qorbit.engine.selenium;

import com.qorbit.engine.model.*;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import com.qorbit.engine.service.ExecutionReportService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeleniumWorker — Testes unitários da lógica de execução")
class SeleniumWorkerTest {

    @Mock private DriverManager          driverManager;
    @Mock private ScreenshotService      screenshotService;
    @Mock private ElementoRepository     elementoRepo;
    @Mock private ExecucaoRepository     execucaoRepo;
    @Mock private ExecutionReportService reportService;
    @Mock private SimpMessagingTemplate  mensageria;

    // Mock com suporte a JavascriptExecutor (necessário para WebDriverWait)
    private WebDriver mockDriver;

    @InjectMocks
    private SeleniumWorker seleniumWorker;

    @BeforeEach
    void setUp() {
        mockDriver = mock(WebDriver.class,
                withSettings().extraInterfaces(JavascriptExecutor.class));

        ReflectionTestUtils.setField(seleniumWorker, "timeoutPadrao", 5);
        ReflectionTestUtils.setField(seleniumWorker, "fecharBrowser",  true);
        ReflectionTestUtils.setField(seleniumWorker, "retryPorStep",   1);
        ReflectionTestUtils.setField(seleniumWorker, "mensageria",     mensageria);

        when(reportService.novaLista()).thenReturn(new ArrayList<>());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void mockDriverPronto() throws Exception {
        when(driverManager.iniciar(anyString())).thenReturn(mockDriver);
        doNothing().when(mockDriver).get(anyString());
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenReturn("complete");
        when(execucaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(driverManager).encerrar();
        when(reportService.gerarRelatorio(any(), any(), any(),
                any(), any(), any(), any(), any()))
                .thenReturn("relatorio.html");
    }

    private Execucao execucaoBase(long id) {
        Execucao e = new Execucao();
        e.setId(id);
        e.setUrlAlvo("https://app.empresa.com");
        e.setStepsPAssou(0);
        e.setStepsFalhou(0);
        e.setTotalSteps(0);
        return e;
    }

    private CasoDeTeste casoVazio(String nome) {
        CasoDeTeste c = new CasoDeTeste();
        c.setNome(nome);
        c.setCodigo("CT-01");
        c.setSteps(Collections.emptyList());
        return c;
    }

    // ── Testes de extração de domínio ────────────────────────────────────────

    @Test
    @DisplayName("extrairDominio — deve retornar protocolo + host para URL completa")
    void extrairDominio_deveRetornarProtocoloEHost() throws Exception {
        var m = SeleniumWorker.class.getDeclaredMethod("extrairDominio", String.class);
        m.setAccessible(true);
        assertThat((String) m.invoke(seleniumWorker,
                "https://app.empresa.com/cadastro/novo?id=1"))
                .isEqualTo("https://app.empresa.com");
    }

    @Test
    @DisplayName("extrairDominio — deve retornar a URL original se inválida")
    void extrairDominio_deveRetornarOriginalSeInvalida() throws Exception {
        var m = SeleniumWorker.class.getDeclaredMethod("extrairDominio", String.class);
        m.setAccessible(true);
        String url = "nao-e-url";
        assertThat((String) m.invoke(seleniumWorker, url)).isEqualTo(url);
    }

    // ── Testes do fluxo de execução ──────────────────────────────────────────

    @Test
    @DisplayName("executar — deve chamar driver.get() com a URL alvo")
    void executar_deveChamarDriverGetComUrl() throws Exception {
        mockDriverPronto();
        seleniumWorker.executar(execucaoBase(1L), List.of(casoVazio("Teste vazio")), "chrome", null);
        verify(driverManager).iniciar("chrome");
        verify(mockDriver, atLeastOnce()).get("https://app.empresa.com");
    }

    @Test
    @DisplayName("executar — deve chamar encerrar() mesmo em caso de erro")
    void executar_deveChamarEncerrarMesmoComErro() {
        when(driverManager.iniciar(anyString())).thenThrow(new RuntimeException("Browser falhou"));
        when(execucaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(driverManager).encerrar();

        Execucao exec = execucaoBase(1L);
        seleniumWorker.executar(exec, Collections.emptyList(), "chrome", null);

        verify(driverManager).encerrar();
        assertThat(exec.getStatus()).isEqualTo("ERRO");
    }

    @Test
    @DisplayName("executar — deve injetar cookies quando fornecidos")
    void executar_deveInjetarCookies() throws Exception {
        mockDriverPronto();
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        when(mockDriver.manage()).thenReturn(mockOptions);

        org.openqa.selenium.Cookie cookie =
                new org.openqa.selenium.Cookie("sessid", "abc123");

        seleniumWorker.executar(execucaoBase(1L),
                List.of(casoVazio("Teste cookies")), "chrome", List.of(cookie));

        verify(mockOptions).addCookie(cookie);
    }

    @Test
    @DisplayName("executar — deve salvar execução com status CONCLUIDO ao finalizar")
    void executar_deveSalvarComStatusConcluido() throws Exception {
        mockDriverPronto();
        Execucao exec = execucaoBase(1L);
        seleniumWorker.executar(exec, List.of(casoVazio("Caso vazio")), "chrome", null);
        assertThat(exec.getStatus()).isEqualTo("CONCLUIDO");
        assertThat(exec.getTempoExecucaoSegundos()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("executar — deve notificar frontend via WebSocket ao iniciar e concluir")
    void executar_deveNotificarWebSocket() throws Exception {
        mockDriverPronto();
        seleniumWorker.executar(execucaoBase(42L), List.of(casoVazio("Caso msg")), "chrome", null);
        verify(mensageria, atLeastOnce())
                .convertAndSend(eq("/topic/execucao/42"), any(Map.class));
    }
}
