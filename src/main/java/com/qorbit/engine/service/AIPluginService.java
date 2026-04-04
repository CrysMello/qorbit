package com.qorbit.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Plugin IA — chama qualquer API REST compatível com o formato OpenAI Chat Completions.
 * Compatível com: Groq, DeepSeek, Mistral, OpenAI, IA interna da empresa, etc.
 *
 * Configuração em application.properties:
 *   scanner.ai.habilitado=true
 *   scanner.ai.endpoint=https://api.groq.com/openai/v1/chat/completions
 *   scanner.ai.modelo=llama-3.3-70b-versatile
 *   scanner.ai.auth-tipo=bearer
 *   scanner.ai.api-key=gsk_xxx
 *
 * Funcionalidades:
 *   1. Naming inteligente — renomeia elementos genéricos com nomes semânticos
 *   2. Detecção de abas — detecta tabs/accordions com conteúdo escondido
 *   3. Diagnóstico de falhas — explica causas e sugere correcções
 *   4. Auto-healing — sugere selector alternativo quando o original falha
 */
@Service
public class AIPluginService {

    @Value("${scanner.ai.habilitado:false}")
    private boolean habilitado;

    @Value("${scanner.ai.endpoint:}")
    private String endpoint;

    @Value("${scanner.ai.modelo:}")
    private String modelo;

    @Value("${scanner.ai.auth-tipo:bearer}")
    private String authTipo;

    @Value("${scanner.ai.api-key:}")
    private String apiKey;

    @Value("${scanner.ai.auto-healing:true}")
    private boolean autoHealingHabilitado;

    @Value("${scanner.ai.auto-naming:true}")
    private boolean autoNamingHabilitado;

    @Value("${scanner.ai.auto-diagnostico:true}")
    private boolean autoDiagnosticoHabilitado;

    @Autowired
    private ElementoRepository elementoRepo;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // ─── Status ───────────────────────────────────────────────────────────────

