package com.qorbit.engine.service;

import com.qorbit.engine.gerador.NormalizadorNomes;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.ElementoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class GeradorCodigoService {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private NormalizadorNomes normalizador;

    public byte[] gerarZip(List<CasoDeTeste> casos) throws IOException {
        return gerarZip(casos, false);
    }

    public byte[] gerarZip(List<CasoDeTeste> casos, boolean includeCiCd) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<Elemento> todosElementos = elementoRepo.findAll();

        Map<String, Elemento> elementosPorNome = todosElementos.stream()
                .filter(e -> e.getNomeLogico() != null && !e.getNomeLogico().isBlank())
                .collect(Collectors.toMap(
                        e -> e.getNomeLogico().trim().toLowerCase(Locale.ROOT),
                        e -> e,
                        this::priorizarElemento,
                        LinkedHashMap::new));

        Map<String, PageObjectSpec> pages = construirPages(todosElementos);

        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            adicionarArquivo(zip, "qorbit-tests-export/pom.xml", gerarPomXml());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/runner/TestRunner.java", gerarTestRunner());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/config/Hooks.java", gerarHooks());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/config/DriverFactory.java",includeCiCd ? gerarDriverFactoryCiCd() : gerarDriverFactory());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/report/StepEvidence.java", gerarStepEvidence());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/report/ExecutionContext.java", gerarExecutionContext());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/report/FeatureStepCatalog.java", gerarFeatureStepCatalog());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/report/HtmlReportGenerator.java", gerarHtmlReportGenerator());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/steps/CommonSteps.java", gerarCommonSteps(pages));

            for (PageObjectSpec page : pages.values()) {
                adicionarArquivo(zip,
                        "qorbit-tests-export/src/test/java/pages/" + page.className + ".java",
                        gerarPageObject(page));
            }

            for (CasoDeTeste caso : casos) {
                String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
                adicionarArquivo(zip,
                        "qorbit-tests-export/src/test/resources/features/" + nomeClasse.toLowerCase(Locale.ROOT) + ".feature",
                        gerarFeature(caso));
                adicionarArquivo(zip,
                        "qorbit-tests-export/src/test/java/steps/" + nomeClasse + "Steps.java",
                        gerarStepDefinitions(caso, pages, elementosPorNome));
            }

            adicionarArquivo(zip, "qorbit-tests-export/README.md",includeCiCd ? gerarReadmeCiCd() : gerarReadme());
            adicionarArquivo(zip, "qorbit-tests-export/executar-testes.bat", gerarExecutarTestesBat());
            adicionarArquivo(zip, "qorbit-tests-export/executar-testes.sh", gerarExecutarTestesSh());
            if (includeCiCd) {
                adicionarArquivo(zip, "qorbit-tests-export/.github/workflows/testes.yml", gerarGithubActionsWorkflow());
            }
            adicionarArquivo(zip, "qorbit-tests-export/src/test/java/healing/HealingService.java", gerarHealingService());
            adicionarArquivo(zip, "qorbit-tests-export/src/test/resources/healing.properties", gerarHealingProperties());
        }

        return baos.toByteArray();
    }

    private String gerarDriverFactoryCiCd() {
        return "package config;\n\n"
                + "import io.github.bonigarcia.wdm.WebDriverManager;\n"
                + "import org.openqa.selenium.WebDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeOptions;\n\n"
                + "public class DriverFactory {\n\n"
                + "    private static WebDriver driver;\n\n"
                + "    public static WebDriver getDriver() {\n"
                + "        if (driver == null) {\n"
                + "            WebDriverManager.chromedriver().setup();\n"
                + "            ChromeOptions options = new ChromeOptions();\n"
                + "            boolean headless = !\"false\".equalsIgnoreCase(System.getProperty(\"headless\", \"true\"));\n"
                + "            if (headless) {\n"
                + "                options.addArguments(\"--headless=new\");\n"
                + "                options.addArguments(\"--no-sandbox\");\n"
                + "                options.addArguments(\"--disable-dev-shm-usage\");\n"
                + "                options.addArguments(\"--window-size=1920,1080\");\n"
                + "            }\n"
                + "            driver = new ChromeDriver(options);\n"
                + "            if (!headless) driver.manage().window().maximize();\n"
                + "        }\n"
                + "        return driver;\n"
                + "    }\n\n"
                + "    public static void encerrar() {\n"
                + "        if (driver != null) {\n"
                + "            driver.quit();\n"
                + "            driver = null;\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarGithubActionsWorkflow() {
        return "name: Testes Automatizados Qorbit\n\n"
                + "on:\n"
                + "  push:\n"
                + "    branches: [ \"**\" ]\n"
                + "  pull_request:\n"
                + "    branches: [ \"**\" ]\n\n"
                + "jobs:\n"
                + "  testes:\n"
                + "    runs-on: ubuntu-latest\n\n"
                + "    steps:\n"
                + "      - name: Checkout do repositório\n"
                + "        uses: actions/checkout@v4\n\n"
                + "      - name: Configurar Java 17\n"
                + "        uses: actions/setup-java@v4\n"
                + "        with:\n"
                + "          java-version: '17'\n"
                + "          distribution: 'temurin'\n"
                + "          cache: maven\n\n"
                + "      - name: Instalar Google Chrome\n"
                + "        run: |\n"
                + "          sudo apt-get update\n"
                + "          sudo apt-get install -y google-chrome-stable\n\n"
                + "      - name: Executar testes\n"
                + "        run: mvn clean test\n\n"
                + "      - name: Publicar artefatos de teste\n"
                + "        if: always()\n"
                + "        uses: actions/upload-artifact@v4\n"
                + "        with:\n"
                + "          name: relatorio-testes\n"
                + "          path: |\n"
                + "            target/relatorio.html\n"
                + "            target/evidencias/\n"
                + "            target/relatorio-cucumber.html\n"
                + "          retention-days: 30\n";
    }

    private String gerarReadmeCiCd() {
        return "# Qorbit - Projeto Exportado\n\n"
                + "## Como executar localmente\n\n"
                + "Execute dentro da pasta `qorbit-tests-export`:\n\n"
                + "```bash\n"
                + "mvn test -Dheadless=false\n"
                + "```\n\n"
                + "No Windows:\n\n"
                + "```bash\n"
                + "executar-testes.bat\n"
                + "```\n\n"
                + "No Linux/Mac:\n\n"
                + "```bash\n"
                + "chmod +x executar-testes.sh\n"
                + "./executar-testes.sh\n"
                + "```\n\n"
                + "## Como executar em modo headless\n\n"
                + "```bash\n"
                + "mvn clean test\n"
                + "```\n\n"
                + "> O modo headless é o **padrão**. Para ver o navegador, passe `-Dheadless=false`.\n\n"
                + "## Execução via GitHub Actions\n\n"
                + "Este projeto já contém o arquivo `.github/workflows/testes.yml`.\n\n"
                + "Para usar o pipeline:\n\n"
                + "1. Suba este projeto para um repositório GitHub\n"
                + "2. O pipeline executa automaticamente a cada `push` ou `pull_request`\n"
                + "3. Os artefatos (relatório HTML e evidências) ficam disponíveis na aba **Actions** do repositório\n\n"
                + "## Importante\n\n"
                + "Nao use `mvn spring-boot:run` neste projeto exportado — ele nao possui aplicacao Spring Boot.\n\n"
                + "## Melhorias incluidas nesta versao\n"
                + "- Pipeline GitHub Actions pronto para uso\n"
                + "- DriverFactory com headless configuravel via `-Dheadless=false`\n"
                + "- `--no-sandbox` e `--disable-dev-shm-usage` incluidos para compatibilidade com CI\n"
                + "- Resolucao padrao de 1920x1080 no headless\n"
                + "- Publicacao automatica de artefatos mesmo em caso de falha\n"
                + "- Page Objects (POM) gerados automaticamente\n"
                + "- Arquivos .feature (Gherkin) em pt-BR\n"
                + "- Step Definitions, TestRunner e Hooks incluidos\n"
                + "- Self-Healing local ativado\n"
                + "- Capturas de evidencia por step em target/evidencias\n"
                + "- Relatorio HTML em target/relatorio.html\n";
    }

    private Map<String, PageObjectSpec> construirPages(List<Elemento> elementos) {
        Map<String, List<Elemento>> porPagina = elementos.stream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.getPagina()).orElse("PaginaInicial"), LinkedHashMap::new, Collectors.toList()));

        Map<String, PageObjectSpec> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, List<Elemento>> entry : porPagina.entrySet()) {
            String rawPageName = entry.getKey();
            String className = normalizador.normalizarClasse(rawPageName, "PaginaInicial") + "Page";
            PageObjectSpec spec = new PageObjectSpec(className, rawPageName);
            Set<String> nomesUsados = new LinkedHashSet<>();
            for (Elemento el : entry.getValue()) {
                if (el.getSeletorTecnico() == null || el.getSeletorTecnico().isBlank()) continue;
                ElementContext ctx = parseContext(el);
                String baseMethod = normalizador.nomeMetodoParaElemento(el, prefixoParaElemento(el, ctx.componentType));
                String methodName = normalizador.tornarUnico(baseMethod, nomesUsados);
                spec.elementos.add(new ElementSpec(el, methodName, locatorFrom(el, ctx), ctx));
                spec.logicalToMethod.put(el.getNomeLogico().trim().toLowerCase(Locale.ROOT), methodName);
            }
            resultado.put(className, spec);
        }
        if (resultado.isEmpty()) {
            resultado.put("PaginaInicialPage", new PageObjectSpec("PaginaInicialPage", "PaginaInicial"));
        }
        return resultado;
    }

    private String gerarPageObject(PageObjectSpec page) {
        StringBuilder sb = new StringBuilder();
        sb.append("package pages;\n\n");
        sb.append("import org.openqa.selenium.By;\n");
        sb.append("import org.openqa.selenium.JavascriptExecutor;\n");
        sb.append("import org.openqa.selenium.NoSuchElementException;\n");
        sb.append("import org.openqa.selenium.StaleElementReferenceException;\n");
        sb.append("import org.openqa.selenium.ElementClickInterceptedException;\n");
        sb.append("import org.openqa.selenium.TimeoutException;\n");
        sb.append("import org.openqa.selenium.WebDriver;\n");
        sb.append("import org.openqa.selenium.WebElement;\n");
        sb.append("import org.openqa.selenium.support.ui.ExpectedConditions;\n");
        sb.append("import org.openqa.selenium.support.ui.Select;\n");
        sb.append("import org.openqa.selenium.support.ui.WebDriverWait;\n\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Locale;\n\n");
        sb.append("public class ").append(page.className).append(" {\n\n");
        sb.append("    private final WebDriver driver;\n");
        sb.append("    private final WebDriverWait wait;\n\n");
        sb.append("    public ").append(page.className).append("(WebDriver driver) {\n");
        sb.append("        this.driver = driver;\n");
        sb.append("        this.wait = new WebDriverWait(driver, Duration.ofSeconds(12));\n");
        sb.append("    }\n\n");
        sb.append("    public void abrir(String url) {\n");
        sb.append("        driver.get(url);\n");
        sb.append("        aguardarEstabilidade();\n");
        sb.append("    }\n\n");
        sb.append("    public void aguardarEstabilidade() {\n");
        sb.append("        try {\n");
        sb.append("            wait.until(d -> {\n");
        sb.append("                Object state = ((JavascriptExecutor) d).executeScript(\"return document.readyState\");\n");
        sb.append("                Object busy = ((JavascriptExecutor) d).executeScript(\"return !!document.querySelector('[aria-busy=\\\"true\\\"], .loading, .loader, .spinner, .ant-spin-spinning, .MuiCircularProgress-root');\");\n");
        sb.append("                return (\"complete\".equals(String.valueOf(state)) || \"interactive\".equals(String.valueOf(state))) && !Boolean.TRUE.equals(busy);\n");
        sb.append("            });\n");
        sb.append("        } catch (Exception ignored) { }\n");
        sb.append("    }\n\n");
        sb.append("    private void resetContext() {\n");
        sb.append("        driver.switchTo().defaultContent();\n");
        sb.append("    }\n\n");
        sb.append("    private void switchToFramePath(String framePath) {\n");
        sb.append("        resetContext();\n");
        sb.append("        if (framePath == null || framePath.isBlank() || \"root\".equalsIgnoreCase(framePath)) return;\n");
        sb.append("        String[] parts = framePath.split(\">\");\n");
        sb.append("        for (String part : parts) {\n");
        sb.append("            String token = part == null ? \"\" : part.trim();\n");
        sb.append("            if (token.isBlank() || \"root\".equalsIgnoreCase(token)) continue;\n");
        sb.append("            List<WebElement> frames = driver.findElements(By.cssSelector(\"iframe,frame\"));\n");
        sb.append("            WebElement alvo = null;\n");
        sb.append("            if (token.startsWith(\"index-\")) {\n");
        sb.append("                int idx = Integer.parseInt(token.substring(6));\n");
        sb.append("                if (idx >= 0 && idx < frames.size()) alvo = frames.get(idx);\n");
        sb.append("            } else {\n");
        sb.append("                for (WebElement frame : frames) {\n");
        sb.append("                    String name = frame.getAttribute(\"name\");\n");
        sb.append("                    String id = frame.getAttribute(\"id\");\n");
        sb.append("                    if (token.equals(name) || token.equals(id)) { alvo = frame; break; }\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("            if (alvo == null) throw new NoSuchElementException(\"Frame nao encontrado: \" + token);\n");
        sb.append("            driver.switchTo().frame(alvo);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    private WebElement localizarShadow(String shadowPath) {\n");
        sb.append("        Object found = ((JavascriptExecutor) driver).executeScript(\"\"\"\n");
        sb.append("                const path = arguments[0];\n");
        sb.append("                if (!path) return null;\n");
        sb.append("                const parts = String(path).split('>').map(p => p.trim()).filter(Boolean);\n");
        sb.append("                function queryIn(root, token) {\n");
        sb.append("                  if (!root || !token) return null;\n");
        sb.append("                  if (token.startsWith('shadow-host(') && token.endsWith(')')) {\n");
        sb.append("                    const hostSel = token.substring(12, token.length - 1).trim();\n");
        sb.append("                    return root.querySelector(hostSel);\n");
        sb.append("                  }\n");
        sb.append("                  return root.querySelector(token);\n");
        sb.append("                }\n");
        sb.append("                let currentRoot = document;\n");
        sb.append("                let currentEl = null;\n");
        sb.append("                for (const token of parts) {\n");
        sb.append("                  if (token.startsWith('shadow-host(')) {\n");
        sb.append("                    currentEl = queryIn(currentRoot, token);\n");
        sb.append("                    if (!currentEl) return null;\n");
        sb.append("                    currentRoot = currentEl.shadowRoot || currentEl;\n");
        sb.append("                    continue;\n");
        sb.append("                  }\n");
        sb.append("                  currentEl = queryIn(currentRoot, token);\n");
        sb.append("                  if (!currentEl) return null;\n");
        sb.append("                  if (currentEl.shadowRoot) currentRoot = currentEl.shadowRoot;\n");
        sb.append("                }\n");
        sb.append("                return currentEl;\n");
        sb.append("                \"\"\", shadowPath);\n");
        sb.append("        if (found instanceof WebElement element) return element;\n");
        sb.append("        throw new NoSuchElementException(\"Elemento shadow nao encontrado: \" + shadowPath);\n");
        sb.append("    }\n\n");
        sb.append("    private WebElement localizar(String tipo, String seletor, String framePath) {\n");
        sb.append("        switchToFramePath(framePath);\n");
        sb.append("        aguardarEstabilidade();\n");
        sb.append("        if (\"SHADOW_CSS\".equalsIgnoreCase(tipo)) return localizarShadow(seletor);\n");
        sb.append("        By by = switch ((tipo == null ? \"CSS\" : tipo).toUpperCase(Locale.ROOT)) {\n");
        sb.append("            case \"XPATH\" -> By.xpath(seletor);\n");
        sb.append("            case \"ID\" -> By.id(seletor);\n");
        sb.append("            case \"NAME\" -> By.name(seletor);\n");
        sb.append("            case \"LINK_TEXT\" -> By.linkText(seletor);\n");
        sb.append("            default -> By.cssSelector(seletor);\n");
        sb.append("        };\n");
        sb.append("        return wait.until(ExpectedConditions.presenceOfElementLocated(by));\n");
        sb.append("    }\n\n");
        sb.append("    private void scrollParaCentro(WebElement elemento) {\n");
        sb.append("        try { ((JavascriptExecutor) driver).executeScript(\"arguments[0].scrollIntoView({block:'center', inline:'center'});\", elemento); } catch (Exception ignored) { }\n");
        sb.append("    }\n\n");
        sb.append("    private boolean possuiInterceptacao(WebElement elemento) {\n");
        sb.append("        try {\n");
        sb.append("            Object result = ((JavascriptExecutor) driver).executeScript(\"\"\"\n");
        sb.append("                    const el = arguments[0];\n");
        sb.append("                    if (!el) return false;\n");
        sb.append("                    const r = el.getBoundingClientRect();\n");
        sb.append("                    const x = Math.min(Math.max(r.left + r.width / 2, 0), window.innerWidth - 1);\n");
        sb.append("                    const y = Math.min(Math.max(r.top + Math.min(r.height / 2, 10), 0), window.innerHeight - 1);\n");
        sb.append("                    const top = document.elementFromPoint(x, y);\n");
        sb.append("                    if (!top) return false;\n");
        sb.append("                    return !(top === el || el.contains(top) || top.contains(el));\n");
        sb.append("                    \"\"\", elemento);\n");
        sb.append("            return Boolean.TRUE.equals(result);\n");
        sb.append("        } catch (Exception ignored) { return false; }\n");
        sb.append("    }\n\n");
        sb.append("    private void aguardarAnimacaoCurta() {\n");
        sb.append("        try { Thread.sleep(250); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }\n");
        sb.append("    }\n\n");
        sb.append("    private void fecharOverlaysConhecidos() {\n");
        sb.append("        try {\n");
        sb.append("            ((JavascriptExecutor) driver).executeScript(\"\"\"\n");
        sb.append("                    const selectors = [\n");
        sb.append("                      '#onetrust-accept-btn-handler',\n");
        sb.append("                      'button[aria-label*=\"Fechar\"]',\n");
        sb.append("                      'button[aria-label*=\"fechar\"]',\n");
        sb.append("                      'button[title*=\"Fechar\"]',\n");
        sb.append("                      '[data-testid*=\"close\"]',\n");
        sb.append("                      '.cookie-banner button',\n");
        sb.append("                      '.cookies button',\n");
        sb.append("                      '.modal button.close',\n");
        sb.append("                      '.drawer button.close',\n");
        sb.append("                      '.lgpd button',\n");
        sb.append("                      'button'\n");
        sb.append("                    ];\n");
        sb.append("                    const texts = ['aceitar','ok','fechar','entendi','continuar','prosseguir'];\n");
        sb.append("                    function visible(el){\n");
        sb.append("                      if(!el) return false;\n");
        sb.append("                      const s = window.getComputedStyle(el);\n");
        sb.append("                      const r = el.getBoundingClientRect();\n");
        sb.append("                      return s && s.visibility !== 'hidden' && s.display !== 'none' && r.width > 0 && r.height > 0;\n");
        sb.append("                    }\n");
        sb.append("                    for (const sel of selectors) {\n");
        sb.append("                      for (const el of Array.from(document.querySelectorAll(sel))) {\n");
        sb.append("                        const txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim().toLowerCase();\n");
        sb.append("                        const genericButton = sel === 'button';\n");
        sb.append("                        if (visible(el) && (!genericButton || texts.some(t => txt.includes(t)))) { try { el.click(); return; } catch (e) {} }\n");
        sb.append("                      }\n");
        sb.append("                    }\n");
        sb.append("                    document.dispatchEvent(new KeyboardEvent('keydown', {key:'Escape', code:'Escape', keyCode:27, which:27, bubbles:true}));\n");
        sb.append("                    \"\"\");\n");
        sb.append("        } catch (Exception ignored) { }\n");
        sb.append("        aguardarAnimacaoCurta();\n");
        sb.append("    }\n\n");
        sb.append("    private void clicarViaJs(WebElement elemento) {\n");
        sb.append("        ((JavascriptExecutor) driver).executeScript(\"arguments[0].click();\", elemento);\n");
        sb.append("    }\n\n");
        sb.append("    public void clicar(WebElement elemento) {\n");
        sb.append("        Exception ultimaFalha = null;\n");
        sb.append("        for (int tentativa = 0; tentativa < 3; tentativa++) {\n");
        sb.append("            try {\n");
        sb.append("                aguardarEstabilidade();\n");
        sb.append("                scrollParaCentro(elemento);\n");
        sb.append("                aguardarAnimacaoCurta();\n");
        sb.append("                if (possuiInterceptacao(elemento)) { fecharOverlaysConhecidos(); scrollParaCentro(elemento); }\n");
        sb.append("                wait.until(ExpectedConditions.elementToBeClickable(elemento)).click();\n");
        sb.append("                return;\n");
        sb.append("            } catch (ElementClickInterceptedException | TimeoutException e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                fecharOverlaysConhecidos();\n");
        sb.append("                try { scrollParaCentro(elemento); } catch (Exception ignored) { }\n");
        sb.append("                if (tentativa == 2) {\n");
        sb.append("                    try { clicarViaJs(elemento); return; } catch (Exception jsEx) { ultimaFalha = jsEx; }\n");
        sb.append("                }\n");
        sb.append("            } catch (StaleElementReferenceException e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                aguardarAnimacaoCurta();\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                ultimaFalha = e;\n");
        sb.append("                try { clicarViaJs(elemento); return; } catch (Exception jsEx) { ultimaFalha = jsEx; }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        throw new RuntimeException(\"Falha ao clicar no elemento de forma resiliente\", ultimaFalha);\n");
        sb.append("    }\n\n");
        sb.append("    public void preencher(String valor, WebElement elemento) {\n");
        sb.append("        try { elemento.clear(); } catch (Exception ignored) { }\n");
        sb.append("        elemento.sendKeys(valor);\n");
        sb.append("    }\n\n");
        sb.append("    /**\n");
        sb.append("     * Preenche input[type=\"date\"] via JavaScript.\n");
        sb.append("     * Suporta formatos: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy\n");
        sb.append("     * Se o campo abrir calendário, tenta digitação directa.\n");
        sb.append("     */\n");
        sb.append("    public void preencherData(String valor, WebElement elemento) {\n");
        sb.append("        try {\n");
        sb.append("            // Normaliza para yyyy-MM-dd (formato aceite pelo input[type=date])\n");
        sb.append("            String dataFormatada = valor;\n");
        sb.append("            if (valor != null && valor.matches(\"\\\\d{2}[/\\\\-]\\\\d{2}[/\\\\-]\\\\d{4}\")) {\n");
        sb.append("                String[] partes = valor.split(\"[/\\\\-]\");\n");
        sb.append("                dataFormatada = partes[2] + \"-\" + partes[1] + \"-\" + partes[0];\n");
        sb.append("            }\n");
        sb.append("            // Tenta via JavaScript (mais fiável para input[type=date])\n");
        sb.append("            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\n");
        sb.append("                \"arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', {bubbles:true})); arguments[0].dispatchEvent(new Event('change', {bubbles:true}));\",\n");
        sb.append("                elemento, dataFormatada);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            // Fallback: tenta sendKeys\n");
        sb.append("            try { elemento.clear(); } catch (Exception ignored) { }\n");
        sb.append("            elemento.sendKeys(valor != null ? valor : \"\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    public void interagirComSelecao(String valor, WebElement elemento, String componentType) {\n");
        sb.append("        String tipo = componentType == null ? \"\" : componentType.toUpperCase(Locale.ROOT);\n");
        sb.append("        switch (tipo) {\n");
        sb.append("            case \"SELECT\" -> new Select(elemento).selectByVisibleText(valor);\n");
        sb.append("            case \"CUSTOM_SELECT\", \"AUTOCOMPLETE\" -> {\n");
        sb.append("                clicar(elemento);\n");
        sb.append("                try { elemento.sendKeys(valor); } catch (Exception ignored) { }\n");
        sb.append("                WebElement opcao = wait.until(d -> {\n");
        sb.append("                    List<WebElement> matches = d.findElements(By.xpath(\"//*[self::li or self::div or self::span or self::button][normalize-space()=\\\"\" + valor + \"\\\" or contains(normalize-space(),\\\"\" + valor + \"\\\")]\"));\n");
        sb.append("                    return matches.isEmpty() ? null : matches.get(0);\n");
        sb.append("                });\n");
        sb.append("                clicar(opcao);\n");
        sb.append("            }\n");
        sb.append("            case \"DATEPICKER\" -> {\n");
        sb.append("                preencherData(valor, elemento);\n");
        sb.append("            }\n");
        sb.append("            default -> preencher(valor, elemento);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        for (ElementSpec element : page.elementos) {
            sb.append("    // ").append(normalizador.literalJava(element.source().getNomeLogico())).append("\n");
            sb.append("    public WebElement ").append(element.methodName()).append("() {\n");
            sb.append("        return localizar(\"").append(normalizador.literalJava(element.locator().tipo())).append("\", \"")
                    .append(normalizador.literalJava(element.locator().valor())).append("\", \"")
                    .append(normalizador.literalJava(element.context().framePath())).append("\");\n");
            sb.append("    }\n\n");
            sb.append("    public String ").append(element.methodName()).append("TipoComponente() {\n");
            sb.append("        return \"").append(normalizador.literalJava(element.context().componentType())).append("\";\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String gerarFeature(CasoDeTeste caso) {
        StringBuilder sb = new StringBuilder();
        sb.append("# language: pt\n");
        sb.append("# Gerado pelo Qorbit\n\n");
        sb.append("Funcionalidade: ").append(safe(caso.getNome())).append("\n\n");
        sb.append("  Cenario: ").append(safe(caso.getNome())).append("\n");

        List<StepTeste> steps = orderedSteps(caso);
        for (int i = 0; i < steps.size(); i++) {
            StepTeste step = steps.get(i);
            String keyword = i == 0 ? "Dado" : ("VALIDAR".equalsIgnoreCase(step.getAcao()) ? "Entao" : "E");
            sb.append("    ").append(keyword).append(" ").append(descricaoStep(step)).append("\n");
        }
        return sb.toString();
    }

    private String gerarStepDefinitions(CasoDeTeste caso,
                                        Map<String, PageObjectSpec> pages,
                                        Map<String, Elemento> elementosPorNome) {
        String nomeClasse = normalizador.normalizarClasse(caso.getNome(), "CasoGerado");
        StringBuilder sb = new StringBuilder();
        sb.append("package steps;\n\n");
        sb.append("import config.DriverFactory;\n");
        sb.append("import io.cucumber.java.pt.*;\n");
        sb.append("import org.junit.Assert;\n");
        sb.append("import org.openqa.selenium.WebDriver;\n");

        LinkedHashMap<String, String> pageVars = new LinkedHashMap<>();
        List<ResolvedStep> resolvedSteps = resolverSteps(caso, pages, elementosPorNome);
        for (ResolvedStep step : resolvedSteps) {
            if (step.pageClassName() != null && !pageVars.containsKey(step.pageClassName())) {
                pageVars.put(step.pageClassName(), lowerFirst(step.pageClassName()));
                sb.append("import pages.").append(step.pageClassName()).append(";\n");
            }
        }
        if (pageVars.isEmpty()) {
            String fallbackPage = pages.keySet().iterator().next();
            pageVars.put(fallbackPage, lowerFirst(fallbackPage));
            sb.append("import pages.").append(fallbackPage).append(";\n");
        }

        sb.append("\npublic class ").append(nomeClasse).append("Steps {\n\n");
        sb.append("    private final WebDriver driver = DriverFactory.getDriver();\n");
        for (Map.Entry<String, String> pageVar : pageVars.entrySet()) {
            sb.append("    private final ").append(pageVar.getKey()).append(" ").append(pageVar.getValue())
                    .append(" = new ").append(pageVar.getKey()).append("(driver);\n");
        }
        sb.append("\n");

        List<StepTeste> ordered = orderedSteps(caso);
        Set<String> expressoesGeradas = new LinkedHashSet<>();
        int metodoSeq = 1;
        for (int i = 0; i < ordered.size(); i++) {
            StepTeste step = ordered.get(i);
            String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
            if ("NAVEGAR".equals(acao)) {
                continue;
            }
            ResolvedStep resolved = i < resolvedSteps.size() ? resolvedSteps.get(i) : null;
            String annotation = i == 0 ? "@Dado" : ("VALIDAR".equalsIgnoreCase(step.getAcao()) ? "@Entao" : "@E");
            String expressaoRaw = expressaoStep(step);
            if (!expressoesGeradas.add(expressaoRaw)) {
                continue;
            }
            String expressao = normalizador.literalJava(expressaoRaw);
            String assinaturaMetodo = expressaoTemParametro(step) ? "(String valorStep)" : "()";
            sb.append("    ").append(annotation).append("(\"").append(expressao).append("\")\n");
            sb.append("    public void step").append(metodoSeq++).append(assinaturaMetodo).append(" {\n");
            gerarCorpoStep(sb, step, resolved, pageVars);
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private void gerarCorpoStep(StringBuilder sb, StepTeste step, ResolvedStep resolved, Map<String, String> pageVars) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String pageVar = resolved != null && resolved.pageClassName() != null
                ? pageVars.getOrDefault(resolved.pageClassName(), pageVars.values().iterator().next())
                : pageVars.values().iterator().next();
        String valor = normalizador.literalJava(Optional.ofNullable(step.getValorEntrada()).orElse(""));
        String valorReferencia = expressaoTemParametro(step) ? "valorStep" : "\"" + valor + "\"";

        switch (acao) {
            case "NAVEGAR" -> sb.append("        ").append(pageVar).append(".abrir(").append(valorReferencia).append(");\n");
            case "PREENCHER" -> {
                if (resolved != null && resolved.methodName() != null) {
                    if (isDynamicComponent(resolved.componentType())) {
                        sb.append("        ").append(pageVar).append(".interagirComSelecao(").append(valorReferencia).append(", ")
                                .append(pageVar).append(".").append(resolved.methodName()).append("(), ")
                                .append(pageVar).append(".").append(resolved.methodName()).append("TipoComponente());\n");
                    } else {
                        sb.append("        ").append(pageVar).append(".preencher(").append(valorReferencia).append(", ")
                                .append(pageVar).append(".").append(resolved.methodName()).append("());\n");
                    }
                } else {
                    failElemento(sb, "preenchimento", step);
                }
            }
            case "CLICAR" -> {
                if (resolved != null && resolved.methodName() != null) {
                    sb.append("        ").append(pageVar).append(".clicar(")
                            .append(pageVar).append(".").append(resolved.methodName()).append("());\n");
                } else {
                    failElemento(sb, "clique", step);
                }
            }
            case "VALIDAR" -> {
                if (resolved != null && resolved.methodName() != null) {
                    sb.append("        Assert.assertTrue(").append(pageVar).append(".").append(resolved.methodName())
                            .append("().isDisplayed());\n");
                } else {
                    failElemento(sb, "validacao", step);
                }
            }
            case "SELECIONAR" -> {
                if (resolved != null && resolved.methodName() != null) {
                    sb.append("        ").append(pageVar).append(".interagirComSelecao(").append(valorReferencia).append(", ")
                            .append(pageVar).append(".").append(resolved.methodName()).append("(), ")
                            .append(pageVar).append(".").append(resolved.methodName()).append("TipoComponente());\n");
                } else {
                    failElemento(sb, "selecao", step);
                }
            }
            default -> sb.append("        // Acao nao suportada: ").append(normalizador.literalJava(acao)).append("\n");
        }
    }

    private void failElemento(StringBuilder sb, String tipoFalha, StepTeste step) {
        sb.append("        Assert.fail(\"Elemento nao encontrado para ").append(tipoFalha).append(": ")
                .append(normalizador.literalJava(Optional.ofNullable(step.getNomeLogicoElemento()).orElse("")))
                .append("\");\n");
    }

    private List<ResolvedStep> resolverSteps(CasoDeTeste caso,
                                             Map<String, PageObjectSpec> pages,
                                             Map<String, Elemento> elementosPorNome) {
        List<ResolvedStep> resolvidos = new ArrayList<>();
        for (StepTeste step : orderedSteps(caso)) {
            String nomeLogico = Optional.ofNullable(step.getNomeLogicoElemento()).orElse("").trim().toLowerCase(Locale.ROOT);
            Elemento elemento = elementosPorNome.get(nomeLogico);
            if (elemento == null) {
                resolvidos.add(new ResolvedStep(null, null, null));
                continue;
            }
            String pageClassName = normalizador.normalizarClasse(elemento.getPagina(), "PaginaInicial") + "Page";
            PageObjectSpec page = pages.get(pageClassName);
            String methodName = page != null ? page.logicalToMethod.get(nomeLogico) : null;
            String componentType = parseContext(elemento).componentType();
            resolvidos.add(new ResolvedStep(pageClassName, methodName, componentType));
        }
        return resolvidos;
    }

    private LocatorSpec locatorFrom(Elemento elemento, ElementContext ctx) {
        String tipo = Optional.ofNullable(elemento.getTipoSeletor()).orElse("CSS").toUpperCase(Locale.ROOT);
        String seletor = Optional.ofNullable(elemento.getSeletorTecnico()).orElse("");
        if ("SHADOW_CSS".equals(tipo) && (seletor.isBlank() || !seletor.contains("shadow-host("))) {
            tipo = "CSS";
        }
        return new LocatorSpec(tipo, seletor);
    }

    private ElementContext parseContext(Elemento elemento) {
        String descricao = Optional.ofNullable(elemento.getDescricao()).orElse("");
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String part : descricao.split("\\\\|")) {
            String token = part.trim();
            int idx = token.indexOf('=');
            if (idx > 0) {
                parsed.put(token.substring(0, idx).trim(), token.substring(idx + 1).trim());
            }
        }
        return new ElementContext(
                parsed.getOrDefault("frame", "root"),
                parsed.getOrDefault("componentType", "INTERACTIVE"),
                parsed.getOrDefault("shadowDom", "false")
        );
    }

    private boolean isDynamicComponent(String componentType) {
        if (componentType == null) return false;
        return Set.of("CUSTOM_SELECT", "AUTOCOMPLETE", "DATEPICKER", "SELECT").contains(componentType.toUpperCase(Locale.ROOT));
    }

    private String expressaoStep(StepTeste step) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String nome = Optional.ofNullable(step.getNomeLogicoElemento()).orElse("elemento");
        return switch (acao) {
            case "PREENCHER" -> "preencho " + nome + " com {string}";
            case "NAVEGAR" -> "acesso a URL {string}";
            case "SELECIONAR" -> "seleciono {string} em " + nome;
            default -> descricaoStep(step);
        };
    }

    private boolean expressaoTemParametro(StepTeste step) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        return Set.of("NAVEGAR", "PREENCHER", "SELECIONAR").contains(acao);
    }

    private String descricaoStep(StepTeste step) {
        String acao = Optional.ofNullable(step.getAcao()).orElse("").toUpperCase(Locale.ROOT);
        String nome = Optional.ofNullable(step.getNomeLogicoElemento()).orElse("elemento");
        String valor = Optional.ofNullable(step.getValorEntrada()).orElse("");
        return switch (acao) {
            case "CLICAR" -> "clico em " + nome;
            case "PREENCHER" -> "preencho " + nome + " com \"" + valor + "\"";
            case "NAVEGAR" -> "acesso a URL \"" + valor + "\"";
            case "VALIDAR" -> nome + " esta visivel";
            case "SELECIONAR" -> "seleciono \"" + valor + "\" em " + nome;
            default -> Optional.ofNullable(step.getDescricaoGherkin()).orElse(acao + " " + nome);
        };
    }

    private List<StepTeste> orderedSteps(CasoDeTeste caso) {
        if (caso.getSteps() == null) return List.of();
        return caso.getSteps().stream()
                .sorted(Comparator.comparingInt(s -> Optional.ofNullable(s.getNumeroStep()).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private Elemento priorizarElemento(Elemento a, Elemento b) {
        return scoreElemento(b) > scoreElemento(a) ? b : a;
    }

    private int scoreElemento(Elemento e) {
        String tipo = Optional.ofNullable(e.getTipoSeletor()).orElse("").toUpperCase(Locale.ROOT);
        int base = switch (tipo) {
            case "ID" -> 100;
            case "CSS" -> 90;
            case "NAME" -> 80;
            case "LINK_TEXT" -> 70;
            case "SHADOW_CSS" -> 85;
            default -> 60;
        };
        String descricao = Optional.ofNullable(e.getDescricao()).orElse("");
        if (descricao.contains("dataTestId=")) base += 15;
        if (descricao.contains("frame=")) base += 5;
        return base;
    }

    private String prefixoParaElemento(Elemento elemento, String componentType) {
        String nome = Optional.ofNullable(elemento.getNomeLogico()).orElse("").toLowerCase(Locale.ROOT);
        String component = Optional.ofNullable(componentType).orElse("").toUpperCase(Locale.ROOT);
        if (Set.of("SELECT","CUSTOM_SELECT").contains(component) || nome.startsWith("select")) return "select";
        if (Set.of("BUTTON","MODAL_ACTION").contains(component) || nome.startsWith("botao") || nome.startsWith("link")) return "botao";
        if ("AUTOCOMPLETE".equals(component)) return "autoComplete";
        if ("DATEPICKER".equals(component)) return "data";
        return "campo";
    }

    private String lowerFirst(String texto) {
        return texto == null || texto.isBlank() ? "page" : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }

    private void adicionarArquivo(ZipOutputStream zip, String caminho, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(caminho));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String gerarCommonSteps(Map<String, PageObjectSpec> pages) {
        String defaultPage = pages.keySet().iterator().next();
        return "package steps;\n\n"
                + "import config.DriverFactory;\n"
                + "import io.cucumber.java.pt.Dado;\n"
                + "import org.openqa.selenium.WebDriver;\n"
                + "import pages." + defaultPage + ";\n\n"
                + "public class CommonSteps {\n\n"
                + "    private final WebDriver driver = DriverFactory.getDriver();\n"
                + "    private final " + defaultPage + " page = new " + defaultPage + "(driver);\n\n"
                + "    @Dado(\"acesso a URL {string}\")\n"
                + "    public void acessoAUrl(String valorStep) {\n"
                + "        page.abrir(valorStep);\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarTestRunner() {
        return "package runner;\n\n"
                + "import io.cucumber.junit.Cucumber;\n"
                + "import io.cucumber.junit.CucumberOptions;\n"
                + "import org.junit.runner.RunWith;\n\n"
                + "@RunWith(Cucumber.class)\n"
                + "@CucumberOptions(\n"
                + "    features = \"src/test/resources/features\",\n"
                + "    glue = {\"steps\", \"config\"},\n"
                + "    plugin = {\n"
                + "        \"pretty\",\n"
                + "        \"html:target/relatorio-cucumber.html\",\n"
                + "        \"json:target/relatorio-cucumber.json\"\n"
                + "    },\n"
                + "    monochrome = true\n"
                + ")\n"
                + "public class TestRunner {\n"
                + "}\n";
    }

    private String gerarDriverFactory() {
        return "package config;\n\n"
                + "import io.github.bonigarcia.wdm.WebDriverManager;\n"
                + "import org.openqa.selenium.WebDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeDriver;\n"
                + "import org.openqa.selenium.chrome.ChromeOptions;\n\n"
                + "public class DriverFactory {\n\n"
                + "    private static WebDriver driver;\n\n"
                + "    public static WebDriver getDriver() {\n"
                + "        if (driver == null) {\n"
                + "            WebDriverManager.chromedriver().setup();\n"
                + "            ChromeOptions options = new ChromeOptions();\n"
                + "            // options.addArguments(\"--headless=new\");\n"
                + "            driver = new ChromeDriver(options);\n"
                + "            driver.manage().window().maximize();\n"
                + "        }\n"
                + "        return driver;\n"
                + "    }\n\n"
                + "    public static void encerrar() {\n"
                + "        if (driver != null) {\n"
                + "            driver.quit();\n"
                + "            driver = null;\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarHooks() {
        return "package config;\n\n"
                + "import io.cucumber.java.After;\n"
                + "import io.cucumber.java.AfterStep;\n"
                + "import io.cucumber.java.Before;\n"
                + "import io.cucumber.java.Scenario;\n"
                + "import org.openqa.selenium.OutputType;\n"
                + "import org.openqa.selenium.TakesScreenshot;\n"
                + "import report.ExecutionContext;\n"
                + "import report.FeatureStepCatalog;\n"
                + "import report.HtmlReportGenerator;\n\n"
                + "public class Hooks {\n\n"
                + "    @Before\n"
                + "    public void antes(Scenario scenario) {\n"
                + "        DriverFactory.getDriver();\n"
                + "        // Credenciais via terminal: mvn test -Dambiente.url=X -Dambiente.usuario=Y -Dambiente.senha=Z\n"
                + "        String url = System.getProperty(\"ambiente.url\", \"\");\n"
                + "        String usuario = System.getProperty(\"ambiente.usuario\", \"\");\n"
                + "        String senha = System.getProperty(\"ambiente.senha\", \"\");\n"
                + "        if (!url.isBlank()) {\n"
                + "            DriverFactory.getDriver().get(url);\n"
                + "            System.out.println(\"[Hooks] Navegando para: \" + url);\n"
                + "        }\n"
                + "        if (!usuario.isBlank() && !senha.isBlank()) {\n"
                + "            System.out.println(\"[Hooks] Credenciais recebidas para usuario: \" + usuario);\n"
                + "            // Adapte os seletores abaixo ao login do seu sistema\n"
                + "            try {\n"
                + "                org.openqa.selenium.WebElement campoUser = DriverFactory.getDriver().findElement(org.openqa.selenium.By.cssSelector(\"input[type=text], input[name*=user], input[id*=user], input[name*=login], input[id*=login]\"));\n"
                + "                campoUser.sendKeys(usuario);\n"
                + "                org.openqa.selenium.WebElement campoSenha = DriverFactory.getDriver().findElement(org.openqa.selenium.By.cssSelector(\"input[type=password]\"));\n"
                + "                campoSenha.sendKeys(senha);\n"
                + "                org.openqa.selenium.WebElement btnLogin = DriverFactory.getDriver().findElement(org.openqa.selenium.By.cssSelector(\"button[type=submit], input[type=submit]\"));\n"
                + "                btnLogin.click();\n"
                + "                System.out.println(\"[Hooks] Login efectuado.\");\n"
                + "            } catch (Exception e) {\n"
                + "                System.out.println(\"[Hooks] Login automatico falhou: \" + e.getMessage() + \". Adapte os seletores no Hooks.java.\");\n"
                + "            }\n"
                + "        }\n"
                + "        ExecutionContext.iniciarCenario(scenario.getName(), DriverFactory.getDriver().getCurrentUrl());\n"
                + "        System.out.println(\"Iniciando cenario: \" + scenario.getName());\n"
                + "    }\n\n"
                + "    @AfterStep\n"
                + "    public void aposCadaStep(Scenario scenario) {\n"
                + "        try {\n"
                + "            if (!(DriverFactory.getDriver() instanceof TakesScreenshot takesScreenshot)) {\n"
                + "                return;\n"
                + "            }\n"
                + "            byte[] screenshot = takesScreenshot.getScreenshotAs(OutputType.BYTES);\n"
                + "            int indiceStep = ExecutionContext.proximoIndiceStep(scenario.getName());\n"
                + "            String descricao = FeatureStepCatalog.descricaoDoStep(scenario.getName(), indiceStep);\n"
                + "            String status = scenario.isFailed() ? \"FALHOU\" : \"PASSOU\";\n"
                + "            String nomeArquivo = ExecutionContext.salvarScreenshot(indiceStep, status, screenshot);\n"
                + "            scenario.attach(screenshot, \"image/png\", \"Step \" + indiceStep + \" - \" + descricao);\n"
                + "            ExecutionContext.registrarStep(scenario.getName(), indiceStep, descricao, status, nomeArquivo, scenario.isFailed() ? \"Falha no step\" : null);\n"
                + "        } catch (Exception e) {\n"
                + "            System.out.println(\"Erro ao capturar evidencia do step: \" + e.getMessage());\n"
                + "        }\n"
                + "    }\n\n"
                + "    @After\n"
                + "    public void depois(Scenario scenario) {\n"
                + "        try {\n"
                + "            if (scenario.isFailed()) {\n"
                + "                ExecutionContext.registrarErroFinal(scenario.getName(), \"Cenario falhou. Verifique a última evidência e o console para detalhes.\");\n"
                + "            }\n"
                + "            HtmlReportGenerator.gerar(ExecutionContext.snapshot(), ExecutionContext.getUrlBase());\n"
                + "        } catch (Exception e) {\n"
                + "            System.out.println(\"Erro ao gerar relatório final: \" + e.getMessage());\n"
                + "        } finally {\n"
                + "            DriverFactory.encerrar();\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarStepEvidence() {
        return "package report;\n\n"
                + "public record StepEvidence(int indice, String descricao, String status, String detalheFalha, String imagemRelativa) { }\n";
    }

    private String gerarExecutionContext() {
        return "package report;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.nio.file.Paths;\n"
                + "import java.time.Duration;\n"
                + "import java.time.Instant;\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.LinkedHashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n\n"
                + "public final class ExecutionContext {\n\n"
                + "    private static final Path TARGET_DIR = Paths.get(\"target\");\n"
                + "    private static final Path EVIDENCIAS_DIR = TARGET_DIR.resolve(\"evidencias\");\n"
                + "    private static final Map<String, List<StepEvidence>> STEPS_POR_CENARIO = new LinkedHashMap<>();\n"
                + "    private static final Map<String, Integer> CONTADORES = new ConcurrentHashMap<>();\n"
                + "    private static final Map<String, Instant> INICIO_CENARIO = new ConcurrentHashMap<>();\n"
                + "    private static String urlBase = \"\";\n\n"
                + "    private ExecutionContext() { }\n\n"
                + "    public static synchronized void iniciarCenario(String nomeCenario, String urlAtual) {\n"
                + "        try {\n"
                + "            Files.createDirectories(EVIDENCIAS_DIR);\n"
                + "        } catch (IOException e) {\n"
                + "            throw new IllegalStateException(\"Nao foi possivel criar a pasta de evidencias\", e);\n"
                + "        }\n"
                + "        STEPS_POR_CENARIO.putIfAbsent(nomeCenario, new ArrayList<>());\n"
                + "        CONTADORES.put(nomeCenario, 0);\n"
                + "        INICIO_CENARIO.put(nomeCenario, Instant.now());\n"
                + "        if ((urlBase == null || urlBase.isBlank()) && urlAtual != null) {\n"
                + "            urlBase = urlAtual;\n"
                + "        }\n"
                + "    }\n\n"
                + "    public static synchronized int proximoIndiceStep(String nomeCenario) {\n"
                + "        int proximo = CONTADORES.getOrDefault(nomeCenario, 0) + 1;\n"
                + "        CONTADORES.put(nomeCenario, proximo);\n"
                + "        return proximo;\n"
                + "    }\n\n"
                + "    public static synchronized String salvarScreenshot(int indice, String status, byte[] bytes) throws IOException {\n"
                + "        Files.createDirectories(EVIDENCIAS_DIR);\n"
                + "        String nomeArquivo = \"step-\" + indice + \"-\" + (\"FALHOU\".equalsIgnoreCase(status) ? \"fail\" : \"ok\") + \"-t1.png\";\n"
                + "        Files.write(EVIDENCIAS_DIR.resolve(nomeArquivo), bytes);\n"
                + "        return \"evidencias/\" + nomeArquivo;\n"
                + "    }\n\n"
                + "    public static synchronized void registrarStep(String nomeCenario, int indice, String descricao, String status, String imagemRelativa, String detalheFalha) {\n"
                + "        STEPS_POR_CENARIO.computeIfAbsent(nomeCenario, chave -> new ArrayList<>())\n"
                + "                .add(new StepEvidence(indice, descricao, status, detalheFalha, imagemRelativa));\n"
                + "    }\n\n"
                + "    public static synchronized void registrarErroFinal(String nomeCenario, String mensagem) {\n"
                + "        List<StepEvidence> steps = STEPS_POR_CENARIO.get(nomeCenario);\n"
                + "        if (steps == null || steps.isEmpty()) return;\n"
                + "        StepEvidence ultimo = steps.get(steps.size() - 1);\n"
                + "        steps.set(steps.size() - 1, new StepEvidence(ultimo.indice(), ultimo.descricao(), \"FALHOU\", mensagem, ultimo.imagemRelativa()));\n"
                + "    }\n\n"
                + "    public static synchronized ReportSnapshot snapshot() {\n"
                + "        int total = STEPS_POR_CENARIO.values().stream().mapToInt(List::size).sum();\n"
                + "        int passou = (int) STEPS_POR_CENARIO.values().stream().flatMap(List::stream).filter(s -> \"PASSOU\".equalsIgnoreCase(s.status())).count();\n"
                + "        int falhou = total - passou;\n"
                + "        Duration duracao = Duration.between(INICIO_CENARIO.values().stream().min(Instant::compareTo).orElse(Instant.now()), Instant.now());\n"
                + "        Map<String, List<StepEvidence>> copia = new LinkedHashMap<>();\n"
                + "        for (Map.Entry<String, List<StepEvidence>> entry : STEPS_POR_CENARIO.entrySet()) {\n"
                + "            copia.put(entry.getKey(), new ArrayList<>(entry.getValue()));\n"
                + "        }\n"
                + "        return new ReportSnapshot(copia, total, passou, falhou, duracao.getSeconds());\n"
                + "    }\n\n"
                + "    public static String getUrlBase() {\n"
                + "        return urlBase == null ? \"\" : urlBase;\n"
                + "    }\n\n"
                + "    public record ReportSnapshot(Map<String, List<StepEvidence>> stepsPorCenario, int total, int passou, int falhou, long duracaoSegundos) { }\n"
                + "}\n";
    }

    private String gerarFeatureStepCatalog() {
        return "package report;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.nio.file.Paths;\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.stream.Stream;\n\n"
                + "public final class FeatureStepCatalog {\n\n"
                + "    private static final Map<String, List<String>> STEPS_POR_CENARIO = carregar();\n\n"
                + "    private FeatureStepCatalog() { }\n\n"
                + "    public static String descricaoDoStep(String nomeCenario, int indiceStep) {\n"
                + "        List<String> steps = STEPS_POR_CENARIO.getOrDefault(nomeCenario, List.of());\n"
                + "        if (indiceStep > 0 && indiceStep <= steps.size()) {\n"
                + "            return steps.get(indiceStep - 1);\n"
                + "        }\n"
                + "        return \"Step \" + indiceStep;\n"
                + "    }\n\n"
                + "    private static Map<String, List<String>> carregar() {\n"
                + "        Map<String, List<String>> mapa = new HashMap<>();\n"
                + "        Path raiz = Paths.get(\"src\", \"test\", \"resources\", \"features\");\n"
                + "        if (!Files.exists(raiz)) return mapa;\n"
                + "        try (Stream<Path> arquivos = Files.walk(raiz)) {\n"
                + "            arquivos.filter(path -> path.toString().endsWith(\".feature\")).forEach(path -> carregarArquivo(path, mapa));\n"
                + "        } catch (IOException ignored) { }\n"
                + "        return mapa;\n"
                + "    }\n\n"
                + "    private static void carregarArquivo(Path arquivo, Map<String, List<String>> mapa) {\n"
                + "        try {\n"
                + "            List<String> linhas = Files.readAllLines(arquivo);\n"
                + "            String cenarioAtual = null;\n"
                + "            List<String> steps = new ArrayList<>();\n"
                + "            for (String linha : linhas) {\n"
                + "                String valor = linha == null ? \"\" : linha.trim();\n"
                + "                if (valor.startsWith(\"Cenario:\")) {\n"
                + "                    if (cenarioAtual != null) {\n"
                + "                        mapa.put(cenarioAtual, new ArrayList<>(steps));\n"
                + "                        steps.clear();\n"
                + "                    }\n"
                + "                    cenarioAtual = valor.substring(8).trim();\n"
                + "                } else if (valor.startsWith(\"Dado \") || valor.startsWith(\"Quando \") || valor.startsWith(\"Entao \") || valor.startsWith(\"E \")) {\n"
                + "                    int idx = valor.indexOf(' ');\n"
                + "                    steps.add(valor.substring(idx + 1).trim());\n"
                + "                }\n"
                + "            }\n"
                + "            if (cenarioAtual != null) {\n"
                + "                mapa.put(cenarioAtual, new ArrayList<>(steps));\n"
                + "            }\n"
                + "        } catch (IOException ignored) { }\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarHtmlReportGenerator() {
        return "package report;\n\n"
                + "import java.io.IOException;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.nio.file.Paths;\n"
                + "import java.time.LocalDateTime;\n"
                + "import java.time.format.DateTimeFormatter;\n"
                + "import java.util.List;\n"
                + "import java.util.stream.Collectors;\n\n"
                + "public final class HtmlReportGenerator {\n\n"
                + "    private HtmlReportGenerator() { }\n\n"
                + "    public static void gerar(ExecutionContext.ReportSnapshot snapshot, String urlBase) {\n"
                + "        try {\n"
                + "            Path target = Paths.get(\"target\");\n"
                + "            Files.createDirectories(target);\n"
                + "            Files.writeString(target.resolve(\"relatorio.html\"), montarHtml(snapshot, urlBase), StandardCharsets.UTF_8);\n"
                + "        } catch (IOException e) {\n"
                + "            throw new IllegalStateException(\"Erro ao escrever relatorio.html\", e);\n"
                + "        }\n"
                + "    }\n\n"
                + "    private static String montarHtml(ExecutionContext.ReportSnapshot snapshot, String urlBase) {\n"
                + "        int total = snapshot.total();\n"
                + "        int passou = snapshot.passou();\n"
                + "        int falhou = snapshot.falhou();\n"
                + "        int percentual = total == 0 ? 0 : (int) Math.round((passou * 100.0) / total);\n"
                + "        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\"));\n"
                + "        String duracao = snapshot.duracaoSegundos() + \"s\";\n"
                + "        List<StepEvidence> steps = snapshot.stepsPorCenario().values().stream().flatMap(List::stream).collect(Collectors.toList());\n"
                + "        String tabela = steps.stream().map(step -> \"<tr><td><b>\" + step.indice() + \"</b></td><td>\" + esc(step.descricao()) + \"</td><td><span class='badge \" + cssStatus(step.status()) + \"'>\" + simbolo(step.status()) + \" \" + esc(step.status()) + \"</span></td><td style='color:#DC2626;font-size:12px'>\" + esc(vazioOuTraco(step.detalheFalha())) + \"</td></tr>\").collect(Collectors.joining());\n"
                + "        String evidencias = steps.stream().map(step -> \"<div class='step-box'><div class='step-head'><div class='step-num'>\" + step.indice() + \"</div><div style='flex:1;font-size:13px;font-weight:500'>\" + esc(step.descricao()) + \"</div><span class='badge \" + cssStatus(step.status()) + \"'>\" + simbolo(step.status()) + \" \" + esc(step.status()) + \"</span></div><div class='step-body'>\" + (step.detalheFalha() == null || step.detalheFalha().isBlank() ? \"\" : \"<div class='erro'>\" + esc(step.detalheFalha()) + \"</div>\") + \"<img src='\" + esc(step.imagemRelativa()) + \"' alt='Step \" + step.indice() + \"'></div></div>\").collect(Collectors.joining());\n"
                + "        String labels = steps.stream().map(step -> \"'Step \" + step.indice() + \"'\").collect(Collectors.joining(\",\"));\n"
                + "        String dataPassou = steps.stream().map(step -> \"PASSOU\".equalsIgnoreCase(step.status()) ? \"1\" : \"0\").collect(Collectors.joining(\",\"));\n"
                + "        String dataFalhou = steps.stream().map(step -> \"FALHOU\".equalsIgnoreCase(step.status()) ? \"1\" : \"0\").collect(Collectors.joining(\",\"));\n"
                + "        return \"<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Relatório de Execução</title><script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script><style>*{box-sizing:border-box;margin:0;padding:0}body{font-family:'Segoe UI',Arial,sans-serif;background:#F1F5F9;color:#1E293B}.header{background:linear-gradient(135deg,#1F4E79 0%,#2563EB 100%);color:white;padding:32px 40px}.header h1{font-size:24px;font-weight:700;margin-bottom:6px}.header .meta{font-size:13px;opacity:.85}.header .meta span{margin-right:20px}.body{max-width:1100px;margin:0 auto;padding:32px 24px}.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:28px}.card{background:white;border-radius:12px;padding:20px 24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}.card-label{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.8px;color:#64748B;margin-bottom:6px}.card-value{font-size:32px;font-weight:700}.charts{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:24px}.chart-box{background:white;border-radius:12px;padding:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}.chart-box h2{font-size:15px;font-weight:600;margin-bottom:16px;color:#1E293B}.section{background:white;border-radius:12px;padding:24px;margin-bottom:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}.section h2{font-size:15px;font-weight:600;margin-bottom:16px;padding-bottom:10px;border-bottom:2px solid #E2E8F0}table{width:100%;border-collapse:collapse;font-size:13px}thead th{background:#F8FAFC;color:#475569;font-size:11px;text-transform:uppercase;padding:10px 14px;text-align:left;border-bottom:2px solid #E2E8F0}tbody td{padding:11px 14px;border-bottom:1px solid #F1F5F9;vertical-align:top}tbody tr:hover{background:#F8FAFC}.badge{display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:600}.passou{background:#DCFCE7;color:#166534}.falhou{background:#FEE2E2;color:#DC2626}.step-box{border:1px solid #E2E8F0;border-radius:8px;margin-bottom:12px;overflow:hidden}.step-head{display:flex;align-items:center;gap:10px;padding:12px 16px;background:#F8FAFC;border-bottom:1px solid #E2E8F0}.step-num{width:26px;height:26px;border-radius:50%;background:#1F4E79;color:white;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0}.step-body{padding:12px 16px}.erro{color:#DC2626;background:#FEF2F2;padding:8px;border-radius:6px;font-size:12px;margin-top:6px;font-family:monospace}.step-body img{max-width:100%;border:1px solid #E2E8F0;border-radius:6px;margin-top:8px}.bar{height:8px;background:#E2E8F0;border-radius:4px;overflow:hidden;margin-top:8px}.bar-fill{height:100%;border-radius:4px;background:linear-gradient(90deg,#16A34A,#22C55E)}.footer{text-align:center;padding:24px;color:#94A3B8;font-size:12px}</style></head><body><div class='header'><h1>&#128203; Relatório de Execução</h1><div class='meta'><span>&#128279; \" + esc(urlBase == null || urlBase.isBlank() ? \"URL não capturada\" : urlBase) + \"</span><span>&#128336; \" + dataHora + \"</span><span>&#9200; Duração: \" + duracao + \"</span></div></div><div class='body'><div class='cards'><div class='card'><div class='card-label'>Total</div><div class='card-value' style='color:#2563EB'>\" + total + \"</div></div><div class='card'><div class='card-label'>Passou</div><div class='card-value' style='color:#16A34A'>\" + passou + \"</div></div><div class='card'><div class='card-label'>Falhou</div><div class='card-value' style='color:#DC2626'>\" + falhou + \"</div></div><div class='card'><div class='card-label'>% Sucesso</div><div class='card-value' style='color:#16A34A'>\" + percentual + \"%</div><div class='bar'><div class='bar-fill' style='width:\" + percentual + \"%'></div></div></div></div><div class='charts'><div class='chart-box'><h2>&#128202; Resultado por Step</h2><canvas id='c1'></canvas></div><div class='chart-box'><h2>&#128200; Resumo</h2><canvas id='c2'></canvas></div></div><div class='section'><h2>&#128203; Tabela de Steps</h2><table><thead><tr><th>#</th><th>Descrição</th><th>Status</th><th>Detalhe da falha</th></tr></thead><tbody>\" + tabela + \"</tbody></table></div><div class='section'><h2>&#128247; Evidências</h2>\" + evidencias + \"</div><div class='footer'>Gerado pelo Qorbit v2.0</div></div><script>new Chart(document.getElementById('c1').getContext('2d'),{type:'bar',data:{labels:[\" + labels + \"],datasets:[{label:'Passou',data:[\" + dataPassou + \"],backgroundColor:'#22C55E'},{label:'Falhou',data:[\" + dataFalhou + \"],backgroundColor:'#EF4444'}]},options:{responsive:true,scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{position:'top'}}}});new Chart(document.getElementById('c2').getContext('2d'),{type:'bar',data:{labels:['Passou','Falhou'],datasets:[{label:'Steps',data:[\" + passou + \",\" + falhou + \"],backgroundColor:['#22C55E','#EF4444']}]},options:{responsive:true,indexAxis:'y',scales:{x:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{display:false}}}});</script></body></html>\";\n"
                + "    }\n\n"
                + "    private static String cssStatus(String status) {\n"
                + "        return \"FALHOU\".equalsIgnoreCase(status) ? \"falhou\" : \"passou\";\n"
                + "    }\n\n"
                + "    private static String simbolo(String status) {\n"
                + "        return \"FALHOU\".equalsIgnoreCase(status) ? \"&#10007;\" : \"&#10003;\";\n"
                + "    }\n\n"
                + "    private static String vazioOuTraco(String valor) {\n"
                + "        return valor == null || valor.isBlank() ? \"—\" : valor;\n"
                + "    }\n\n"
                + "    private static String esc(String valor) {\n"
                + "        if (valor == null) return \"\";\n"
                + "        return valor.replace(\"&\", \"&amp;\").replace(\"<\", \"&lt;\").replace(\">\", \"&gt;\").replace(\"\\\"\", \"&quot;\");\n"
                + "    }\n"
                + "}\n";
    }

    private String gerarPomXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.qorbit.engine</groupId>\n"
                + "    <artifactId>qorbit-tests-export</artifactId>\n"
                + "    <version>2.0.0</version>\n"
                + "    <properties>\n"
                + "        <maven.compiler.source>17</maven.compiler.source>\n"
                + "        <maven.compiler.target>17</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "        <selenium.version>4.18.1</selenium.version>\n"
                + "        <cucumber.version>7.15.0</cucumber.version>\n"
                + "        <maven.compiler.plugin.version>3.11.0</maven.compiler.plugin.version>\n"
                + "        <maven.surefire.plugin.version>3.2.5</maven.surefire.plugin.version>\n"
                + "    </properties>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>org.seleniumhq.selenium</groupId>\n"
                + "            <artifactId>selenium-java</artifactId>\n"
                + "            <version>${selenium.version}</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>io.github.bonigarcia</groupId>\n"
                + "            <artifactId>webdrivermanager</artifactId>\n"
                + "            <version>5.7.0</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>io.cucumber</groupId>\n"
                + "            <artifactId>cucumber-java</artifactId>\n"
                + "            <version>${cucumber.version}</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>io.cucumber</groupId>\n"
                + "            <artifactId>cucumber-junit</artifactId>\n"
                + "            <version>${cucumber.version}</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "            <scope>test</scope>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "    <build>\n"
                + "        <plugins>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                <version>${maven.compiler.plugin.version}</version>\n"
                + "                <configuration>\n"
                + "                    <release>17</release>\n"
                + "                    <encoding>UTF-8</encoding>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-surefire-plugin</artifactId>\n"
                + "                <version>${maven.surefire.plugin.version}</version>\n"
                + "                <configuration>\n"
                + "                    <includes>\n"
                + "                        <include>**/Test*.java</include>\n"
                + "                        <include>**/*Test.java</include>\n"
                + "                        <include>**/*Runner.java</include>\n"
                + "                    </includes>\n"
                + "                    <systemPropertyVariables>\n"
                + "                        <file.encoding>UTF-8</file.encoding>\n"
                + "                    </systemPropertyVariables>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "        </plugins>\n"
                + "    </build>\n"
                + "</project>\n";
    }

    private String gerarHealingProperties() {
        return """
# ============================================================
# Self-Healing Testing — Configuração opcional
# ============================================================
# O Self-Healing funciona SEM IA usando estratégias locais:
#   1. Tenta pelo texto visível do elemento
#   2. Tenta pelo aria-label
#   3. Tenta pelo href parcial
#
# Para activar a IA (nível extra de resiliência):
#   1. Mude healing.ai.habilitado para true
#   2. Preencha o endpoint e modelo da sua IA
#   3. Se a IA não precisar de chave, deixe healing.ai.api-key em branco
#      e mude healing.ai.auth-tipo para none
#
# ONDE ACTUALIZAR A CHAVE:
#   Ficheiro: src/test/resources/healing.properties
#   Campo:    healing.ai.api-key=SUA_CHAVE_AQUI
#
# Exemplo com Groq (gratuito — https://console.groq.com):
#   healing.ai.habilitado=true
#   healing.ai.endpoint=https://api.groq.com/openai/v1/chat/completions
#   healing.ai.modelo=llama-3.3-70b-versatile
#   healing.ai.auth-tipo=bearer
#   healing.ai.api-key=gsk_xxxx
#
# Exemplo com IA interna sem autenticação:
#   healing.ai.habilitado=true
#   healing.ai.endpoint=https://minha-ia-interna.com/v1/chat/completions
#   healing.ai.modelo=meu-modelo
#   healing.ai.auth-tipo=none
#   healing.ai.api-key=
# ============================================================

healing.ai.habilitado=false
healing.ai.endpoint=https://api.groq.com/openai/v1/chat/completions
healing.ai.modelo=llama-3.3-70b-versatile
healing.ai.auth-tipo=bearer
healing.ai.api-key=
""";
    }

    private String gerarHealingService() {
        // Lê o template do HealingService do ficheiro de recursos
        // Evita escaping complexo de strings Java dentro de strings Java
        try (java.io.InputStream is = getClass().getClassLoader()
                .getResourceAsStream("HealingServiceTemplate.java")) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[Qorbit] Erro ao ler HealingServiceTemplate.java: " + e.getMessage());
        }
        return "// HealingService nao disponivel\n";
    }

    private String gerarReadme() {
        return "# Qorbit - Projeto Exportado\n\n"
                + "## Como executar\n\n"
                + "Este projeto exportado **nao e um backend Spring Boot**.\n\n"
                + "Use um destes comandos dentro da pasta `qorbit-tests-export`:\n\n"
                + "```bash\n"
                + "mvn test\n"
                + "```\n\n"
                + "No Windows, voce tambem pode executar:\n\n"
                + "```bash\n"
                + "executar-testes.bat\n"
                + "```\n\n"
                + "No Linux/Mac:\n\n"
                + "```bash\n"
                + "chmod +x executar-testes.sh\n"
                + "./executar-testes.sh\n"
                + "```\n\n"
                + "## Importante\n\n"
                + "Nao use `mvn spring-boot:run` neste projeto exportado, porque ele nao possui aplicacao Spring Boot.\n\n"
                + "## Melhorias incluidas nesta versao\n"
                + "- imports obrigatorios adicionados nos Page Objects gerados\n"
                + "- configuracao do Maven reforcada para UTF-8 e execucao do runner\n"
                + "- script `.bat` e `.sh` para facilitar a execucao\n"
                + "- imports explicitos nas classes de steps\n"
                + "- nomes de metodos consistentes entre Page Object e Steps\n"
                + "- seletor tecnico respeitando CSS, XPATH, ID, NAME e SHADOW_CSS\n"
                + "- suporte inicial a iframe no codigo exportado\n"
                + "- waits mais fortes para UI dinamica\n"
                + "- tratamento especial para select customizado, autocomplete e datepicker\n"
                + "- suporte a UTF-8 no projeto exportado\n"
                + "- tratamento de strings com aspas e quebras de linha\n"
                + "- captura de evidencias por step (PASS e FAIL)\n"
                + "- relatorio HTML estilo Qorbit em target/relatorio.html\n"
                + "- screenshots fisicas em target/evidencias\n";
    }

    private String gerarExecutarTestesBat() {
        return "@echo off\r\n"
                + "cd /d %~dp0\r\n"
                + "mvn test\r\n";
    }

    private String gerarExecutarTestesSh() {
        return "#!/usr/bin/env bash\n"
                + "set -e\n"
                + "cd \"$(dirname \"$0\")\"\n"
                + "mvn test\n";
    }

    private String safe(String valor) {
        return valor == null ? "" : valor;
    }

    private record LocatorSpec(String tipo, String valor) { }
    private record ElementContext(String framePath, String componentType, String shadowDom) { }

    private static class PageObjectSpec {
        private final String className;
        private final String rawPageName;
        private final List<ElementSpec> elementos = new ArrayList<>();
        private final Map<String, String> logicalToMethod = new LinkedHashMap<>();

        private PageObjectSpec(String className, String rawPageName) {
            this.className = className;
            this.rawPageName = rawPageName;
        }
    }

    private record ElementSpec(Elemento source, String methodName, LocatorSpec locator, ElementContext context) { }
    private record ResolvedStep(String pageClassName, String methodName, String componentType) { }
}
