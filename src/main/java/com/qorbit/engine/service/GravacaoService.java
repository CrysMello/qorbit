package com.qorbit.engine.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.gerador.NormalizadorNomes;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ElementoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
public class GravacaoService {
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ElementoRepository elementoRepo;
    @Autowired private QorbitUserRepository userRepo;
    @Autowired(required = false) private SimpMessagingTemplate mensageria;
    @Autowired private NormalizadorNomes normalizadorNomes;

    private final AtomicBoolean gravando = new AtomicBoolean(false);
    private String urlBase = "";
    private volatile String tokenSessao;
    private final List<StepTeste> stepsGravados = Collections.synchronizedList(new ArrayList<>());
    private final List<Elemento> elementosGravados = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger contadorStep = new AtomicInteger(0);
    private final Set<String> seletoresVistos = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> assinaturasRecentes = new ConcurrentHashMap<>();
    private static final long JANELA_DUPLICIDADE_MS = 1800;

    private static final Pattern PARECE_VALOR_DATA = Pattern.compile(
            "^\\d{4,8}$"
            + "|^\\d{1,2}[/\\-.\\s]\\d{1,2}[/\\-.\\s]\\d{2,4}$"
            + "|^\\d{4}[/\\-]\\d{2}[/\\-]\\d{2}$"
    );

    private final Map<String, String> ultimoValorPorCampo = new ConcurrentHashMap<>();
    private static final Set<String> TAGS_CONTAINER = Set.of("div", "section", "form", "article", "main", "aside", "header", "footer", "fieldset", "ul", "ol", "li", "table", "tbody", "tr", "td", "th");
    private static final Set<String> ROLES_CONTAINER = Set.of("main", "region", "group", "list", "grid", "table", "form", "document", "presentation", "none");

