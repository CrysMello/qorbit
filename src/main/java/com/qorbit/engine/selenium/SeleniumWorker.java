package com.qorbit.engine.selenium;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.model.Evidencia;
import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.repository.EvidenciaRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import com.qorbit.engine.service.AIPluginService;
import com.qorbit.engine.service.ExecutionReportService;
import com.qorbit.engine.service.ExecutionReportService.StepLog;
import com.qorbit.engine.service.IframeScannerService;
import com.qorbit.engine.service.ShadowDomScannerService;
import com.qorbit.engine.execution.StepExecutionPipeline;
import com.qorbit.engine.execution.StepExecutionTrace;
import com.qorbit.engine.healing.HealingResult;
import com.qorbit.engine.healing.HealingService;

@Component
public class SeleniumWorker {

    @Autowired private DriverManager          driverManager;
    @Autowired private ExecucaoRepository     execucaoRepo;
    @Autowired private EvidenciaRepository    evidenciaRepo;
    @Autowired private ScreenshotService      screenshotService;
    @Autowired private ExecutionReportService reportService;
    @Autowired private ElementoRepository     elementoRepo;
    @Autowired private AIPluginService        aiPluginService;
    @Autowired private com.qorbit.engine.service.QorbitLoggerService qorbitLogger;
    @Autowired private StepExecutionPipeline  stepExecutionPipeline;
    @Autowired private HealingService         healingService;
    @Autowired private IframeScannerService   iframeScannerService;
    @Autowired private ShadowDomScannerService shadowDomScannerService;

    @Autowired(required = false)
    private SimpMessagingTemplate mensageria;

    @Value("${scanner.browser.fechar:true}")
    private boolean fecharBrowser;

    @Value("${scanner.step.retry:1}")
    private int retryPorStep;

    @Value("${scanner.timeout.padrao:10}")
    private int timeoutPadrao;

    @Value("${scanner.evidencias.path:evidencias}")
    private String evidenciasBasePath;

    @Async
    @SuppressWarnings("unchecked")
    public void executar(Execucao execucaoParam, List<CasoDeTeste> casos,
                         String browser, Object auth) {

        Long execId = execucaoParam.getId();
        System.out.println("[EXEC " + execId + "] Thread async iniciada: "
                + Thread.currentThread().getName());

        Execucao execucao = execucaoRepo.findById(execId).orElse(execucaoParam);

        WebDriver driver = null;
        List<StepLog> logs = reportService.novaLista();
        Instant inicio = Instant.now();

        try {
            int totalSteps = casos.stream()
                    .mapToInt(c -> c.getSteps() == null ? 0 : c.getSteps().size())
                    .sum();

            execucao.setStatus("RODANDO");
            execucao.setStepsPAssou(0);
            execucao.setStepsFalhou(0);
            execucao.setTotalSteps(totalSteps);
            execucao = execucaoRepo.save(execucao);
            notificar(execucao);
            System.out.println("[EXEC " + execId + "] Status -> RODANDO | totalSteps=" + totalSteps);

            qorbitLogger.execucaoIniciada(execId, execucao.getUrlAlvo(), totalSteps);
            driver = driverManager.iniciar(browser);

            if (auth instanceof List<?> list && !list.isEmpty()) {
                driver.get(extrairDominio(execucao.getUrlAlvo()));
                for (Object c : list) {
                    if (c instanceof Cookie) driver.manage().addCookie((Cookie) c);
                }
            }

            System.out.println("[EXEC " + execId + "] Abrindo: " + execucao.getUrlAlvo());
            driver.get(execucao.getUrlAlvo());
            aguardarPaginaCarregar(driver, 15);

            for (CasoDeTeste caso : casos) {
                if (caso.getSteps() == null || caso.getSteps().isEmpty()) continue;
                for (StepTeste step : caso.getSteps()) {
                    executarStep(driver, execucao, step, logs);
                    Thread.sleep(500);
                }
            }

            int p = execucao.getStepsPAssou() != null ? execucao.getStepsPAssou() : 0;
            int f = execucao.getStepsFalhou() != null ? execucao.getStepsFalhou() : 0;
            int t = p + f;
            double pct = t > 0 ? (p * 100.0 / t) : 0;

            long seg = Duration.between(inicio, Instant.now()).toSeconds();
            execucao.setTempoExecucaoSegundos(seg);
            execucao.setPercentualSucesso(pct);
            execucao.setStatus("CONCLUIDO");
            execucao.setFinalizadoEm(LocalDateTime.now().toString());
            execucao = execucaoRepo.save(execucao);
            notificar(execucao);

            gerarRelatorio(execucao, logs);
            qorbitLogger.execucaoConcluida(execId, p, f, 0, seg);
            System.out.println("[EXEC " + execId + "] CONCLUIDO em " + seg + "s"
                    + " | passou=" + p + " falhou=" + f
                    + " | pct=" + String.format("%.0f", pct) + "%");

        } catch (Exception e) {
            System.err.println("[EXEC " + execId + "] ERRO: " + e.getMessage());
            e.printStackTrace();
            try {
                long seg = Duration.between(inicio, Instant.now()).toSeconds();
                execucao.setTempoExecucaoSegundos(seg);
                execucao.setStatus("ERRO");
                execucao.setErro(limitar(e.getMessage(), 500));
                execucao.setFinalizadoEm(LocalDateTime.now().toString());
                execucao = execucaoRepo.save(execucao);
                notificar(execucao);
            } catch (Exception se) {
                System.err.println("[EXEC " + execId + "] Falha ao salvar ERRO: " + se.getMessage());
            }
            try { if (driver != null) screenshotService.capturar(driver, execId, "erro-geral"); } catch (Exception ignored) {}
            try { gerarRelatorio(execucao, logs); } catch (Exception ignored) {}

        } finally {
            try { driverManager.encerrar(); } catch (Exception ignored) {}
        }
    }

