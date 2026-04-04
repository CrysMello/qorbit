package com.qorbit.engine.service;

import com.qorbit.engine.model.*;
import com.qorbit.engine.repository.ElementoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeradorCodigoService — Testes unitários")
class GeradorCodigoServiceTest {

    @Mock
    private ElementoRepository elementoRepo;

    @InjectMocks
    private GeradorCodigoService geradorService;

    private List<Elemento> elementos;
    private List<CasoDeTeste> casos;

    @BeforeEach
    void setUp() {
        // Monta biblioteca POM fake
        Elemento el1 = new Elemento();
        el1.setNomeLogico("BotaoSalvar");
        el1.setPagina("CadastroCliente");
        el1.setTipoSeletor("CSS");
        el1.setSeletorTecnico("#btn-salvar");
        el1.setDescricao("Botao que salva o cadastro");

        Elemento el2 = new Elemento();
        el2.setNomeLogico("CampoNome");
        el2.setPagina("CadastroCliente");
        el2.setTipoSeletor("XPATH");
        el2.setSeletorTecnico("//input[@name='nome']");

        Elemento el3 = new Elemento();
        el3.setNomeLogico("CampoEmail");
        el3.setPagina("CadastroCliente");
        el3.setTipoSeletor("CSS");
        el3.setSeletorTecnico("input[type='email']");

        elementos = List.of(el1, el2, el3);

        // Monta caso de teste fake
        StepTeste step1 = new StepTeste();
        step1.setNumeroStep(1);
        step1.setAcao("NAVEGAR");
        step1.setValorEntrada("https://app.empresa.com/cadastro");
        step1.setDescricaoGherkin("acesso a URL \"https://app.empresa.com/cadastro\"");

        StepTeste step2 = new StepTeste();
        step2.setNumeroStep(2);
        step2.setAcao("PREENCHER");
        step2.setNomeLogicoElemento("CampoNome");
        step2.setValorEntrada("João Silva");
        step2.setDescricaoGherkin("preencho CampoNome com \"João Silva\"");

        StepTeste step3 = new StepTeste();
        step3.setNumeroStep(3);
        step3.setAcao("CLICAR");
        step3.setNomeLogicoElemento("BotaoSalvar");
        step3.setDescricaoGherkin("clico em BotaoSalvar");

        StepTeste step4 = new StepTeste();
        step4.setNumeroStep(4);
        step4.setAcao("VALIDAR");
        step4.setNomeLogicoElemento("MensagemSucesso");
        step4.setDescricaoGherkin("MensagemSucesso esta visivel");

        CasoDeTeste caso = new CasoDeTeste();
        caso.setId(1L);
        caso.setCodigo("CT-01");
        caso.setNome("CadastroCliente completo");
        caso.setModulo("Cadastros");
        caso.setSteps(List.of(step1, step2, step3, step4));

        casos = List.of(caso);
    }

    // ── Testes da geração do .zip ────────────────────────────────────────────

    @Test
    @DisplayName("gerarZip — deve gerar arquivo .zip não vazio")
    void gerarZip_deveGerarArquivoNaoVazio() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zip = geradorService.gerarZip(casos);

