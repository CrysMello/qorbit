package com.qorbit.engine.controller;

import com.qorbit.engine.service.GravacaoService;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/gravacao")
public class GravacaoController {

    @Autowired private GravacaoService gravacaoService;

    private WebDriver driverGravacao;
    private final AtomicBoolean coletando = new AtomicBoolean(false);

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciar(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "URL é obrigatória"));
        }
        if (gravacaoService.isGravando()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Já existe uma gravação em andamento. Pare primeiro."));
        }

        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions opts = new ChromeOptions();
            opts.addArguments("--disable-blink-features=AutomationControlled");
            opts.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            opts.setExperimentalOption("useAutomationExtension", false);

            driverGravacao = new ChromeDriver(opts);
            driverGravacao.manage().window().maximize();
            driverGravacao.get(url);

            gravacaoService.iniciarGravacao(url);
            gravacaoService.registrarStep(Map.of("tipo", "NAVIGATE", "valor", url, "url", url));

            injetarScript();
            coletando.set(true);
            iniciarLoopColetaAsync();

            return ResponseEntity.ok(Map.of(
                    "mensagem", "Gravação iniciada — interaja com o Chrome que foi aberto",
                    "url", url
            ));
        } catch (Exception e) {
            pararBrowser();
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao abrir browser: " + e.getMessage()));
        }
    }

    @PostMapping("/parar")
    public ResponseEntity<?> parar(@RequestBody Map<String, String> body) {
        coletando.set(false);
        if (driverGravacao != null) {
            try { coletarEventosPendentes(); } catch (Exception ignored) {}
        }
        pararBrowser();

        String nomeCaso = body.getOrDefault("nomeCaso", "");
        String modulo = body.getOrDefault("modulo", "");
        Map<String, Object> resultado = gravacaoService.pararGravacao(nomeCaso, modulo);
        if (resultado.containsKey("erro")) {
            return ResponseEntity.badRequest().body(resultado);
        }
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/step")
    public ResponseEntity<?> receberStep(@RequestBody Map<String, Object> evento) {
        return ResponseEntity.ok(gravacaoService.registrarStep(evento));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(gravacaoService.getStatus());
    }

    @DeleteMapping("/step/{numero}")
    public ResponseEntity<Void> removerStep(@PathVariable int numero) {
        gravacaoService.removerStep(numero);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/descartar")
    public ResponseEntity<?> descartar() {
        coletando.set(false);
        pararBrowser();
        gravacaoService.pararGravacao("_descartar_", "_descartar_");
        return ResponseEntity.ok(Map.of("mensagem", "Gravação descartada"));
    }

    private void iniciarLoopColetaAsync() {
        Thread t = new Thread(this::iniciarLoopColeta, "scanner-gravacao-loop");
        t.setDaemon(true);
        t.start();
    }

    public void iniciarLoopColeta() {
        while (coletando.get() && driverGravacao != null) {
            try {
                injetarScript();
                coletarEventosPendentes();
                Thread.sleep(220);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (org.openqa.selenium.WebDriverException e) {
                if (!coletando.get()) break;
                try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            } catch (Exception e) {
                if (!coletando.get()) break;
                try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void coletarEventosPendentes() {
        if (driverGravacao == null) return;
        try {
            Object resultado = ((JavascriptExecutor) driverGravacao)
                    .executeScript("return window.__scannerDrainEventos ? window.__scannerDrainEventos() : [];\n");
            if (resultado instanceof List<?> eventos && !eventos.isEmpty()) {
                for (Object ev : eventos) {
                    if (ev instanceof Map<?, ?> mapa) {
                        gravacaoService.registrarStep((Map<String, Object>) mapa);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void injetarScript() {
        if (driverGravacao == null) return;
        try {
            ((JavascriptExecutor) driverGravacao).executeScript(gerarScript());
        } catch (Exception ignored) {
        }
    }

    private void pararBrowser() {
        if (driverGravacao != null) {
            try { driverGravacao.quit(); } catch (Exception ignored) {}
            driverGravacao = null;
        }
    }

    private String gerarScript() {
        return """
        (function() {
          function boot(win, framePath) {
            try {
              if (!win || !win.document) return;
              if (win.__scannerBooted) return;
              win.__scannerBooted = true;
              if (!window.__scannerEventos) window.__scannerEventos = [];
              window.__scannerDrainEventos = window.__scannerDrainEventos || function() {
                var evs = window.__scannerEventos.splice(0, window.__scannerEventos.length);
                return evs;
              };

              function safe(v) { return (v || '').toString().trim(); }
              function norm(v) { return safe(v).replace(/\s+/g, ' ').trim(); }
              function cssEscape(v) {
                try {
                  if (!v) return '';
                  if (typeof CSS !== 'undefined' && CSS && CSS.escape) return CSS.escape(v);
                  return String(v);
                } catch (e) { return v; }
              }
              function textOf(el) {
                if (!el) return '';
                return norm(el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.name || el.id || '');
              }
              function componentType(el) {
                if (!el) return 'INTERACTIVE';
                var role = safe(el.getAttribute('role')).toLowerCase();
                var tag = safe(el.tagName).toLowerCase();
                var cls = safe(el.className).toLowerCase();
                if (tag === 'select') return 'SELECT';
                if (el.matches && el.matches('input[type="date"],input[type="datetime-local"]')) return 'DATEPICKER';
                if (role === 'tab') return 'TAB';
                if (role.indexOf('menu') >= 0) return 'MENU';
                if (role === 'combobox' || cls.indexOf('autocomplete') >= 0) return 'AUTOCOMPLETE';
                if (cls.indexOf('datepicker') >= 0 || cls.indexOf('calendar') >= 0) return 'DATEPICKER';
                if (cls.indexOf('select') >= 0 && tag !== 'select') return 'CUSTOM_SELECT';
                if (cls.indexOf('card') >= 0) return 'CARD';
                return 'INTERACTIVE';
              }
              function betterSelector(el) {
                if (!el || !el.tagName) return { seletor:'', tipoSeletor:'CSS' };
                if (el.id) return { seletor:'#' + cssEscape(el.id), tipoSeletor:'CSS' };
                var dt = el.getAttribute('data-testid') || el.getAttribute('data-test') || el.getAttribute('data-qa');
                if (dt) return { seletor:'[' + (el.getAttribute('data-testid') ? 'data-testid' : (el.getAttribute('data-test') ? 'data-test' : 'data-qa')) + '="' + cssEscape(dt) + '"]', tipoSeletor:'CSS' };
                if (el.name) return { seletor: el.tagName.toLowerCase() + '[name="' + cssEscape(el.name) + '"]', tipoSeletor:'CSS' };
                if (el.getAttribute('aria-label')) return { seletor: el.tagName.toLowerCase() + '[aria-label="' + cssEscape(el.getAttribute('aria-label')) + '"]', tipoSeletor:'CSS' };
                if (el.getAttribute('placeholder')) return { seletor: el.tagName.toLowerCase() + '[placeholder="' + cssEscape(el.getAttribute('placeholder')) + '"]', tipoSeletor:'CSS' };
                var href = el.getAttribute('href');
                if (el.tagName.toLowerCase() === 'a' && href) return { seletor:'a[href="' + cssEscape(href) + '"]', tipoSeletor:'CSS' };
                var cls = safe(el.className).split(/\s+/).filter(function(c){ return c && !/^ng-/.test(c) && !/^css-/.test(c) && !/^jsx-/.test(c); }).slice(0,2);
                if (cls.length) return { seletor: el.tagName.toLowerCase() + '.' + cls.map(cssEscape).join('.'), tipoSeletor:'CSS' };
                return { seletor: el.tagName.toLowerCase(), tipoSeletor:'CSS' };
              }
              function labelOf(el) {
                if (!el) return '';
                var id = el.id;
                if (id) {
                  try {
                    var explicit = win.document.querySelector('label[for="' + cssEscape(id) + '"]');
                    if (explicit) return norm(explicit.innerText || explicit.textContent || '');
                  } catch (e) {}
                }
                var wrapper = el.closest ? el.closest('label,[data-field],[data-testid],[class*="field"],[class*="form-group"]') : null;
                if (wrapper) {
                  var txt = norm(wrapper.innerText || wrapper.textContent || '');
                  if (txt) return txt.substring(0, 120);
                }
                return '';
              }
              function shadowPathOf(target) {
                try {
                  var root = target.getRootNode && target.getRootNode();
                  if (!root || !root.host) return '';
                  var host = root.host;
                  var hostSel = betterSelector(host).seletor;
                  return 'shadow-host(' + hostSel + ') > ' + betterSelector(target).seletor;
                } catch (e) { return ''; }
              }
              function push(ev) {
                if (!ev || !ev.tipo) return;
                ev.url = win.location.href;
                ev.framePath = framePath || 'root';
                window.__scannerEventos.push(ev);
              }
              function realClickable(start) {
                if (!start) return null;
                var path = start.composedPath ? start.composedPath() : null;
                if (path && path.length) {
                  for (var i = 0; i < path.length; i++) {
                    var p = path[i];
                    if (!p || !p.tagName) continue;
                    if (p.matches && p.matches('a,button,[role="button"],[role="tab"],[role="menuitem"],input[type="submit"],input[type="button"],summary')) return p;
                    if (p.onclick || safe(p.style && p.style.cursor) === 'pointer') return p;
                    var cls = safe(p.className).toLowerCase();
                    if (cls.indexOf('btn') >= 0 || cls.indexOf('button') >= 0 || cls.indexOf('tab') >= 0 || cls.indexOf('menu') >= 0 || cls.indexOf('card') >= 0) return p;
                  }
                }
                if (start.closest) {
                  return start.closest('a,button,[role="button"],[role="tab"],[role="menuitem"],input[type="submit"],input[type="button"],summary,[class*="btn"],[class*="button"],[class*="tab"],[class*="menu"],[class*="card"],div[onclick],span[onclick]');
                }
                return start;
              }

              win.document.addEventListener('click', function(e) {
                var alvo = realClickable(e.target);
                if (!alvo) return;
                var sel = betterSelector(alvo);
                push(Object.assign({}, sel, {
                  tipo: 'CLICK',
                  tagName: safe(alvo.tagName).toLowerCase(),
                  textoElemento: textOf(alvo),
                  label: labelOf(alvo),
                  placeholder: safe(alvo.getAttribute && alvo.getAttribute('placeholder')),
                  ariaLabel: safe(alvo.getAttribute && alvo.getAttribute('aria-label')),
                  role: safe(alvo.getAttribute && alvo.getAttribute('role')),
                  href: safe(alvo.getAttribute && alvo.getAttribute('href')),
                  componentType: componentType(alvo),
                  shadowPath: shadowPathOf(alvo)
                }));
              }, true);

              var debounce = {};
              win.document.addEventListener('input', function(e) {
                var el = e.target;
                if (!el || !el.tagName) return;
                var tag = el.tagName.toUpperCase();
                if (tag !== 'INPUT' && tag !== 'TEXTAREA') return;
                if ((el.type || '').toLowerCase() === 'password') return;
                var key = (framePath || 'root') + '|' + (el.id || el.name || betterSelector(el).seletor || tag);
                clearTimeout(debounce[key]);
                debounce[key] = setTimeout(function() {
                  if (!safe(el.value)) return;
                  var sel = betterSelector(el);
                  push(Object.assign({}, sel, {
                    tipo: 'INPUT',
                    tagName: tag.toLowerCase(),
                    valor: el.value,
                    textoElemento: textOf(el),
                    label: labelOf(el),
                    placeholder: safe(el.getAttribute('placeholder')),
                    ariaLabel: safe(el.getAttribute('aria-label')),
                    role: safe(el.getAttribute('role')),
                    componentType: componentType(el),
                    shadowPath: shadowPathOf(el)
                  }));
                }, 600);
              }, true);

              win.document.addEventListener('change', function(e) {
                var el = e.target;
                if (!el || !el.tagName) return;
                var tag = el.tagName.toUpperCase();
                if (tag === 'SELECT' || componentType(el) === 'CUSTOM_SELECT' || componentType(el) === 'AUTOCOMPLETE' || componentType(el) === 'DATEPICKER') {
                  var val = el.value;
                  if (tag === 'SELECT' && el.options && el.selectedIndex >= 0) {
                    val = el.options[el.selectedIndex].text || el.value;
                  }
                  var sel = betterSelector(el);
                  push(Object.assign({}, sel, {
                    tipo: 'SELECT',
                    tagName: tag.toLowerCase(),
                    valor: val,
                    textoElemento: textOf(el),
                    label: labelOf(el),
                    placeholder: safe(el.getAttribute('placeholder')),
                    ariaLabel: safe(el.getAttribute('aria-label')),
                    role: safe(el.getAttribute('role')),
                    componentType: componentType(el),
                    shadowPath: shadowPathOf(el)
                  }));
                }
              }, true);

              win.addEventListener('beforeunload', function() {
                push({ tipo:'NAVIGATE', valor: win.location.href, url: win.location.href, seletor:'' });
              }, true);

              try {
                var observer = new MutationObserver(function() {
                  try {
                    var frames = win.document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                      try { boot(frames[i].contentWindow, (framePath || 'root') + ' > iframe:nth-of-type(' + (i + 1) + ')'); } catch (e) {}
                    }
                  } catch (e) {}
                });
                observer.observe(win.document.documentElement || win.document.body, { childList:true, subtree:true });
              } catch (e) {}

              try {
                var frames = win.document.querySelectorAll('iframe');
                for (var i = 0; i < frames.length; i++) {
                  try { boot(frames[i].contentWindow, (framePath || 'root') + ' > iframe:nth-of-type(' + (i + 1) + ')'); } catch (e) {}
                }
              } catch (e) {}
            } catch (err) {
              console.warn('[Scanner] erro ao iniciar captura', err);
            }
          }
          boot(window, 'root');
        })();
        """;
    }
}