    private void executarStep(WebDriver driver, Execucao execucao,
                               StepTeste step, List<StepLog> logs) {
        String statusStep  = "FALHOU";
        String motivoFalha = null;
        String screenshot  = null;
        String diagnosticoIA = null;

        System.out.printf("[EXEC %d] Step %d - %s%s%n",
                execucao.getId(), step.getNumeroStep(), step.getAcao(),
                step.getNomeLogicoElemento() != null ? " [" + step.getNomeLogicoElemento() + "]" : "");
        qorbitLogger.stepIniciado(execucao.getId(), step.getNumeroStep(),
                step.getAcao(), step.getNomeLogicoElemento());

        for (int t = 1; t <= Math.max(1, retryPorStep); t++) {
            try {
                WebElement el = precisaElemento(step.getAcao())
                        ? resolverElemento(driver, step) : null;

                if (el != null) {
                    highlight(driver, el);
                    screenshotService.capturar(driver, execucao.getId(),
                            "step-" + step.getNumeroStep() + "-antes-t" + t);
                }

                aguardarPorTipoComponente(driver, step.getAcao(), el);
                StepExecutionTrace trace = executarAcao(driver, el, step);
                System.out.printf("[Qorbit Engine] Step %d classificado como %s (%.2f) - estrategia %s - assinatura %s%n",
                        step.getNumeroStep(),
                        trace.classification().type(),
                        trace.classification().confidence(),
                        trace.plan().strategyType(),
                        trace.signature());

                aguardarEstabilidadeVisual(driver);

                screenshot = screenshotService.capturar(driver, execucao.getId(),
                        "step-" + step.getNumeroStep() + "-ok-t" + t);
                statusStep  = "PASSOU";
                motivoFalha = null;
                diagnosticoIA = null;
                System.out.printf("[EXEC %d] Step %d PASSOU%n",
                        execucao.getId(), step.getNumeroStep());
                qorbitLogger.stepPassou(execucao.getId(), step.getNumeroStep(),
                        step.getNomeLogicoElemento() != null ? step.getNomeLogicoElemento() : "");

                break;

            } catch (Exception e) {
                motivoFalha = limitar(e.getMessage(), 400);
                screenshot  = screenshotService.capturar(driver, execucao.getId(),
                        "step-" + step.getNumeroStep() + "-falha-t" + t);
                String diagnosticoEstruturado = stepExecutionPipeline.classifyFailure(null, step, e);
                if (diagnosticoEstruturado != null && !diagnosticoEstruturado.isBlank()) {
                    motivoFalha = motivoFalha + "\n Engine: " + diagnosticoEstruturado;
                }
                System.out.printf("[EXEC %d] Step %d tentativa %d FALHOU: %s%n",
                        execucao.getId(), step.getNumeroStep(), t, motivoFalha);
                qorbitLogger.stepFalhou(execucao.getId(), step.getNumeroStep(),
                        step.getNomeLogicoElemento() != null ? step.getNomeLogicoElemento() : "",
                        motivoFalha != null ? motivoFalha : "Erro desconhecido");

                if (aiPluginService.isHabilitado() && aiPluginService.isAutoDiagnosticoHabilitado()) {
                    try {
                        Elemento elDiag = elementoRepo.findByNomeLogico(
                                step.getNomeLogicoElemento() != null ? step.getNomeLogicoElemento() : "")
                                .stream().findFirst().orElse(null);
                        String seletor = elDiag != null ? elDiag.getSeletorTecnico() : step.getNomeLogicoElemento();
                        Map<String, Object> diag = aiPluginService.diagnosticarFalha(
                                seletor, e.getMessage(), driver.getCurrentUrl());
                        diagnosticoIA = (String) diag.get("diagnostico");
                        if (diagnosticoIA != null) {
                            System.out.println("[Qorbit AI] Diagnostico: " + diagnosticoIA);
                            motivoFalha = motivoFalha + "\n IA: " + diagnosticoIA;
                        }
                    } catch (Exception diagEx) {
                        System.err.println("[Qorbit AI] Erro no diagnostico: " + diagEx.getMessage());
                    }
                }
            }
        }

        if ("PASSOU".equals(statusStep)) {
            execucao.setStepsPAssou((execucao.getStepsPAssou() == null ? 0 : execucao.getStepsPAssou()) + 1);
        } else {
            execucao.setStepsFalhou((execucao.getStepsFalhou() == null ? 0 : execucao.getStepsFalhou()) + 1);
        }

        try {
            Evidencia ev = new Evidencia();
            ev.setExecucao(execucao);
            ev.setNumeroStep(step.getNumeroStep());
            ev.setNomeStep(step.getNomeStep());
            ev.setStatusStep(statusStep);
            ev.setMotivoFalha(motivoFalha);
            if (screenshot != null) {
                ev.setNomeArquivo(screenshot);
                ev.setCaminhoArquivo(
                        java.nio.file.Paths.get(evidenciasBasePath)
                                .toAbsolutePath()
                                .resolve(screenshot)
                                .normalize()
                                .toString()
                );
            }
            evidenciaRepo.save(ev);
        } catch (Exception e) {
            System.err.println("[EXEC " + execucao.getId() + "] Erro ao salvar evidencia: " + e.getMessage());
        }

        try {
            execucaoRepo.save(execucao);
            notificar(execucao);
        } catch (Exception e) {
            System.err.println("[EXEC " + execucao.getId() + "] Erro ao salvar execucao: " + e.getMessage());
        }

        StepLog log = new StepLog();
        log.numero     = step.getNumeroStep();
        log.nome       = step.getNomeStep();
        log.acao       = step.getAcao();
        log.status     = statusStep;
        log.detalhe    = motivoFalha;
        log.screenshot = screenshot;
        logs.add(log);
    }

