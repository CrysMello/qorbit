package com.qorbit.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.qorbit.engine.gerador.NormalizadorNomes;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.*;

@Service
public class CapturaInteligenteService {

    @Autowired private NormalizadorNomes       normalizadorNomes;
    @Autowired private UiWaitService           uiWaitService;
    @Autowired private IframeScannerService    iframeScannerService;
    @Autowired private ShadowDomScannerService shadowDomScannerService;
    @Autowired private AIPluginService         aiPluginService;
    @Autowired private ElementoRepository      elementoRepo;

    private static final int MAX_SNAPSHOTS = 3;

    public List<Elemento> capturarElementos(WebDriver driver, String urlAtual) {
        uiWaitService.aguardarPaginaPronta(driver);
        uiWaitService.aguardarUiEstavel(driver);
        uiWaitService.aguardarFramesProntos(driver);

        Map<String, Elemento> unicos = new LinkedHashMap<>();
        Set<String> nomesUsados = new LinkedHashSet<>();

        // ── Captura principal ─────────────────────────────────────────────────
        iframeScannerService.percorrerFrames(driver, (driverNoFrame, framePath) -> {
            for (int tentativa = 0; tentativa < MAX_SNAPSHOTS; tentativa++) {
                if (tentativa > 0) {
                    uiWaitService.pequenaPausa();
                    uiWaitService.aguardarUiEstavel(driverNoFrame);
                }
                List<Map<String, Object>> itens = shadowDomScannerService.capturarNoContextoAtual(driverNoFrame);
                for (Map<String, Object> item : itens) {
                    registrarElemento(item, framePath, urlAtual, unicos, nomesUsados, driverNoFrame);
                }
            }
        });

        // ── MELHORIA 4: Detecção automática de abas/accordions via IA ────────
        if (aiPluginService.isHabilitado()) {
            try {
                String html = (String) ((JavascriptExecutor) driver)
                        .executeScript("return document.body.innerHTML.substring(0, 6000)");
                Map<String, Object> resultadoAbas = aiPluginService.detectarAbas(html, urlAtual);
                Object abasObj = resultadoAbas.get("abas");

                if (abasObj instanceof JsonNode abasNode && abasNode.isArray() && abasNode.size() > 0) {
                    System.out.println("[Qorbit AI] Detectadas " + abasNode.size() + " abas/accordions. Capturando elementos escondidos...");

                    for (JsonNode aba : abasNode) {
                        String seletor = aba.path("seletor").asText();
                        String descricao = aba.path("descricao").asText("aba");
                        if (seletor == null || seletor.isBlank()) continue;

                        try {
                            WebElement botaoAba = new WebDriverWait(driver, Duration.ofSeconds(5))
                                    .until(ExpectedConditions.elementToBeClickable(By.cssSelector(seletor)));
                            botaoAba.click();
                            uiWaitService.aguardarUiEstavel(driver);
                            System.out.println("[Qorbit AI] Clicou na aba: " + descricao + " (" + seletor + ")");

                            // Captura elementos que ficaram visíveis após o clique
                            iframeScannerService.percorrerFrames(driver, (driverNoFrame, framePath) -> {
                                List<Map<String, Object>> itens = shadowDomScannerService.capturarNoContextoAtual(driverNoFrame);
                                for (Map<String, Object> item : itens) {
                                    registrarElemento(item, framePath, urlAtual, unicos, nomesUsados, driverNoFrame);
                                }
                            });
                        } catch (Exception e) {
                            System.out.println("[Qorbit AI] Não foi possível clicar na aba '" + descricao + "': " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Qorbit AI] Erro na detecção de abas: " + e.getMessage());
            }
        }

        List<Elemento> resultado = new ArrayList<>(unicos.values());

        // ── MELHORIA 3: Naming automático após captura ────────────────────────
        if (aiPluginService.isHabilitado() && aiPluginService.isAutoNamingHabilitado() && !resultado.isEmpty()) {
            try {
                System.out.println("[Qorbit AI] A melhorar nomes de " + resultado.size() + " elementos...");
                elementoRepo.saveAll(resultado);
                Map<String, Object> namingResult = aiPluginService.melhorarNomesElementos();
                int renomeados = (int) namingResult.getOrDefault("renomeados", 0);
                System.out.println("[Qorbit AI] Naming automático concluído: " + renomeados + " elementos renomeados.");
                return elementoRepo.findAll();
            } catch (Exception e) {
                System.err.println("[Qorbit AI] Erro no naming automático: " + e.getMessage());
            }
        }

        return resultado;
    }

    public void aplicarCookies(WebDriver driver, String url, List<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        try {
            driver.get(extrairDominio(url));
            for (Cookie cookie : cookies) {
                try { driver.manage().addCookie(cookie); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private void registrarElemento(Map<String, Object> item,
                                   String framePath,
                                   String urlAtual,
                                   Map<String, Elemento> unicos,
                                   Set<String> nomesUsados,
                                   WebDriver driver) {
        String seletor        = str(item.get("seletorPrincipal"));
        String tipoSeletor    = str(item.get("tipoSeletorPrincipal"));
        String backup         = str(item.get("seletorBackup"));
        String componentType  = str(item.get("componentType"));
        String containerContext = str(item.get("containerContext"));

        String chave = String.join("|",
                safe(tipoSeletor), safe(seletor), safe(backup), safe(componentType), safe(containerContext), safe(framePath));
        if ((seletor.isBlank() && backup.isBlank()) || unicos.containsKey(chave)) return;

        uiWaitService.aguardarComponenteEstavel(driver, componentType);
        String nomeLogico = gerarNomeLogico(item, nomesUsados, framePath);

        Elemento elemento = new Elemento();
        elemento.setNomeLogico(nomeLogico);
        elemento.setPagina(extrairNomePagina(urlAtual));
        elemento.setTipoSeletor(tipoSeletor.isBlank() ? "XPATH" : tipoSeletor);
        elemento.setSeletorTecnico(!seletor.isBlank() ? seletor : backup);
        elemento.setDescricao(montarDescricao(item, framePath));
        elemento.setStatus("PENDENTE");
        unicos.put(chave, elemento);
    }

    private String gerarNomeLogico(Map<String, Object> item, Set<String> nomesUsados, String framePath) {
        String componentType = str(item.get("componentType"));
        String base = primeiroNaoVazio(
                str(item.get("nomeBase")),
                str(item.get("dataTestId")),
                str(item.get("ariaLabel")),
                str(item.get("text")),
                str(item.get("name")),
                str(item.get("id")),
                componentType,
                "Elemento");

        String prefixo = switch (componentType) {
            case "BUTTON", "MODAL_ACTION" -> "botao";
            case "SELECT", "CUSTOM_SELECT" -> "select";
            case "AUTOCOMPLETE" -> "autoComplete";
            case "DATEPICKER" -> "data";
            case "TEXTAREA" -> "texto";
            case "LINK" -> "link";
            case "CHECKBOX" -> "checkbox";
            case "RADIO" -> "radio";
            case "EMAIL" -> "email";
            case "PASSWORD" -> "senha";
            case "TEL" -> "telefone";
            case "NUMBER" -> "numero";
            case "MODAL_FIELD" -> "campoModal";
            default -> "campo";
        };

        if (framePath != null && !"root".equalsIgnoreCase(framePath) && !framePath.isBlank()) {
            base = base + " " + framePath.replace('>', ' ');
        }

        String nomeBase = normalizadorNomes.normalizarNomeLogico(base, prefixo);
        return normalizadorNomes.tornarUnico(nomeBase, nomesUsados);
    }

    private String montarDescricao(Map<String, Object> item, String framePath) {
        List<String> partes = new ArrayList<>();
        addIfPresent(partes, "componentType", item.get("componentType"));
        addIfPresent(partes, "container", item.get("containerContext"));
        addIfPresent(partes, "role", item.get("role"));
        addIfPresent(partes, "ariaLabel", item.get("ariaLabel"));
        addIfPresent(partes, "ariaExpanded", item.get("ariaExpanded"));
        addIfPresent(partes, "ariaAutocomplete", item.get("ariaAutocomplete"));
        addIfPresent(partes, "dataTestId", item.get("dataTestId"));
        addIfPresent(partes, "texto", item.get("text"));
        addIfPresent(partes, "frame", framePath);
        addIfPresent(partes, "backup", item.get("seletorBackup"));
        if (Boolean.TRUE.equals(item.get("inShadowDom"))) partes.add("shadowDom=true");
        String descricao = String.join(" | ", partes);
        return descricao.length() > 200 ? descricao.substring(0, 200) : descricao;
    }

    private void addIfPresent(List<String> partes, String chave, Object valor) {
        String texto = str(valor);
        if (!texto.isBlank()) partes.add(chave + "=" + texto);
    }

    private String extrairNomePagina(String url) {
        if (url == null || url.isBlank()) return "PaginaInicial";
        try {
            URI uri = URI.create(url);
            String path = Optional.ofNullable(uri.getPath()).orElse("").trim();
            if (path.isBlank() || "/".equals(path)) return "PaginaInicial";
            String[] partes = path.split("/");
            for (int i = partes.length - 1; i >= 0; i--) {
                if (!partes[i].isBlank()) return normalizadorNomes.normalizarClasse(partes[i], "PaginaInicial");
            }
        } catch (Exception ignored) { }
        return "PaginaInicial";
    }

    private String extrairDominio(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) { return url; }
    }

    private String str(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String primeiroNaoVazio(String... values) {
        for (String value : values) { if (value != null && !value.trim().isEmpty()) return value.trim(); }
        return "";
    }
    private String safe(String value) { return value == null ? "" : value; }
}
