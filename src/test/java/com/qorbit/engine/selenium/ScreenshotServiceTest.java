package com.qorbit.engine.selenium;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScreenshotService — Testes unitários")
class ScreenshotServiceTest {

    @InjectMocks
    private ScreenshotService screenshotService;

    @TempDir
    Path pastaTemp;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(screenshotService, "basePath", pastaTemp.toString());
    }

    @Test
    @DisplayName("capturar — deve salvar arquivo PNG com nomenclatura correta")
    void capturar_deveSalvarArquivoPngComNomenclaturaCorreta() throws Exception {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        byte[] fakeScreenshot = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47}; // PNG magic bytes
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(fakeScreenshot);

        String caminho = screenshotService.capturar(mockDriver, "CT-01", "CadastroCliente", 3, "PASSOU");

        assertThat(caminho).isNotBlank();
        assertThat(caminho).endsWith(".png");
        assertThat(Files.exists(Paths.get(caminho))).isTrue();
    }

    @Test
    @DisplayName("capturar — nome do arquivo deve conter ID, nome, número do step e status")
    void capturar_nomeDeveConterTodasAsInformacoes() throws Exception {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(new byte[]{1, 2, 3});

        String caminho = screenshotService.capturar(mockDriver, "CT-05", "LoginSistema", 2, "FALHOU");

        assertThat(caminho).contains("CT-05");
        assertThat(caminho).contains("LoginSistema");
        assertThat(caminho).contains("02");
        assertThat(caminho).contains("FALHOU");
    }

    @Test
    @DisplayName("capturar — deve criar estrutura de pastas data/nomeTeste/")
    void capturar_deveCriarEstruturaDePastas() throws Exception {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(new byte[]{1, 2, 3});

        String caminho = screenshotService.capturar(mockDriver, "CT-01", "CadastroCliente", 1, "PASSOU");

        Path arquivo = Paths.get(caminho);
        assertThat(arquivo.getParent()).isNotNull();
        assertThat(Files.isDirectory(arquivo.getParent())).isTrue();
        // Estrutura: basePath/yyyy-MM-dd/CT-01_CadastroCliente/
        assertThat(arquivo.getParent().getFileName().toString()).contains("CT-01");
    }

    @Test
    @DisplayName("capturar — deve salvar o conteúdo correto do screenshot")
    void capturar_deveSalvarConteudoCorreto() throws Exception {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        byte[] conteudoEsperado = {10, 20, 30, 40, 50};
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(conteudoEsperado);

        String caminho = screenshotService.capturar(mockDriver, "CT-01", "Teste", 1, "PASSOU");

        byte[] conteudoSalvo = Files.readAllBytes(Paths.get(caminho));
        assertThat(conteudoSalvo).isEqualTo(conteudoEsperado);
    }

    @Test
    @DisplayName("capturar — deve sanitizar caracteres especiais no nome")
    void capturar_deveSanitizarCaracteresEspeciais() throws Exception {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(new byte[]{1});

        // Nome com caracteres especiais
        String caminho = screenshotService.capturar(mockDriver, "CT-01", "Tela: Login/Senha!", 1, "PASSOU");

        // Verifica apenas a parte sanitizada do caminho (nome do diretório do teste),
        // excluindo o prefixo absoluto do SO (ex.: "C:\" no Windows).
        String nomeDiretorio = Paths.get(caminho).getParent().getFileName().toString();
        assertThat(nomeDiretorio).doesNotContain(":");
        assertThat(nomeDiretorio).doesNotContain("/");
        assertThat(nomeDiretorio).doesNotContain("!");
    }

    @Test
    @DisplayName("capturar — deve funcionar com status PASSOU e FALHOU")
    void capturar_deveFuncionarComAmbosStatus() throws Exception {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(new byte[]{1});

        String passou = screenshotService.capturar(mockDriver, "CT-01", "Teste", 1, "PASSOU");
        String falhou = screenshotService.capturar(mockDriver, "CT-01", "Teste", 2, "FALHOU");

        assertThat(passou).contains("PASSOU");
        assertThat(falhou).contains("FALHOU");
    }

    @Test
    @DisplayName("capturar — deve lançar IOException se o driver falhar")
    void capturar_deveLancarExcecaoSeDriverFalhar() {
        WebDriver mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BYTES))
            .thenThrow(new WebDriverException("Driver crashed"));

        assertThatThrownBy(() ->
            screenshotService.capturar(mockDriver, "CT-01", "Teste", 1, "PASSOU")
        ).isInstanceOf(Exception.class);
    }
}