    private StepExecutionTrace executarAcao(WebDriver driver, WebElement el, StepTeste step) throws Exception {
        return stepExecutionPipeline.execute(driver, el, step);
    }

    private boolean precisaElemento(String acao) {
        if (acao == null) return false;
        return switch (acao.trim().toUpperCase()) {
            case "WAIT","OPEN","NAVEGAR" -> false;
            default -> true;
        };
    }

    private WebElement resolverElemento(WebDriver driver, StepTeste step) {
        By by = montarBy(step.getNomeLogicoElemento());
        Elemento el = elementoRepo.findByNomeLogico(
                step.getNomeLogicoElemento() != null ? step.getNomeLogicoElemento() : "")
                .stream().findFirst().orElse(null);

        try {
            return new WebDriverWait(driver, Duration.ofSeconds(timeoutPadrao))
                    .until(ExpectedConditions.elementToBeClickable(by));

        } catch (Exception e) {

            try {
                WebElement hidden = new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(ExpectedConditions.presenceOfElementLocated(by));
                System.out.println("[Qorbit] Elemento oculto - clicando via JS: " + step.getNomeLogicoElemento());
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", hidden);
                return hidden;
            } catch (Exception jsEx) {
                System.out.println("[Qorbit] Fallback JS no frame principal falhou: " + jsEx.getMessage());
            }

            AtomicReference<WebElement> foundInFrame = new AtomicReference<>(null);
            try {
                iframeScannerService.percorrerFrames(driver, (driverNoFrame, framePath) -> {
                    if (foundInFrame.get() != null) return;
                    try {
                        WebElement elNoFrame = new WebDriverWait(driverNoFrame, Duration.ofSeconds(3))
                                .until(ExpectedConditions.presenceOfElementLocated(by));
                        if (elNoFrame != null) {
                            System.out.println("[Qorbit] Elemento encontrado em iframe: " + framePath);
                            foundInFrame.set(elNoFrame);
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception frameEx) {
                System.out.println("[Qorbit] Busca em iframes falhou: " + frameEx.getMessage());
            }
            if (foundInFrame.get() != null) return foundInFrame.get();

            try {
                String seletorTecnico = el != null ? el.getSeletorTecnico() : null;
                if (seletorTecnico != null && !seletorTecnico.isBlank()) {
                    Object result = ((JavascriptExecutor) driver).executeScript(
                        "function findInShadow(root, selector) {" +
                        "  try { const el = root.querySelector(selector); if (el) return el; } catch(e) {}" +
                        "  const all = root.querySelectorAll('*');" +
                        "  for (const el of all) {" +
                        "    if (el.shadowRoot) {" +
                        "      const found = findInShadow(el.shadowRoot, selector);" +
                        "      if (found) return found;" +
                        "    }" +
                        "  }" +
                        "  return null;" +
                        "}" +
                        "return findInShadow(document, arguments[0]);",
                        seletorTecnico);
                    if (result instanceof WebElement shadowEl) {
                        System.out.println("[Qorbit] Elemento encontrado em Shadow DOM");
                        return shadowEl;
                    }
                }
            } catch (Exception shadowEx) {
                System.out.println("[Qorbit] Busca em Shadow DOM falhou: " + shadowEx.getMessage());
            }

            if (el != null) {
                System.out.println("[Qorbit] Selector original falhou - iniciando self-healing: "
                        + step.getNomeLogicoElemento());
                HealingResult result = healingService.tentar(driver, el, timeoutPadrao);
                if (result != null) {
                    try {
                        String descAtual = el.getDescricao() != null ? el.getDescricao() : "";
                        String semHealed = descAtual.replaceAll("\\s*\\|?\\s*healed_selector=[^|]*", "").trim();
                        String novoDesc  = semHealed + " | healed_selector=" + result.getNovoSeletor();
                        el.setDescricao(novoDesc.length() > 200 ? novoDesc.substring(0, 200) : novoDesc);
                        elementoRepo.save(el);
                        System.out.println("[Qorbit] Self-Healing persistido no DB ("
                                + result.getEstrategia() + ")");
                    } catch (Exception saveEx) {
                        System.err.println("[Qorbit] Erro ao persistir seletor curado: " + saveEx.getMessage());
                    }
                    return result.getElemento();
                }
            }

            throw e;
        }
    }

    private By montarBy(String nomeLogico) {
        if (nomeLogico == null || nomeLogico.isBlank())
            throw new IllegalArgumentException("nomeLogicoElemento nao informado");
        Elemento el = elementoRepo.findByNomeLogico(nomeLogico).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Elemento nao encontrado: " + nomeLogico));
        String tipo = el.getTipoSeletor(), val = el.getSeletorTecnico();
        if (tipo == null || val == null) throw new IllegalArgumentException("Seletor invalido: " + nomeLogico);

        if ("CSS".equalsIgnoreCase(tipo.trim()) || "CSSSELECTOR".equalsIgnoreCase(tipo.trim())) {
            val = normalizarSeletorHref(val);
        }

        return switch (tipo.trim().toUpperCase()) {
            case "ID"                    -> By.id(val);
            case "NAME"                  -> By.name(val);
            case "CSS","CSSSELECTOR"     -> By.cssSelector(val);
            case "XPATH"                 -> By.xpath(val);
            case "CLASSNAME"             -> By.className(val);
            case "TAGNAME"               -> By.tagName(val);
            case "LINKTEXT","LINK_TEXT"  -> By.linkText(val);
            case "PARTIALLINKTEXT"       -> By.partialLinkText(val);
            default -> throw new IllegalArgumentException("Tipo invalido: " + tipo);
        };
    }

    private String normalizarSeletorHref(String selector) {
        if (selector == null) return selector;
        if (!selector.contains("href=\"http") && !selector.contains("href='http")) return selector;
        try {
            int hStart = selector.contains("href=\"http")
                    ? selector.indexOf("href=\"") + 6
                    : selector.indexOf("href='") + 6;
            char quote = selector.charAt(hStart - 1);
            int hEnd = selector.indexOf(quote, hStart);
            if (hStart < 6 || hEnd < 0) return selector;
            String url = selector.substring(hStart, hEnd);

            int idxHref = selector.indexOf("[href=");
            String prefixo = idxHref > 0 ? selector.substring(0, idxHref) : "a";

            int qIdx = url.indexOf('?');
            if (qIdx >= 0) {
                String query = url.substring(qIdx + 1);
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.contains("action") || param.contains("buy") || param.contains("add")) {
                        String normalizado = prefixo + "[href*=\"" + param + "\"]";
                        System.out.println("[Qorbit] Seletor href normalizado: " + selector + " -> " + normalizado);
                        return normalizado;
                    }
                }
                java.net.URI uri = new java.net.URI(url);
                String path = uri.getPath();
                if (path != null && !path.isBlank() && !path.equals("/")) {
                    String[] partes = path.split("/");
                    for (int i = partes.length - 1; i >= 0; i--) {
                        String p = partes[i];
                        if (p.length() > 5 && !p.equals("demosite") && !p.equals("produto")) {
                            String normalizado = prefixo + "[href*=\"" + p + "\"]";
                            System.out.println("[Qorbit] Seletor href normalizado via slug: " + selector + " -> " + normalizado);
                            return normalizado;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Qorbit] Falha ao normalizar seletor href: " + e.getMessage());
        }
        return selector;
    }

    private void aguardarPorTipoComponente(WebDriver driver, String acao, WebElement elemento) {
        if (acao == null || elemento == null) return;
        try {
            switch (acao.trim().toUpperCase()) {
                case "INPUT", "PREENCHER" -> {
                    new WebDriverWait(driver, Duration.ofSeconds(5)).until(d ->
                        elemento.isDisplayed() && elemento.isEnabled());
                }
                case "CLICK", "CLICAR" -> {
                    Thread.sleep(1000);
                    new WebDriverWait(driver, Duration.ofSeconds(5)).until(
                        ExpectedConditions.elementToBeClickable(elemento));
                    Thread.sleep(150);
                }
                case "SELECT", "SELECIONAR" -> {
                    new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> {
                        try {
                            org.openqa.selenium.support.ui.Select sel =
                                new org.openqa.selenium.support.ui.Select(elemento);
                            return sel.getOptions().size() > 1;
                        } catch (Exception e) { return true; }
                    });
                }
                default -> {
                    new WebDriverWait(driver, Duration.ofSeconds(5)).until(
                        ExpectedConditions.visibilityOf(elemento));
                }
            }
        } catch (Exception ignored) { }
    }

    private void aguardarEstabilidadeVisual(WebDriver driver) {
        try {
            String urlAntes = driver.getCurrentUrl();
            Thread.sleep(300);

            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                try { return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")); }
                catch (Exception e) { return true; }
            });

            String urlDepois = driver.getCurrentUrl();
            if (!urlAntes.equals(urlDepois)) {
                Thread.sleep(800);
                return;
            }

            try {
                new WebDriverWait(driver, Duration.ofMillis(1000)).until(d ->
                    (Boolean) ((JavascriptExecutor) d).executeScript(
                        "const selectors = ['.modal','.popup','.popup-box','.cart-popup'," +
                        "'[class*=\"modal\"]','[class*=\"popup\"]','[class*=\"overlay\"]'," +
                        "'[role=\"dialog\"]','[aria-modal=\"true\"]'];" +
                        "return selectors.some(sel => {" +
                        "  const el = document.querySelector(sel);" +
                        "  if (!el) return false;" +
                        "  const s = window.getComputedStyle(el);" +
                        "  const r = el.getBoundingClientRect();" +
                        "  return s.display !== 'none' && s.visibility !== 'hidden'" +
                        "    && s.opacity !== '0' && r.width > 0 && r.height > 0;" +
                        "});"
                    )
                );
                try {
                    new WebDriverWait(driver, Duration.ofSeconds(5)).until(d ->
                        !(Boolean) ((JavascriptExecutor) d).executeScript(
                            "const spinner = document.querySelector(" +
                            "'.loading,.loader,.spinner,[class*=\"spin\"],[class*=\"load\"]');" +
                            "if (!spinner) return false;" +
                            "const s = window.getComputedStyle(spinner);" +
                            "const r = spinner.getBoundingClientRect();" +
                            "return s.display !== 'none' && r.width > 0 && r.height > 0;"
                        )
                    );
                } catch (Exception ignored) { }
                Thread.sleep(800);
            } catch (Exception ignored) {
                Thread.sleep(400);
            }

        } catch (Exception ignored) {}
    }

    private void aguardarPaginaCarregar(WebDriver driver, int seg) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seg)).until(d -> {
                try { return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")); }
                catch (Exception e) { return true; }
            });
        } catch (Exception ignored) {}
    }