    public Map<String, Object> status() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("habilitado", habilitado);
        info.put("endpoint", endpoint);
        info.put("modelo", modelo);
        info.put("authTipo", authTipo);
        info.put("apiKeyConfigurada", apiKey != null && !apiKey.isBlank());
        info.put("autoHealingHabilitado", autoHealingHabilitado);
        info.put("autoNamingHabilitado", autoNamingHabilitado);
        info.put("autoDiagnosticoHabilitado", autoDiagnosticoHabilitado);
        return info;
    }

    // ─── Configurar em runtime ────────────────────────────────────────────────

    public void configurar(String novoEndpoint, String novoModelo, String novoAuthTipo, String novaKey) {
        this.endpoint  = novoEndpoint;
        this.modelo    = novoModelo;
        this.authTipo  = novoAuthTipo;
        this.apiKey    = novaKey;
        this.habilitado = novoEndpoint != null && !novoEndpoint.isBlank();
    }

    // ─── Testar conexão ───────────────────────────────────────────────────────

    public Map<String, Object> testarConexao() {
        if (!habilitado || endpoint.isBlank()) {
            return erro("Plugin não configurado. Preencha o endpoint e o modelo.");
        }
        String resposta = chamarIA("Responda apenas: ok");
        if (resposta == null) return erro("Falha ao conectar. Verifique o endpoint e a autenticação.");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("mensagem", "Conexão estabelecida com sucesso.");
        r.put("modelo", modelo);
        return r;
    }

    // ─── 1. Melhorar nomes de elementos ──────────────────────────────────────

    public Map<String, Object> melhorarNomesElementos() {
        if (!habilitado) return erro("Plugin IA não habilitado. Configure nas definições.");

        List<Elemento> todos = elementoRepo.findAll();
        int renomeados = 0;
        List<String> log = new ArrayList<>();

        for (Elemento el : todos) {
            if (!nomeGenerico(el.getNomeLogico())) continue;

            String prompt = String.format("""
                Você é um especialista em automação de testes. Gere um nome em camelCase,
                curto (máx 4 palavras), semântico e em português para este elemento de UI:
                - Tipo: %s
                - Seletor: %s
                - Descrição: %s
                - Página: %s
                - Nome actual (genérico): %s
                
                Responda APENAS com o nome em camelCase, sem explicações.
                Exemplo: campoCPFContribuinte, botaoConfirmarPagamento, selectEstadoCivil
                """,
                el.getTipoSeletor(), el.getSeletorTecnico(),
                el.getDescricao(), el.getPagina(), el.getNomeLogico());

            String novoNome = chamarIA(prompt);
            if (novoNome != null && !novoNome.isBlank()) {
                novoNome = novoNome.replaceAll("[^a-zA-Z0-9]", "").trim();
                if (!novoNome.equals(el.getNomeLogico()) && novoNome.length() > 3) {
                    String nomeAntigo = el.getNomeLogico();
                    el.setNomeLogico(novoNome);
                    elementoRepo.save(el);
                    renomeados++;
                    log.add(nomeAntigo + " → " + novoNome);
                }
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("renomeados", renomeados);
        resultado.put("total", todos.size());
        resultado.put("log", log);
        return resultado;
    }

    // ─── 2. Detectar abas ─────────────────────────────────────────────────────

    public Map<String, Object> detectarAbas(String htmlResumido, String url) {
        if (!habilitado) return erro("Plugin IA não habilitado.");

        String prompt = String.format("""
            Analise este HTML da página '%s' e identifique TODOS os componentes
            que escondem conteúdo atrás de um clique: abas (tabs), accordions,
            dropdowns de navegação, wizards, carousels.
            
            Para cada um retorne JSON:
            [{"seletor":"CSS_SELECTOR","tipo":"tab|accordion|dropdown|carousel","descricao":"nome"}]
            
            Responda APENAS com o array JSON, sem explicações ou markdown.
            
            HTML:
            %s
            """, url, htmlResumido.length() > 4000 ? htmlResumido.substring(0, 4000) : htmlResumido);

        String resposta = chamarIA(prompt);
        if (resposta == null) return erro("Falha ao contatar a IA.");

        try {
            String json = resposta.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode node = mapper.readTree(json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("abas", node);
            result.put("total", node.size());
            return result;
        } catch (Exception e) {
            return Map.of("abas", List.of(), "total", 0, "raw", resposta);
        }
    }

    // ─── 3. Diagnosticar falha ────────────────────────────────────────────────

    public Map<String, Object> diagnosticarFalha(String seletor, String erroMensagem, String pagina) {
        if (!habilitado) return erro("Plugin IA não habilitado.");

        String prompt = String.format("""
            Sou um QA a usar Selenium. Um step falhou com estes dados:
            - Seletor: %s
            - Erro: %s
            - Página: %s
            
            Explica em 2-3 linhas qual é provavelmente a causa e como corrigir.
            Responde em português, de forma directa e prática.
            """, seletor, erroMensagem, pagina);

        String resposta = chamarIA(prompt);
        if (resposta == null) return erro("Falha ao contatar a IA.");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("diagnostico", resposta.trim());
        r.put("seletor", seletor);
        return r;
    }

    // ─── 4. Auto-healing de selector ─────────────────────────────────────────

    /**
     * Quando um selector falha, a IA analisa o HTML da página e sugere um
     * selector alternativo. Tenta localizar o elemento com o novo selector.
     *
     * @return WebElement encontrado com o selector alternativo, ou null se não conseguir.
     */
    public WebElement tentarAutoHealing(WebDriver driver, String seletorOriginal,
                                         String tipoSeletor, int timeoutSegundos) {
        if (!habilitado || !autoHealingHabilitado) return null;

        try {
            String html = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.body.innerHTML.substring(0, 6000)");

            String prompt = String.format("""
                Sou um QA a usar Selenium. O selector abaixo falhou na página e preciso de um alternativo.
                
                Selector original (%s): %s
                URL: %s
                
                Analisa o HTML e sugere o MELHOR selector CSS alternativo para o mesmo elemento.
                Responde APENAS com o selector CSS, sem explicações, sem aspas, sem markdown.
                
                HTML:
                %s
                """,
                tipoSeletor, seletorOriginal,
                driver.getCurrentUrl(),
                html.length() > 5000 ? html.substring(0, 5000) : html);

            String novoSeletor = chamarIA(prompt);
            if (novoSeletor == null || novoSeletor.isBlank()) return null;

            novoSeletor = novoSeletor.replaceAll("```", "").trim();
            System.out.println("[Qorbit AI] Auto-healing: '" + seletorOriginal + "' → '" + novoSeletor + "'");

            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(timeoutSegundos))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(novoSeletor)));

            System.out.println("[Qorbit AI] Auto-healing SUCESSO com selector: " + novoSeletor);
            return el;

        } catch (Exception e) {
            System.out.println("[Qorbit AI] Auto-healing falhou: " + e.getMessage());
            return null;
        }
    }

    // ─── Chamada HTTP à IA ────────────────────────────────────────────────────

    public String chamarIA(String promptUsuario) {
        if (endpoint == null || endpoint.isBlank()) return null;
        try {
            String body = mapper.writeValueAsString(Map.of(
                "model", modelo,
                "messages", List.of(Map.of("role", "user", "content", promptUsuario)),
                "max_tokens", 500,
                "temperature", 0.3
            ));

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if ("bearer".equalsIgnoreCase(authTipo) && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = http.send(req.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[Qorbit AI] Erro HTTP " + response.statusCode() + ": " + response.body());
                return null;
            }

            JsonNode json = mapper.readTree(response.body());
            return json.path("choices").path(0).path("message").path("content").asText();

        } catch (Exception e) {
            System.err.println("[Qorbit AI] Erro ao chamar IA: " + e.getMessage());
            return null;
        }
    }

    // ─── Getters para uso interno ─────────────────────────────────────────────

    public boolean isHabilitado() { return habilitado; }
    public boolean isAutoHealingHabilitado() { return autoHealingHabilitado; }
    public boolean isAutoDiagnosticoHabilitado() { return autoDiagnosticoHabilitado; }
    public boolean isAutoNamingHabilitado() { return autoNamingHabilitado; }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean nomeGenerico(String nome) {
        if (nome == null || nome.isBlank()) return true;
        return nome.matches(".*\\d+$") || nome.length() <= 6;
    }

    private Map<String, Object> erro(String mensagem) {
        return Map.of("erro", mensagem);
    }
}
