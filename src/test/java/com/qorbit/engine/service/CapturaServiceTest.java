package com.qorbit.engine.service;

import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do CapturaService.
 * O Selenium é mockado para não depender de browser real nos testes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CapturaService — Testes unitários")
class CapturaServiceTest {

    @Mock
    private ElementoRepository elementoRepo;

    @InjectMocks
    private CapturaService capturaService;

    // ── Testes da lógica de geração de nomes lógicos ─────────────────────────

    @Test
    @DisplayName("gerarNomeLogico — deve gerar CamelCase a partir do texto do botão")
    void gerarNomeLogico_deveGerarCamelCaseParaBotao() throws Exception {
        // Usa reflexão para testar método privado
        var metodo = CapturaService.class.getDeclaredMethod(
            "gerarNomeLogico", String.class, String.class,
            String.class, String.class, String.class, String.class
        );
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(
            capturaService, "button", null, null, null, "Salvar Cadastro", null
        );

        assertThat(resultado).startsWith("Botao");
        assertThat(resultado).contains("Salvar");
    }

    @Test
    @DisplayName("gerarNomeLogico — deve usar placeholder para campos de input")
    void gerarNomeLogico_deveUsarPlaceholderParaInput() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod(
            "gerarNomeLogico", String.class, String.class,
            String.class, String.class, String.class, String.class
        );
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(
            capturaService, "input", null, null, "Digite seu email", null, null
        );

        assertThat(resultado).startsWith("Campo");
        assertThat(resultado).contains("Digite");
    }

    @Test
    @DisplayName("gerarNomeLogico — deve usar ID quando disponível")
    void gerarNomeLogico_deveUsarIdQuandoDisponivel() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod(
            "gerarNomeLogico", String.class, String.class,
            String.class, String.class, String.class, String.class
        );
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(
            capturaService, "input", "btn-submit", null, null, null, null
        );

        assertThat(resultado).isNotBlank();
        assertThat(resultado).startsWith("Campo");
    }

    @Test
    @DisplayName("gerarNomeLogico — deve usar ariaLabel com prioridade")
    void gerarNomeLogico_deveUsarAriaLabel() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod(
            "gerarNomeLogico", String.class, String.class,
            String.class, String.class, String.class, String.class
        );
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(
            capturaService, "button", "btn-id", "btn-name", null, "Texto visível", "Fechar janela"
        );

        // ariaLabel tem prioridade sobre texto
        assertThat(resultado).contains("Fechar");
    }

    @Test
    @DisplayName("gerarNomeLogico — deve gerar nome automático quando sem atributos")
    void gerarNomeLogico_deveGerarNomeAutomaticoSemAtributos() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod(
            "gerarNomeLogico", String.class, String.class,
            String.class, String.class, String.class, String.class
        );
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(
            capturaService, "link", null, null, null, null, null
        );

        assertThat(resultado).startsWith("Link");
    }

    // ── Testes de prefixo por tipo ───────────────────────────────────────────

    @Test
    @DisplayName("gerarNomeLogico — botão deve ter prefixo Botao")
    void gerarNomeLogico_botaoDeveTerPrefixoBotao() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod(
            "gerarNomeLogico", String.class, String.class,
            String.class, String.class, String.class, String.class
        );
        metodo.setAccessible(true);

        String bot = (String) metodo.invoke(capturaService, "button", null, null, null, "OK", null);
        String lnk = (String) metodo.invoke(capturaService, "link",   null, null, null, "OK", null);
        String sel = (String) metodo.invoke(capturaService, "select", null, null, null, "OK", null);
        String inp = (String) metodo.invoke(capturaService, "input",  null, null, null, "OK", null);

        assertThat(bot).startsWith("Botao");
        assertThat(lnk).startsWith("Link");
        assertThat(sel).startsWith("Select");
        assertThat(inp).startsWith("Campo");
    }

    // ── Testes de extração de nome de página ─────────────────────────────────

    @Test
    @DisplayName("extrairNomePagina — deve extrair o nome da última parte da URL")
    void extrairNomePagina_deveExtrairNome() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod("extrairNomePagina", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(capturaService, "https://app.empresa.com/cadastro");
        assertThat(resultado).isEqualToIgnoringCase("Cadastro");
    }

    @Test
    @DisplayName("extrairNomePagina — deve retornar PaginaInicial para URL raiz")
    void extrairNomePagina_deveRetornarPaginaInicialParaRaiz() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod("extrairNomePagina", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(capturaService, "https://app.empresa.com/");
        assertThat(resultado).isEqualTo("PaginaInicial");
    }

    @Test
    @DisplayName("extrairNomePagina — deve retornar PaginaDesconhecida para URL inválida")
    void extrairNomePagina_deveRetornarDesconhecidaParaUrlInvalida() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod("extrairNomePagina", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(capturaService, "url-invalida");
        assertThat(resultado).isEqualTo("PaginaDesconhecida");
    }

    // ── Testes de extração de domínio ────────────────────────────────────────

    @Test
    @DisplayName("extrairDominio — deve retornar protocolo + host")
    void extrairDominio_deveRetornarProtocoloEHost() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod("extrairDominio", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(capturaService, "https://app.empresa.com/cadastro/form");
        assertThat(resultado).isEqualTo("https://app.empresa.com");
    }

    @Test
    @DisplayName("extrairDominio — deve retornar a URL original se inválida")
    void extrairDominio_deveRetornarUrlOriginalSeInvalida() throws Exception {
        var metodo = CapturaService.class.getDeclaredMethod("extrairDominio", String.class);
        metodo.setAccessible(true);

        String urlInvalida = "nao-e-uma-url";
        String resultado   = (String) metodo.invoke(capturaService, urlInvalida);
        assertThat(resultado).isEqualTo(urlInvalida);
    }
}