    private void highlight(WebDriver driver, WebElement el) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].style.border='3px solid red'", el);
            js.executeScript("arguments[0].scrollIntoView({behavior:'smooth',block:'center'})", el);
            Thread.sleep(200);
        } catch (Exception ignored) {}
    }

    private void notificar(Execucao exec) {
        if (mensageria == null) return;
        try {
            mensageria.convertAndSend("/topic/execucao/" + exec.getId(), Map.of(
                    "id",     exec.getId(),
                    "status", exec.getStatus(),
                    "passou", exec.getStepsPAssou() != null ? exec.getStepsPAssou() : 0,
                    "falhou", exec.getStepsFalhou() != null ? exec.getStepsFalhou() : 0,
                    "total",  exec.getTotalSteps()  != null ? exec.getTotalSteps()  : 0));
        } catch (Exception ignored) {}
    }

    private String extrairDominio(String url) {
        try { URI u = new URI(url); return u.getScheme() + "://" + u.getHost(); }
        catch (Exception e) { return url; }
    }

    private String extrairSeletorDoHtml(String outerHtml) {
        if (outerHtml == null || outerHtml.isBlank()) return "";
        try {
            if (outerHtml.contains("data-testid=")) {
                int s = outerHtml.indexOf("data-testid=") + 12;
                char q = outerHtml.charAt(s);
                int e = outerHtml.indexOf(q, s + 1);
                if (e > s) return "[data-testid='" + outerHtml.substring(s + 1, e) + "']";
            }
            if (outerHtml.contains(" id=")) {
                int s = outerHtml.indexOf(" id=") + 4;
                char q = outerHtml.charAt(s);
                int e = outerHtml.indexOf(q, s + 1);
                if (e > s) return "#" + outerHtml.substring(s + 1, e);
            }
            if (outerHtml.contains(" name=")) {
                String tag = outerHtml.substring(1,
                        outerHtml.indexOf(' ') > 0 ? outerHtml.indexOf(' ') : 10);
                int s = outerHtml.indexOf(" name=") + 6;
                char q = outerHtml.charAt(s);
                int e = outerHtml.indexOf(q, s + 1);
                if (e > s) return tag + "[name='" + outerHtml.substring(s + 1, e) + "']";
            }
        } catch (Exception ignored) { }
        return "";
    }

    private String limitar(String msg, int max) {
        if (msg == null || msg.isBlank()) return "Erro sem detalhe";
        return msg.length() > max ? msg.substring(0, max) + "..." : msg;
    }

    private void gerarRelatorio(Execucao exec, List<StepLog> logs) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String ini = null, fim = null;
            try { ini = LocalDateTime.parse(exec.getIniciadoEm()).format(fmt); } catch (Exception e) { ini = exec.getIniciadoEm(); }
            try { fim = LocalDateTime.parse(exec.getFinalizadoEm()).format(fmt); } catch (Exception e) { fim = exec.getFinalizadoEm(); }
            reportService.gerarRelatorio(exec.getId(), exec.getStatus(), exec.getUrlAlvo(),
                    exec.getStepsPAssou() == null ? 0 : exec.getStepsPAssou(),
                    exec.getStepsFalhou() == null ? 0 : exec.getStepsFalhou(),
                    ini, fim, logs);
        } catch (Exception e) {
            System.err.println("[EXEC " + exec.getId() + "] Erro ao gerar relatorio: " + e.getMessage());
        }
    }
}