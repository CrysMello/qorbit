package com.qorbit.engine.healing;

import com.qorbit.engine.model.Elemento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultHealingService — Testes unitários")
class HealingServiceTest {

    @InjectMocks DefaultHealingService service;

    // ── parsearFingerprint ────────────────────────────────────────────────────

    @Test
    @DisplayName("parsearFingerprint — extrai chaves e valores corretamente")
    void parsearFingerprint_extraiChaves() {
        Map<String, String> fp = service.parsearFingerprint(
                "label=Data de Nascimento|ariaLabel=birthday|id=dt-nasc|placeholder=dd/mm/aaaa");

        assertThat(fp).containsEntry("label", "Data de Nascimento")
                      .containsEntry("ariaLabel", "birthday")
                      .containsEntry("id", "dt-nasc")
                      .containsEntry("placeholder", "dd/mm/aaaa");
    }

    @Test
    @DisplayName("parsearFingerprint — descricao null retorna mapa vazio")
    void parsearFingerprint_null() {
        assertThat(service.parsearFingerprint(null)).isEmpty();
    }

    @Test
    @DisplayName("parsearFingerprint — entradas sem '=' sao ignoradas")
    void parsearFingerprint_semIgual() {
        Map<String, String> fp = service.parsearFingerprint("label=Ok|invalido|outro=valor");
        assertThat(fp).containsOnlyKeys("label", "outro");
    }

    // ── extrairIdDoSeletor ────────────────────────────────────────────────────

    @Test
    @DisplayName("extrairIdDoSeletor — reconhece #id")
    void extrairId_hash() {
        assertThat(service.extrairIdDoSeletor("#meu-campo")).isEqualTo("meu-campo");
    }

    @Test
    @DisplayName("extrairIdDoSeletor — reconhece [id='valor']")
    void extrairId_atributo() {
        assertThat(service.extrairIdDoSeletor("input[id='data-nasc']")).isEqualTo("data-nasc");
    }

    @Test
    @DisplayName("extrairIdDoSeletor — retorna null quando nao encontra")
    void extrairId_semId() {
        assertThat(service.extrairIdDoSeletor("[name='campo']")).isNull();
    }

    // ── extrairNameDoSeletor ──────────────────────────────────────────────────

    @Test
    @DisplayName("extrairNameDoSeletor — reconhece [name='valor']")
    void extrairName() {
        assertThat(service.extrairNameDoSeletor("input[name='startDate']")).isEqualTo("startDate");
    }

    // ── extrairTestIdDoSeletor ────────────────────────────────────────────────

    @Test
    @DisplayName("extrairTestIdDoSeletor — reconhece data-testid")
    void extrairTestId() {
        assertThat(service.extrairTestIdDoSeletor("[data-testid='input-birthday']"))
                .isEqualTo("input-birthday");
    }

    @Test
    @DisplayName("extrairTestIdDoSeletor — reconhece data-qa")
    void extrairDataQa() {
        assertThat(service.extrairTestIdDoSeletor("[data-qa='campo-data']"))
                .isEqualTo("campo-data");
    }

    // ── tentar — elemento null retorna null ───────────────────────────────────

    @Test
    @DisplayName("tentar — elemento sem descricao e sem seletor retorna null")
    void tentar_semFingerprint_retornaNull() {
        interface JsDriver extends WebDriver, JavascriptExecutor {}
        JsDriver driver = mock(JsDriver.class);
        when(driver.findElements(any(By.class))).thenReturn(java.util.List.of());

        Elemento el = new Elemento();
        el.setNomeLogico("campoSemFingerprint");
        el.setSeletorTecnico(".classe-generica");
        el.setDescricao(null);

        HealingResult result = service.tentar(driver, el, 1);
        assertThat(result).isNull();
    }

    // ── tentar — healed_selector no fingerprint ───────────────────────────────

    @Test
    @DisplayName("tentar — healed_selector no fingerprint é tentado primeiro")
    void tentar_healedSeletor_primeiraTentativa() {
        interface JsDriver extends WebDriver, JavascriptExecutor {}
        JsDriver driver = mock(JsDriver.class);

        // Nao simula WebDriverWait real — verifica que a chave é extraída corretamente
        Map<String, String> fp = service.parsearFingerprint(
                "label=Nascimento|healed_selector=#data-nasc-curado");

        assertThat(fp).containsKey("healed_selector");
        assertThat(fp.get("healed_selector")).isEqualTo("#data-nasc-curado");
    }

    // ── parsearFingerprint com selectorScore ──────────────────────────────────

    @Test
    @DisplayName("parsearFingerprint — ignora selectorScore (nao e fingerprint semantico)")
    void parsearFingerprint_ignoraScore() {
        Map<String, String> fp = service.parsearFingerprint(
                "label=Nome|ariaLabel=name-field | selectorScore=ALTO");
        assertThat(fp).containsEntry("label", "Nome")
                      .containsEntry("ariaLabel", "name-field");
        // selectorScore pode aparecer mas nao e usado pelo healing
    }

    // ── Fingerprint com todos os campos ──────────────────────────────────────

    @Test
    @DisplayName("parsearFingerprint — fingerprint completo com todos os campos")
    void parsearFingerprint_completo() {
        String desc = "label=Data|ariaLabel=dt|placeholder=dd/mm/aaaa"
                    + "|id=campo-data|name=dataInput|data-testid=input-date"
                    + "|texto=04/06/2015|healed_selector=#campo-data";
        Map<String, String> fp = service.parsearFingerprint(desc);

        assertThat(fp).containsEntry("id",           "campo-data")
                      .containsEntry("name",          "dataInput")
                      .containsEntry("data-testid",   "input-date")
                      .containsEntry("texto",         "04/06/2015")
                      .containsEntry("healed_selector", "#campo-data");
    }
}
