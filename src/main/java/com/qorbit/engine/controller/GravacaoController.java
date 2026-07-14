package com.qorbit.engine.controller;

import com.qorbit.engine.service.GravacaoService;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import java.io.File;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
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
        String erroUrl = validarUrlSegura(url);
        if (erroUrl != null) {
            return ResponseEntity.badRequest().body(Map.of("erro", erroUrl));
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
            // Stability flags for Linux servers
            opts.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--disable-setuid-sandbox");

            String chromeBin = System.getProperty("scanner.chrome.bin");
            if (chromeBin != null && !chromeBin.isBlank()) {
              opts.setBinary(chromeBin);
            }

            String logPath = System.getProperty("scanner.chromedriver.log", "/tmp/chromedriver.log");
            ChromeDriverService service = new ChromeDriverService.Builder()
                .usingAnyFreePort()
                .withVerbose(true)
                .withLogFile(new File(logPath))
                .build();

            driverGravacao = new ChromeDriver(service, opts);
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

    /** Valida URL para prevenir SSRF: permite apenas http/https para hosts públicos. */
    private String validarUrlSegura(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return "Apenas URLs http:// e https:// são permitidas";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "Host inválido na URL";
            }
            // Bloqueia endpoints de metadados de cloud (AWS/GCP/Azure)
            String hostLower = host.toLowerCase();
            if (hostLower.equals("169.254.169.254") || hostLower.contains("metadata.google.internal")) {
                return "URL bloqueada por política de segurança";
            }
            // Resolve o host e bloqueia endereços privados/loopback
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                return "URL bloqueada por política de segurança";
            }
            return null; // OK
        } catch (Exception e) {
            return "URL inválida";
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
              function norm(v) { return safe(v).replace(/\\s+/g, ' ').trim(); }

              function cssEscape(v) {
                try {
                  if (!v) return '';
                  if (typeof CSS !== 'undefined' && CSS && CSS.escape) return CSS.escape(v);
                  return String(v).replace(/([ #;?%&,.+*~\\':"!^$\\[\\]()=>|\\/])/g, '\\\\$1');
                } catch (e) { return v; }
              }

              function looksDynamic(v) {
                if (!v) return false;
                v = String(v).toLowerCase();
                return /^mui-\\d+$/.test(v)
                    || /^jss\\d+$/.test(v)
                    || /^css-[a-z0-9]+$/i.test(v)
                    || /(^|[-_])mui-\\d+($|[-_])/.test(v);
              }

              function textOf(el) {
                if (!el) return '';
                return norm(el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.name || el.id || '');
              }

              function visibleDatePickerRoot() {
                var roots = Array.from(win.document.querySelectorAll(
                  '.MuiPickersPopper-root, .MuiDateCalendar-root, .MuiPickersLayout-root, ' +
                  '.mat-datepicker-content, .react-datepicker, .ui-datepicker, .flatpickr-calendar, ' +
                  '[class*="datepicker"], [class*="calendar"], [class*="pickers"]'
                ));
                return roots.find(function(r) {
                  try { return r && r.offsetParent !== null; } catch (e) { return false; }
                }) || null;
              }

              function isInsideOpenDatePicker(el) {
                if (!el || !el.closest) return false;
                return !!el.closest(
                  '.MuiPickersPopper-root, .MuiDateCalendar-root, .MuiPickersLayout-root, ' +
                  '.mat-datepicker-content, .react-datepicker, .ui-datepicker, .flatpickr-calendar, ' +
                  '[class*="datepicker"], [class*="calendar"], [class*="pickers"]'
                );
              }

              function isDatePickerElement(el) {
                if (!el || !el.tagName) return false;

                var tag = safe(el.tagName).toLowerCase();
                var type = safe(el.getAttribute('type')).toLowerCase();
                var role = safe(el.getAttribute('role')).toLowerCase();
                var cls = safe(el.className).toLowerCase();
                var aria = safe(el.getAttribute('aria-label')).toLowerCase();
                var txt = norm(el.innerText || el.textContent || '').toLowerCase();

                if (tag === 'input' && (type === 'date' || type === 'datetime-local')) return true;

                if (cls.indexOf('datepicker') >= 0 ||
                    cls.indexOf('calendar') >= 0 ||
                    cls.indexOf('pickers') >= 0 ||
                    cls.indexOf('muipickers') >= 0 ||
                    cls.indexOf('mat-calendar') >= 0) return true;

                if (role === 'gridcell' || role === 'dialog') {
                  if (cls.indexOf('calendar') >= 0 || cls.indexOf('pickers') >= 0) return true;
                }

                if (aria.indexOf('choose date') >= 0 ||
                    aria.indexOf('selected date') >= 0 ||
                    aria.indexOf('calendar') >= 0 ||
                    aria.indexOf('date') >= 0) return true;

                if (txt.match(/^(janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)$/)) return true;

                return !!(el.closest && el.closest(
                  '.MuiPickersPopper-root, .MuiDateCalendar-root, .MuiPickersLayout-root, ' +
                  '.mat-datepicker-content, .react-datepicker, .ui-datepicker, .flatpickr-calendar, ' +
                  '[class*="datepicker"], [class*="calendar"], [class*="pickers"]'
                ));
              }

              function activeDateInput() {
                try {
                  var ae = win.document.activeElement;
                  if (ae && ae.tagName && ae.tagName.toLowerCase() === 'input') return ae;
                } catch (e) {}

                var focused = win.document.querySelector('input:focus, textarea:focus');
                if (focused) return focused;

                var inputs = Array.from(win.document.querySelectorAll('input'));
                return inputs.length ? inputs[inputs.length - 1] : null;
              }

              function dateFieldOf(el) {
                if (!el) return null;
                var campo = null;

                try {
                  campo = el.closest('label,[data-field],[class*="field"],[class*="form-group"]');
                } catch (e) {}

                try {
                  if (!campo && el.closest) {
                    var dialog = el.closest(
                      '.MuiPickersPopper-root, .MuiDateCalendar-root, .MuiPickersLayout-root, ' +
                      '.mat-datepicker-content, .react-datepicker, .ui-datepicker, .flatpickr-calendar, ' +
                      '[class*="datepicker"], [class*="calendar"], [class*="pickers"]'
                    );
                    if (dialog) {
                      var active = activeDateInput();
                      if (active) campo = active;
                    }
                  }
                } catch (e) {}

                if (campo && campo.tagName) {
                  var tag = campo.tagName.toLowerCase();
                  if (tag === 'input' || tag === 'textarea' || tag === 'select') return campo;
                  try {
                    var innerInput = campo.querySelector('input, textarea, select');
                    if (innerInput) return innerInput;
                  } catch (e) {}
                }

                return campo;
              }

              function componentType(el) {
                if (!el) return 'INTERACTIVE';
                var role = safe(el.getAttribute('role')).toLowerCase();
                var tag = safe(el.tagName).toLowerCase();
                var cls = safe(el.className).toLowerCase();
                var type = safe(el.getAttribute('type')).toLowerCase();

                if (isDatePickerElement(el)) return 'DATEPICKER';
                if (tag === 'select') return 'SELECT';
                if (role === 'tab') return 'TAB';
                if (role.indexOf('menu') >= 0) return 'MENU';
                if (role === 'combobox' || cls.indexOf('autocomplete') >= 0) return 'AUTOCOMPLETE';
                if (cls.indexOf('select') >= 0 && tag !== 'select') return 'CUSTOM_SELECT';
                if (cls.indexOf('card') >= 0) return 'CARD';
                if (type === 'radio') return 'RADIO';
                if (type === 'checkbox') return 'CHECKBOX';
                return 'INTERACTIVE';
              }

              function betterSelector(el) {
                if (!el || !el.tagName) return { seletor:'', tipoSeletor:'CSS' };

                var tag = el.tagName.toLowerCase();
                var type = safe(el.getAttribute('type')).toLowerCase();

                var dt = el.getAttribute('data-testid') || el.getAttribute('data-test') || el.getAttribute('data-qa');
                if (dt) {
                  return {
                    seletor: '[' + (el.getAttribute('data-testid') ? 'data-testid' : (el.getAttribute('data-test') ? 'data-test' : 'data-qa')) + '="' + cssEscape(dt) + '"]',
                    tipoSeletor:'CSS'
                  };
                }

                if (el.id && !looksDynamic(el.id)) {
                  return {
                    seletor:'#' + cssEscape(el.id),
                    tipoSeletor:'CSS'
                  };
                }

                var value = el.getAttribute('value');
                if ((type === 'radio' || type === 'checkbox') && safe(value)) {
                  return {
                    seletor: tag + '[type="' + cssEscape(type) + '"][value="' + cssEscape(value) + '"]',
                    tipoSeletor:'CSS'
                  };
                }

                if (el.name && !looksDynamic(el.name)) {
                  return {
                    seletor: tag + '[name="' + cssEscape(el.name) + '"]',
                    tipoSeletor:'CSS'
                  };
                }

                if (el.getAttribute('aria-label')) {
                  return {
                    seletor: tag + '[aria-label="' + cssEscape(el.getAttribute('aria-label')) + '"]',
                    tipoSeletor:'CSS'
                  };
                }

                if (el.getAttribute('placeholder')) {
                  return {
                    seletor: tag + '[placeholder="' + cssEscape(el.getAttribute('placeholder')) + '"]',
                    tipoSeletor:'CSS'
                  };
                }

                var href = el.getAttribute('href');
                if (tag === 'a' && href) {
                  return {
                    seletor:'a[href="' + cssEscape(href) + '"]',
                    tipoSeletor:'CSS'
                  };
                }

                var cls = safe(el.className)
                  .split(/\\s+/)
                  .filter(function(c){
                    return c
                      && !/^ng-/.test(c)
                      && !/^css-/.test(c)
                      && !/^jsx-/.test(c)
                      && !/^jss\\d+$/.test(c)
                      && !/^mui-\\d+$/.test(c);
                  })
                  .slice(0,2);

                if (cls.length) {
                  return {
                    seletor: tag + '.' + cls.map(cssEscape).join('.'),
                    tipoSeletor:'CSS'
                  };
                }

                return { seletor: tag, tipoSeletor:'CSS' };
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

              function emitDatePickerValue() {
                try {
                  var campo = activeDateInput();
                  if (!campo) return;

                  var val = safe(campo.value);
                  if (!val) return;

                  var sel = betterSelector(campo);
                  push(Object.assign({}, sel, {
                    tipo: 'INPUT',
                    tagName: safe(campo.tagName).toLowerCase(),
                    valor: val,
                    textoElemento: textOf(campo),
                    label: labelOf(campo),
                    placeholder: safe(campo.getAttribute('placeholder')),
                    ariaLabel: safe(campo.getAttribute('aria-label')),
                    role: safe(campo.getAttribute('role')),
                    componentType: 'DATEPICKER',
                    shadowPath: shadowPathOf(campo)
                  }));
                } catch (e) {}
              }

              function realClickable(start) {
                if (!start) return null;
                var path = start.composedPath ? start.composedPath() : null;
                if (path && path.length) {
                  for (var i = 0; i < path.length; i++) {
                    var p = path[i];
                    if (!p || !p.tagName) continue;
                    if (p.matches && p.matches('a,button,[role="button"],[role="tab"],[role="menuitem"],input[type="submit"],input[type="button"],input[type="radio"],input[type="checkbox"],summary')) return p;
                    if (p.onclick || safe(p.style && p.style.cursor) === 'pointer') return p;
                    var cls = safe(p.className).toLowerCase();
                    if (cls.indexOf('btn') >= 0 || cls.indexOf('button') >= 0 || cls.indexOf('tab') >= 0 || cls.indexOf('menu') >= 0 || cls.indexOf('card') >= 0) return p;
                  }
                }
                if (start.closest) {
                  return start.closest('a,button,[role="button"],[role="tab"],[role="menuitem"],input[type="submit"],input[type="button"],input[type="radio"],input[type="checkbox"],summary,[class*="btn"],[class*="button"],[class*="tab"],[class*="menu"],[class*="card"],div[onclick],span[onclick]');
                }
                return start;
              }

              win.document.addEventListener('click', function(e) {
                var alvo = realClickable(e.target);
                if (!alvo) return;

                var calendarioAberto = visibleDatePickerRoot();

                if (calendarioAberto && isInsideOpenDatePicker(alvo)) {
                  setTimeout(function() {
                    emitDatePickerValue();
                  }, 250);
                  return;
                }

                if (isDatePickerElement(alvo)) {
                  setTimeout(function() {
                    emitDatePickerValue();
                  }, 250);
                  return;
                }

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
                var type = safe(el.getAttribute('type')).toLowerCase();

                if (type === 'radio' || type === 'checkbox') {
                  var selCheck = betterSelector(el);
                  push(Object.assign({}, selCheck, {
                    tipo: 'CLICK',
                    tagName: tag.toLowerCase(),
                    valor: el.value,
                    checked: !!el.checked,
                    textoElemento: textOf(el),
                    label: labelOf(el),
                    placeholder: safe(el.getAttribute('placeholder')),
                    ariaLabel: safe(el.getAttribute('aria-label')),
                    role: safe(el.getAttribute('role')),
                    componentType: componentType(el),
                    shadowPath: shadowPathOf(el)
                  }));
                  return;
                }

                if (componentType(el) === 'DATEPICKER' || isDatePickerElement(el)) {
                  var campoData = dateFieldOf(el) || el;
                  var selData = betterSelector(campoData);
                  var valData = campoData.value || el.value || textOf(campoData) || textOf(el);

                  if (safe(valData)) {
                    push(Object.assign({}, selData, {
                      tipo: 'INPUT',
                      tagName: safe(campoData.tagName).toLowerCase(),
                      valor: valData,
                      textoElemento: textOf(campoData),
                      label: labelOf(campoData),
                      placeholder: safe(campoData.getAttribute && campoData.getAttribute('placeholder')),
                      ariaLabel: safe(campoData.getAttribute && campoData.getAttribute('aria-label')),
                      role: safe(campoData.getAttribute && campoData.getAttribute('role')),
                      componentType: 'DATEPICKER',
                      shadowPath: shadowPathOf(campoData)
                    }));
                  }
                  return;
                }

                if (tag === 'SELECT' || componentType(el) === 'CUSTOM_SELECT' || componentType(el) === 'AUTOCOMPLETE') {
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