        assertThat(zip).isNotNull();
        assertThat(zip.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("gerarZip — deve conter pom.xml")
    void gerarZip_deveConterPomXml() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.endsWith("pom.xml"));
    }

    @Test
    @DisplayName("gerarZip — deve conter arquivo .feature com Gherkin")
    void gerarZip_deveConterFeatureGherkin() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.endsWith(".feature"));
    }

    @Test
    @DisplayName("gerarZip — deve conter Page Object (POM) por página")
    void gerarZip_deveConterPageObject() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.contains("pages/") && e.endsWith("Page.java"));
    }

    @Test
    @DisplayName("gerarZip — deve conter StepDefinitions.java")
    void gerarZip_deveConterStepDefinitions() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.contains("steps/") && e.endsWith("Steps.java"));
    }

    @Test
    @DisplayName("gerarZip — deve conter TestRunner.java")
    void gerarZip_deveConterTestRunner() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.endsWith("TestRunner.java"));
    }

    @Test
    @DisplayName("gerarZip — deve conter Hooks.java")
    void gerarZip_deveConterHooks() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.endsWith("Hooks.java"));
    }

    @Test
    @DisplayName("gerarZip — deve conter DriverFactory.java")
    void gerarZip_deveConterDriverFactory() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.endsWith("DriverFactory.java"));
    }

    @Test
    @DisplayName("gerarZip — deve conter README.md")
    void gerarZip_deveConterReadme() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        List<String> entradas = listarEntradasZip(zipBytes);

        assertThat(entradas).anyMatch(e -> e.endsWith("README.md"));
    }

    // ── Testes do conteúdo do Page Object ────────────────────────────────────

    @Test
    @DisplayName("gerarZip — Page Object deve conter seletores CSS dos elementos")
    void gerarZip_pageObjectDeveConterSeletores() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoPage = lerArquivoDoZip(zipBytes, "Page.java");

        assertThat(conteudoPage).contains("By.cssSelector");
        assertThat(conteudoPage).contains("#btn-salvar");
        assertThat(conteudoPage).contains("botaoSalvar");  // método CamelCase
    }

    @Test
    @DisplayName("gerarZip — Page Object deve conter seletor XPath")
    void gerarZip_pageObjectDeveConterXpath() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoPage = lerArquivoDoZip(zipBytes, "Page.java");

        assertThat(conteudoPage).contains("By.xpath");
    }

    // ── Testes do conteúdo do arquivo .feature ───────────────────────────────

    @Test
    @DisplayName("gerarZip — .feature deve conter diretiva de linguagem pt")
    void gerarZip_featureDeveConterLanguagePt() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoFeature = lerArquivoDoZip(zipBytes, ".feature");

        assertThat(conteudoFeature).contains("# language: pt");
    }

    @Test
    @DisplayName("gerarZip — .feature deve conter os steps em Gherkin")
    void gerarZip_featureDeveConterStepsGherkin() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoFeature = lerArquivoDoZip(zipBytes, ".feature");

        assertThat(conteudoFeature).contains("Funcionalidade:");
        assertThat(conteudoFeature).contains("Cenario:");
        assertThat(conteudoFeature).contains("acesso a URL");
        assertThat(conteudoFeature).contains("clico em BotaoSalvar");
    }

    @Test
    @DisplayName("gerarZip — .feature não deve conter seletores técnicos (apenas nomes lógicos)")
    void gerarZip_featureNaoDeveConterSeletoresTecnicos() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoFeature = lerArquivoDoZip(zipBytes, ".feature");

        // O .feature deve referenciar nomes lógicos, nunca seletores técnicos
        assertThat(conteudoFeature).doesNotContain("#btn-salvar");
        assertThat(conteudoFeature).doesNotContain("By.cssSelector");
        assertThat(conteudoFeature).doesNotContain("xpath");
    }


    @Test
    @DisplayName("gerarZip — StepDefinitions deve parametrizar URL e campos com {string}")
    void gerarZip_stepDefinitionsDeveParametrizarValores() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoSteps = lerArquivoDoZip(zipBytes, "Steps.java");

        assertThat(conteudoSteps).contains("@Dado(\"acesso a URL {string}\")");
        assertThat(conteudoSteps).contains("public void step1(String valorStep)");
        assertThat(conteudoSteps).contains(".abrir(valorStep);");
        assertThat(conteudoSteps).contains("@E(\"preencho CampoNome com {string}\")");
        assertThat(conteudoSteps).contains("public void step2(String valorStep)");
        assertThat(conteudoSteps).contains(".preencher(valorStep,");
    }
    // ── Testes do pom.xml gerado ─────────────────────────────────────────────

    @Test
    @DisplayName("gerarZip — pom.xml deve conter dependência do Selenium")
    void gerarZip_pomDeveConterSelenium() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoPom = lerArquivoDoZip(zipBytes, "pom.xml");

        assertThat(conteudoPom).contains("selenium-java");
        assertThat(conteudoPom).contains("selenium.version");
    }

    @Test
    @DisplayName("gerarZip — pom.xml deve conter dependência do Cucumber")
    void gerarZip_pomDeveConterCucumber() throws IOException {
        when(elementoRepo.findAll()).thenReturn(elementos);

        byte[] zipBytes = geradorService.gerarZip(casos);
        String conteudoPom = lerArquivoDoZip(zipBytes, "pom.xml");

        assertThat(conteudoPom).contains("cucumber-java");
        assertThat(conteudoPom).contains("cucumber-junit");
    }

    @Test
    @DisplayName("gerarZip — deve funcionar com lista de casos vazia")
    void gerarZip_deveFuncionarComListaVazia() throws IOException {
        when(elementoRepo.findAll()).thenReturn(Collections.emptyList());

        byte[] zip = geradorService.gerarZip(Collections.emptyList());

        assertThat(zip).isNotNull();
        assertThat(zip.length).isGreaterThan(0);
    }

    // ── Utilitários ──────────────────────────────────────────────────────────

    private List<String> listarEntradasZip(byte[] zipBytes) throws IOException {
        List<String> entradas = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entradas.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entradas;
    }

    private String lerArquivoDoZip(byte[] zipBytes, String sufixo) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(sufixo) || entry.getName().contains(sufixo)) {
                    return new String(zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
        return "";
    }
}