    /**
     * Obtém o usuário autenticado do contexto de segurança
     */
    private QorbitUser getUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            return userRepo.findByEmailIgnoreCase(email).orElse(null);
        }
        return null;
    }

    public Map<String, Object> iniciarGravacao(String url) {
        if (gravando.get()) {
            return Map.of("erro", "Já existe uma gravação em andamento.");
        }
        gravando.set(true);
        urlBase = url;
        tokenSessao = UUID.randomUUID().toString();
        stepsGravados.clear();
        elementosGravados.clear();
        seletoresVistos.clear();
        assinaturasRecentes.clear();
        ultimoValorPorCampo.clear();
        contadorStep.set(0);
        return Map.of("mensagem", "Gravação iniciada", "url", url, "token", tokenSessao);
    }

    /** Valida o token da sessão de gravação atual (usado pela captura client-side via extensão de navegador). */
    public boolean tokenValido(String token) {
        return gravando.get() && tokenSessao != null && tokenSessao.equals(token);
    }

    /** Retorna a URL alvo da gravação atual se o token bater, senão null — usado pela página de bridge. */
    public String getUrlSeTokenValido(String token) {
        return tokenValido(token) ? urlBase : null;
    }

    public Map<String, Object> pararGravacao(String nomeCaso, String modulo) {
        gravando.set(false);
        tokenSessao = null;

        if ("_descartar_".equals(nomeCaso)) {
            stepsGravados.clear();
            elementosGravados.clear();
            assinaturasRecentes.clear();
            return Map.of("mensagem", "Descartado");
        }

        if (stepsGravados.isEmpty()) {
            return Map.of("erro", "Nenhum step gravado. Interaja com a aplicação antes de parar.");
        }

        QorbitUser usuario = getUsuarioAutenticado();
        if (usuario == null) {
            return Map.of("erro", "Usuário não autenticado. Faça login para gravar testes.");
        }

        // Salva elementos com o usuário correto
        elementosGravados.forEach(el -> {
            boolean jaExiste = elementoRepo.findByNomeLogicoAndPagina(el.getNomeLogico(), el.getPagina())
                    .filter(ex -> ex.getUsuario() != null && ex.getUsuario().getId().equals(usuario.getId()))
                    .isPresent();
            if (!jaExiste) {
                el.setUsuario(usuario);
                elementoRepo.save(el);
            }
        });

        CasoDeTeste caso = new CasoDeTeste();
        caso.setNome(nomeCaso != null && !nomeCaso.isBlank() ? nomeCaso : "Gravacao_" + System.currentTimeMillis());
        caso.setModulo(modulo != null && !modulo.isBlank() ? modulo : "Gravado");
        caso.setUrlAlvo(urlBase);
        caso.setCodigo("CT-" + String.format("%02d", casoRepo.count() + 1));
        caso.setStatus("ATIVO");
        caso.setUsuario(usuario);

        List<StepTeste> copia = new ArrayList<>();
        for (int i = 0; i < stepsGravados.size(); i++) {
            StepTeste orig = stepsGravados.get(i);
            StepTeste s = new StepTeste();
            s.setNumeroStep(i + 1);
            s.setAcao(orig.getAcao());
            s.setNomeLogicoElemento(orig.getNomeLogicoElemento());
            s.setValorEntrada(orig.getValorEntrada());
            s.setDescricaoGherkin(orig.getDescricaoGherkin());
            s.setResultadoEsperado(orig.getResultadoEsperado());
            s.setCasoDeTeste(caso);
            copia.add(s);
        }
        caso.setSteps(copia);

        CasoDeTeste salvo = casoRepo.save(caso);
        int total = copia.size();

        stepsGravados.clear();
        elementosGravados.clear();
        assinaturasRecentes.clear();

        return Map.of(
                "mensagem", total + " steps salvos com sucesso",
                "casoId", salvo.getId(),
                "totalSteps", total,
                "totalElementos", seletoresVistos.size()
        );
    }

    public Map<String, Object> registrarStep(Map<String, Object> evento) {
        if (!gravando.get()) return Map.of("ignorado", true);

        String tipo = str(evento, "tipo").toUpperCase(Locale.ROOT);
        String seletor = str(evento, "seletor");
        String tipoSel = str(evento, "tipoSeletor", "CSS");
        String valor = str(evento, "valor");
        String tagName = str(evento, "tagName");
        String textoElem = str(evento, "textoElemento");
        String urlAtual = str(evento, "url", urlBase);
        String pagina = extrairNomePagina(urlAtual);
        String label = str(evento, "label");
        String placeholder = str(evento, "placeholder");
        String ariaLabel = str(evento, "ariaLabel");
        String role = str(evento, "role");
        String href = str(evento, "href");
        String componentType = str(evento, "componentType");
        String framePath = str(evento, "framePath", "root");
        String shadowPath = str(evento, "shadowPath");

        boolean isDatePicker = "DATEPICKER".equalsIgnoreCase(componentType);

        if ((seletor == null || seletor.isBlank()) && !"NAVIGATE".equalsIgnoreCase(tipo)) {
            return Map.of("ignorado", true);
        }

        String assinatura = String.join("|",
                tipo,
                defaultIfBlank(seletor, "-"),
                defaultIfBlank(valor, "-"),
                defaultIfBlank(urlAtual, "-"));
        long agora = System.currentTimeMillis();
        Long ultimoRegistro = assinaturasRecentes.get(assinatura);
        if (ultimoRegistro != null && (agora - ultimoRegistro) < JANELA_DUPLICIDADE_MS) {
            return Map.of("ignorado", true, "motivo", "duplicado");
        }
        assinaturasRecentes.put(assinatura, agora);
        limparAssinaturasAntigas(agora);

        StepTeste step = new StepTeste();
        step.setNumeroStep(contadorStep.incrementAndGet());

        String acao;
        String nomeLogico = null;
        String descricao;
        String descricaoElemento = melhorDescricaoElemento(label, placeholder, ariaLabel, textoElem, href, seletor);

        switch (tipo) {
            case "CLICK" -> {
                if (isDatePicker) {
                    contadorStep.decrementAndGet();
                    return Map.of("ignorado", true, "motivo", "click_interno_datepicker");
                }

                String nomeTecnico = defaultIfBlank(descricaoElemento, "").toLowerCase(Locale.ROOT);
                if (nomeTecnico.contains("muipickers")
                        || nomeTecnico.contains("muiiconbutton")
                        || nomeTecnico.contains("pickersyear")
                        || nomeTecnico.matches("botao\\d+")
                        || nomeTecnico.contains("buttonmuibuttonbaseroot")
                        || nomeTecnico.contains("divmuitypographyroot")) {
                    contadorStep.decrementAndGet();
                    return Map.of("ignorado", true, "motivo", "click_tecnico_datepicker");
                }

                String tagLower = tagName != null ? tagName.toLowerCase(Locale.ROOT) : "";
                String roleLower = role != null ? role.toLowerCase(Locale.ROOT) : "";
                boolean isContainer = TAGS_CONTAINER.contains(tagLower) && ROLES_CONTAINER.contains(roleLower);
                boolean temHref = href != null && !href.isBlank() && !href.equals("#");
                boolean isButton = "button".equals(tagLower) || "a".equals(tagLower) || "input".equals(tagLower)
                        || "BUTTON".equalsIgnoreCase(componentType) || "LINK".equalsIgnoreCase(componentType);
                boolean temOnclick = str(evento, "temOnclick").equalsIgnoreCase("true");
                boolean temCursorPointer = str(evento, "temCursorPointer").equalsIgnoreCase("true");
                boolean temEventoJs = str(evento, "temEventoJs").equalsIgnoreCase("true");
                boolean eInteractivo = temHref || isButton || temOnclick || temCursorPointer || temEventoJs
                        || "button".equalsIgnoreCase(role) || "link".equalsIgnoreCase(role);
                if (isContainer && !eInteractivo) {
                    contadorStep.decrementAndGet();
                    return Map.of("ignorado", true, "motivo", "container_sem_interactividade");
                }
                acao = "CLICAR";
                String categoria = inferirCategoriaClique(tagName, role, href, componentType);
                nomeLogico = gerarNomeLogico(categoria, descricaoElemento, seletor);
                descricao = "clico em \"" + nomeLogico + "\"";
            }
            case "INPUT", "CHANGE" -> {
                acao = "PREENCHER";
                nomeLogico = gerarNomeLogico(isDatePicker ? "date" : "input", descricaoElemento, seletor);

                String chaveField = seletor + "|" + pagina;
                String ultimoValor = ultimoValorPorCampo.get(chaveField);
                if (ultimoValor != null) {
                    for (int i = stepsGravados.size() - 1; i >= 0; i--) {
                        StepTeste stepExistente = stepsGravados.get(i);
                        if (nomeLogico.equals(stepExistente.getNomeLogicoElemento())
                                && "PREENCHER".equals(stepExistente.getAcao())) {
                            stepExistente.setValorEntrada(valor);
                            stepExistente.setDescricaoGherkin("preencho \"" + nomeLogico + "\" com \"" + valor + "\"");
                            ultimoValorPorCampo.put(chaveField, valor);
                            contadorStep.decrementAndGet();
                            return Map.of("stepNumero", stepExistente.getNumeroStep(), "acao", acao, "consolidado", true);
                        }
                    }
                }
                ultimoValorPorCampo.put(chaveField, valor);
                step.setValorEntrada(valor);
                descricao = "preencho \"" + nomeLogico + "\" com \"" + valor + "\"";
            }
            case "SELECT" -> {
                acao = "SELECIONAR";
                nomeLogico = gerarNomeLogico("select", descricaoElemento, seletor);
                step.setValorEntrada(valor);
                descricao = "seleciono \"" + valor + "\" em \"" + nomeLogico + "\"";
            }
            case "NAVIGATE" -> {
                acao = "NAVEGAR";
                step.setValorEntrada(urlAtual);
                descricao = "acesso a URL \"" + urlAtual + "\"";
            }
            case "SUBMIT" -> {
                acao = "CLICAR";
                nomeLogico = gerarNomeLogico("button", defaultIfBlank(descricaoElemento, "Enviar"), seletor);
                descricao = "clico em \"" + nomeLogico + "\"";
            }
            default -> {
                contadorStep.decrementAndGet();
                return Map.of("ignorado", true);
            }
        }

        step.setAcao(acao);
        step.setNomeLogicoElemento(nomeLogico);
        step.setDescricaoGherkin(descricao);
        stepsGravados.add(step);

        if (nomeLogico != null && !nomeLogico.isBlank() && seletor != null && !seletor.isBlank() && seletoresVistos.add(tipoSel + "|" + seletor)) {
            Elemento el = new Elemento();
            el.setNomeLogico(nomeLogico);
            el.setPagina(pagina);
            el.setTipoSeletor(tipoSel);

            String seletorFinal = seletor;
            String qualidade = avaliarQualidadeSelector(seletor);

            if ("BAIXO".equals(qualidade)) {
                String htmlResumido = "<" + tagName
                    + (label != null && !label.isBlank() ? " aria-label=\"" + label + "\"" : "")
                    + (href != null && !href.isBlank() ? " href=\"" + href + "\"" : "")
                    + ">";
                String seletorMelhorado = simplificarHref(seletor, htmlResumido);
                if (seletorMelhorado != null && !seletorMelhorado.equals(seletor)) {
                    System.out.println("[Qorbit] Selector melhorado na gravação: "
                        + seletor.substring(0, Math.min(60, seletor.length()))
                        + " → " + seletorMelhorado
                        + " (era: " + qualidade + ")");
                    seletorFinal = seletorMelhorado;
                    qualidade = avaliarQualidadeSelector(seletorFinal);
                }
            }

            el.setSeletorTecnico(seletorFinal);
            el.setStatus("ATIVO");
            String descBase = montarDescricaoElemento(label, placeholder, ariaLabel, role, href, framePath, shadowPath, componentType);
            StringBuilder fp = new StringBuilder(descBase);
            appendAtributoDoSeletor(fp, seletorFinal, "id",          "#");
            appendAtributoDoSeletor(fp, seletorFinal, "name",        null);
            appendAtributoDoSeletor(fp, seletorFinal, "data-testid", null);
            appendAtributoDoSeletor(fp, seletorFinal, "data-qa",     null);
            appendAtributoDoSeletor(fp, seletorFinal, "type",        null);
            appendAtributoDoSeletor(fp, seletorFinal, "value",       null);
            if (textoElem != null && !textoElem.isBlank()) {
                String textoSemEspacos = textoElem.replaceAll("\\s+", "");
                if (!PARECE_VALOR_DATA.matcher(textoSemEspacos).matches()) {
                    String textoTrunc = textoElem.length() > 40 ? textoElem.substring(0, 40) : textoElem;
                    fp.append("|texto=").append(textoTrunc);
                }
            }
            String descFinal = fp.toString();
            if (descFinal.length() > 190) descFinal = descFinal.substring(0, 190);
            el.setDescricao(descFinal + " | selectorScore=" + qualidade);
            elementosGravados.add(el);
        }

        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("numeroStep", step.getNumeroStep());
        notif.put("acao", acao);
        notif.put("nomeLogico", nomeLogico != null ? nomeLogico : "");
        notif.put("valor", valor != null ? valor : "");
        notif.put("seletor", seletor != null ? seletor : "");
        notif.put("gherkin", step.getDescricaoGherkin() != null ? step.getDescricaoGherkin() : "");
        notif.put("totalSteps", stepsGravados.size());
        if (mensageria != null) {
            mensageria.convertAndSend("/topic/gravacao", notif);
        }

        return Map.of("stepNumero", step.getNumeroStep(), "acao", acao);
    }

    public Map<String, Object> getStatus() {
        return Map.of(
                "gravando", gravando.get(),
                "url", urlBase,
                "token", tokenSessao != null ? tokenSessao : "",
                "totalSteps", stepsGravados.size(),
                "steps", stepsGravados.stream().map(s -> Map.of(
                        "numero", s.getNumeroStep(),
                        "acao", s.getAcao() != null ? s.getAcao() : "",
                        "elemento", s.getNomeLogicoElemento() != null ? s.getNomeLogicoElemento() : "",
                        "valor", s.getValorEntrada() != null ? s.getValorEntrada() : "",
                        "gherkin", s.getDescricaoGherkin() != null ? s.getDescricaoGherkin() : ""
                )).toList()
        );
    }

    public void removerStep(int numeroStep) {
        stepsGravados.removeIf(s -> Objects.equals(s.getNumeroStep(), numeroStep));
        AtomicInteger num = new AtomicInteger(1);
        stepsGravados.forEach(s -> s.setNumeroStep(num.getAndIncrement()));
        contadorStep.set(stepsGravados.size());
    }

    public boolean isGravando() { return gravando.get(); }

    private void limparAssinaturasAntigas(long agora) {
        assinaturasRecentes.entrySet().removeIf(e -> (agora - e.getValue()) > JANELA_DUPLICIDADE_MS * 3);
    }

    private String montarDescricaoElemento(String label, String placeholder, String ariaLabel, String role,
                                           String href, String framePath, String shadowPath, String componentType) {
        List<String> partes = new ArrayList<>();
        if (!label.isBlank())         partes.add("label=" + label);
        if (!placeholder.isBlank())   partes.add("placeholder=" + placeholder);
        if (!ariaLabel.isBlank())     partes.add("ariaLabel=" + ariaLabel);
        if (!role.isBlank())          partes.add("role=" + role);
        if (!href.isBlank())          partes.add("href=" + href);
        if (!framePath.isBlank())     partes.add("frame=" + framePath);
        if (!shadowPath.isBlank())    partes.add("shadow=" + shadowPath);
        if (!componentType.isBlank()) partes.add("componentType=" + componentType);
        return String.join("|", partes);
    }

    private void appendAtributoDoSeletor(StringBuilder sb, String seletor,
                                         String atributo, String prefixo) {
        if (seletor == null || seletor.isBlank()) return;
        try {
            String val = null;
            if (prefixo != null && seletor.trim().startsWith(prefixo)) {
                String resto = seletor.trim().substring(prefixo.length());
                int fim = 0;
                while (fim < resto.length()) {
                    char c = resto.charAt(fim);
                    if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') break;
                    fim++;
                }
                val = resto.substring(0, fim);
            } else {
                String busca = "[" + atributo + "=";
                int idx = seletor.indexOf(busca);
                if (idx < 0) return;
                int inicio = idx + busca.length();
                if (inicio >= seletor.length()) return;
                char abre = seletor.charAt(inicio);
                if (abre == '\'' || abre == '"') inicio++;
                int fim = inicio;
                while (fim < seletor.length()) {
                    char c = seletor.charAt(fim);
                    if (c == '\'' || c == '"' || c == ']') break;
                    fim++;
                }
                val = seletor.substring(inicio, fim).trim();
            }
            if (val != null && !val.isBlank()) {
                sb.append("|").append(atributo).append("=").append(val);
            }
        } catch (Exception ignored) { }
    }

    private String inferirCategoriaClique(String tagName, String role, String href, String componentType) {
        String tag = defaultIfBlank(tagName, "").toLowerCase(Locale.ROOT);
        String roleNorm = defaultIfBlank(role, "").toLowerCase(Locale.ROOT);
        String comp = defaultIfBlank(componentType, "").toUpperCase(Locale.ROOT);
        if ("TAB".equals(comp) || "tab".equals(roleNorm)) return "aba";
        if ("MENU".equals(comp) || roleNorm.contains("menu")) return "menu";
        if ("CARD".equals(comp)) return "card";
        if ("a".equals(tag) || !href.isBlank()) return "link";
        if ("button".equals(tag) || roleNorm.contains("button")) return "button";
        return "button";
    }

    private String melhorDescricaoElemento(String label, String placeholder, String ariaLabel, String textoElem,
                                           String href, String seletor) {
        List<String> candidatos = List.of(label, ariaLabel, placeholder, textoElem, href, seletor);
        for (String candidato : candidatos) {
            if (candidato == null) continue;
            String limpo = candidato.replaceAll("https?://", "")
                    .replaceAll("[#._\\-]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (limpo.isBlank() || limpo.length() < 2) continue;
            String semEspacos = limpo.replaceAll("\\s+", "");
            if (PARECE_VALOR_DATA.matcher(semEspacos).matches()) continue;
            return limpo;
        }
        return "elemento";
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private String str(Map<String, Object> m, String key, String defaultVal) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : defaultVal;
    }

    private String gerarNomeLogico(String tipo, String texto, String seletor) {
        String base = (texto != null && !texto.isBlank())
                ? texto.trim()
                : (seletor != null ? seletor.replaceAll("[^a-zA-Z0-9 ]", " ").trim() : "");

        String prefixo = switch (tipo) {
            case "button", "link", "menu", "aba", "card" -> "botao";
            case "select" -> "select";
            case "date" -> "data";
            default -> "campo";
        };

        return normalizadorNomes.normalizarNomeLogico(base, prefixo);
    }

    private String defaultIfBlank(String valor, String fallback) {
        return (valor == null || valor.isBlank()) ? fallback : valor;
    }

    public String avaliarQualidadeSelector(String selector) {
        if (selector == null || selector.isBlank()) return "BAIXO";

        if (selector.matches(".*#[a-zA-Z][\\w-]+.*")) return "ALTO";
        if (selector.contains("[data-testid=") || selector.contains("[data-qa=")
                || selector.contains("[data-cy=") || selector.contains("[name=")
                || selector.contains("[aria-label=")) return "ALTO";

        if (selector.contains("http://") || selector.contains("https://")) return "BAIXO";
        if (selector.split("nth-child").length > 3) return "BAIXO";
        if (selector.split(" > div").length > 5) return "BAIXO";
        if (selector.length() > 120) return "BAIXO";

        return "MEDIO";
    }

    public String simplificarHref(String selector, String elementoHtml) {
        if (selector == null || (!selector.contains("href=\"http") && !selector.contains("href='http")))
            return selector;
        try {
            int hStart = selector.indexOf("href=\"") >= 0
                    ? selector.indexOf("href=\"") + 6
                    : selector.indexOf("href='") + 6;
            char quote = selector.charAt(hStart - 1);
            int hEnd = selector.indexOf(quote, hStart);
            if (hStart < 6 || hEnd < 0) return selector;
            String url = selector.substring(hStart, hEnd);

            int idxHref = selector.indexOf("[href=");
            if (idxHref < 0) idxHref = selector.indexOf("[href*=");
            String prefixo = idxHref >= 0 ? selector.substring(0, idxHref) : "";

            String slug = null;
            try {
                java.net.URI uri = new java.net.URI(url);
                String path = uri.getPath();
                if (path != null) {
                    String[] partes = path.split("/");
                    for (int i = partes.length - 1; i >= 0; i--) {
                        String p = partes[i];
                        if (p.length() > 5
                                && !p.equals("produto") && !p.equals("product")
                                && !p.equals("item") && !p.equals("categoria")
                                && !p.equals("demosite")) {
                            slug = p;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) { }

            String acao = null;
            try {
                java.net.URI uri = new java.net.URI(url);
                String query = uri.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("action=")) {
                            acao = param.substring(7);
                            break;
                        }
                        if (param.contains("buy") || param.contains("add_to_cart")
                                || param.contains("checkout")) {
                            acao = param.contains("=") ? param.split("=")[1] : param;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) { }

            String classeEstavel = null;
            if (elementoHtml != null && !elementoHtml.isBlank()) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("class=[\"']([^\"']+)[\"']")
                        .matcher(elementoHtml);
                if (m.find()) {
                    for (String cls : m.group(1).split(" ")) {
                        if (cls.length() > 2
                                && !cls.matches(".*[0-9a-f]{8,}.*")
                                && !cls.matches(".*_[0-9]+.*")) {
                            classeEstavel = cls;
                            break;
                        }
                    }
                }
            }

            String resultado;

            if (classeEstavel != null && slug != null && acao != null) {
                resultado = prefixo + "." + classeEstavel
                        + "[href*=\"" + slug + "\"][href*=\"" + acao + "\"]";
                log("[Qorbit] Href simplificado (estratégia 1): " + resultado);
                return resultado;
            }

            if (slug != null && slug.length() > 5) {
                if (classeEstavel != null) {
                    resultado = prefixo + "." + classeEstavel + "[href*=\"" + slug + "\"]";
                } else {
                    resultado = prefixo + "[href*=\"" + slug + "\"]";
                }
                log("[Qorbit] Href simplificado (estratégia 2): " + resultado);
                return resultado;
            }

            if (classeEstavel != null && acao != null) {
                resultado = prefixo + "." + classeEstavel + "[href*=\"" + acao + "\"]";
                log("[Qorbit] Href simplificado (estratégia 3): " + resultado);
                return resultado;
            }

            if (acao != null && acao.length() > 3) {
                resultado = prefixo + "[href*=\"" + acao + "\"]";
                System.err.println("[Qorbit] AVISO — selector frágil gerado (href genérico): "
                        + resultado + " | Recomenda-se revisão manual em Elementos.");
                return resultado;
            }

            System.err.println("[Qorbit] Não foi possível simplificar href: "
                    + selector.substring(0, Math.min(80, selector.length())));
            return selector;

        } catch (Exception e) {
            return selector;
        }
    }

    public String simplificarHref(String selector) {
        return simplificarHref(selector, null);
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private String extrairNomePagina(String url) {
        try {
            String path = new java.net.URL(url).getPath();
            if (path == null || path.equals("/") || path.isBlank()) return "PaginaInicial";
            String[] partes = path.split("/");
            String ultima = partes[partes.length - 1];
            return ultima.isEmpty() ? "PaginaInicial"
                    : Character.toUpperCase(ultima.charAt(0)) + ultima.substring(1);
        } catch (Exception e) {
            return "PaginaGravada";
        }
    }
}