package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.exception.DatePickerCalendarNotOpenedException;
import com.qorbit.engine.exception.DatePickerDateDisabledException;
import com.qorbit.engine.exception.DatePickerDateNotFoundException;
import com.qorbit.engine.exception.SelectElementNotFoundException;
import com.qorbit.engine.exception.SelectOptionNotFoundException;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ========== CALENDÁRIO: DateInputExecutionStrategy ==========
@Component
public class DateInputExecutionStrategy implements ExecutionStrategy {

    private static final String CALENDAR_SELECTOR =
            // jQuery UI
            ".ui-datepicker, " +
            // Flatpickr
            ".flatpickr-calendar.open, " +
            // React Datepicker
            ".react-datepicker, .react-datepicker-popper, " +
            // Angular Material
            ".mat-datepicker-content, .mat-calendar, " +
            // MUI v5 (emotion/styled)
            ".MuiPickersPopper-root, .MuiPickersLayout-root, .MuiDateCalendar-root, " +
            // MUI v4 (legacy)
            ".MuiPickersModal-root, .MuiPickersBasePicker-container, .MuiPickersCalendar-root, " +
            // Ant Design
            ".ant-picker-dropdown:not(.ant-picker-dropdown-hidden), " +
            // Pikaday
            ".pika-single, " +
            // Bootstrap Datepicker
            ".datepicker.datepicker-dropdown, .bootstrap-datepicker, " +
            // Date Range Picker
            ".daterangepicker.show-calendar, " +
            // Generic ARIA grid dialog (fallback for custom calendars)
            "[role='dialog'] [role='grid'], [role='dialog'][aria-modal='true']";

    /**
     * Mapa bilíngue (inglês + português) de nomes de meses → número (1-12).
     * Chaves em minúsculas e sem acentos para comparação normalizada.
     * Suporta nomes completos e abreviações.
     */
    private static final Map<String, Integer> MONTH_MAP = new LinkedHashMap<>();
    static {
        // Inglês — nomes completos
        MONTH_MAP.put("january",   1); MONTH_MAP.put("february",  2); MONTH_MAP.put("march",     3);
        MONTH_MAP.put("april",     4); MONTH_MAP.put("may",       5); MONTH_MAP.put("june",      6);
        MONTH_MAP.put("july",      7); MONTH_MAP.put("august",    8); MONTH_MAP.put("september", 9);
        MONTH_MAP.put("october",  10); MONTH_MAP.put("november", 11); MONTH_MAP.put("december", 12);
        // Inglês — abreviações (3 letras)
        MONTH_MAP.put("jan",  1); MONTH_MAP.put("feb",  2); MONTH_MAP.put("mar",  3);
        MONTH_MAP.put("apr",  4); MONTH_MAP.put("jun",  6); MONTH_MAP.put("jul",  7);
        MONTH_MAP.put("aug",  8); MONTH_MAP.put("sep",  9); MONTH_MAP.put("oct", 10);
        MONTH_MAP.put("nov", 11); MONTH_MAP.put("dec", 12);
        // Português — nomes completos (sem acento, pré-normalizados)
        MONTH_MAP.put("janeiro",   1); MONTH_MAP.put("fevereiro",  2); MONTH_MAP.put("marco",     3);
        MONTH_MAP.put("abril",     4); MONTH_MAP.put("maio",       5); MONTH_MAP.put("junho",     6);
        MONTH_MAP.put("julho",     7); MONTH_MAP.put("agosto",     8); MONTH_MAP.put("setembro",  9);
        MONTH_MAP.put("outubro",  10); MONTH_MAP.put("novembro",  11); MONTH_MAP.put("dezembro", 12);
        // Português — abreviações
        MONTH_MAP.put("fev",  2); MONTH_MAP.put("abr",  4); MONTH_MAP.put("mai",  5);
        MONTH_MAP.put("ago",  8); MONTH_MAP.put("set",  9); MONTH_MAP.put("out", 10);
        MONTH_MAP.put("dez", 12);
    }

    /** Formatos de data aceitos como entrada (ordem: mais específico → mais genérico). */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),  // 13/07/1950  (BR padrão)
        DateTimeFormatter.ofPattern("d/M/yyyy"),    // 3/7/1950
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),  // 07/13/1950  (US padrão)
        DateTimeFormatter.ofPattern("M/d/yyyy"),    // 7/3/1950
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),  // 1950-07-13  (ISO)
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),  // 13-07-1950
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),  // 07-13-1950  (US com traço)
        DateTimeFormatter.ofPattern("M-d-yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),  // 13.07.1950
        DateTimeFormatter.ofPattern("MM.dd.yyyy")   // 07.13.1950
    );

    /** Extrai um ano de 4 dígitos do texto do header do calendário. */
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(\\d{4})\\b");

    @Override
    public StrategyType type() {
        return StrategyType.DATE_INPUT;
    }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            throw new IllegalArgumentException("Elemento não encontrado para preenchimento de data");
        }

        String rawValue = step.getValor() != null ? step.getValor().trim() : "";
        if (rawValue.isBlank()) {
            throw new IllegalArgumentException("Valor da data não informado no step");
        }

        LocalDate targetDate = parseTargetDate(rawValue);

        // Tentativa rápida via API nativa do datepicker (sem abrir o calendário).
        // Evita navegação mês a mês para datas muito distantes (ex.: 70 anos no passado).
        if (targetDate != null && tentarSetarViaApiNativa(driver, element, targetDate)) {
            validarValor(element, rawValue);
            return;
        }

        abrirCalendarioSePossivel(driver, element);

        boolean calendarioUsado = false;
        if (calendarioVisivel(driver)) {
            if (targetDate != null) {
                selecionarDataNoCalendario(driver, targetDate, rawValue);
                calendarioUsado = true;
            }
        }

        // Quando o calendário foi usado, ele já preencheu o campo no formato correto.
        // Chamar aplicarValorNoCampo depois sobrescreve esse valor com o rawValue (formato
        // diferente), o que pode fazer o jQuery UI resetar para a data de hoje.
        if (!calendarioUsado) {
            aplicarValorNoCampo(driver, element, rawValue);
        }
        validarValor(element, rawValue);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSING DA DATA ALVO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converte a string de entrada para {@link LocalDate} tentando múltiplos formatos.
     * Retorna {@code null} se nenhum formato reconhecer o valor — neste caso
     * o fluxo pula a navegação no calendário e aplica o valor diretamente no campo.
     */
    private LocalDate parseTargetDate(String rawValue) {
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(rawValue.trim(), fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ABERTURA E DETECÇÃO DO CALENDÁRIO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tenta setar a data diretamente via API interna do datepicker, sem abrir o calendário.
     * Suporta: jQuery UI ($.fn.datepicker), Flatpickr (_flatpickr.setDate) e input[type=date].
     * Retorna true se a data foi aplicada com sucesso — nesse caso o fluxo de calendário é pulado.
     * Essencial para datas muito distantes (ex.: 70 anos no passado) onde navegar mês a mês
     * seria impraticável (912 cliques para julho de 1950).
     */
    private boolean tentarSetarViaApiNativa(WebDriver driver, WebElement element, LocalDate date) {
        if (!(driver instanceof JavascriptExecutor js)) return false;
        try {
            Boolean ok = (Boolean) js.executeScript("""
                    const el = arguments[0];
                    const year = arguments[1], month0 = arguments[2], day = arguments[3];
                    const d = new Date(year, month0, day);

                    // jQuery UI — $.fn.datepicker('setDate')
                    if (window.$ && typeof $(el).datepicker === 'function') {
                        try {
                            $(el).datepicker('setDate', d);
                            return true;
                        } catch(e) {}
                    }

                    // Flatpickr — _flatpickr.setDate()
                    if (el._flatpickr) {
                        try {
                            el._flatpickr.setDate(d, true);
                            return true;
                        } catch(e) {}
                    }

                    // HTML5 input[type=date] — valor ISO direto
                    if (el.type === 'date' || el.type === 'datetime-local') {
                        const iso = year + '-'
                            + String(month0 + 1).padStart(2, '0') + '-'
                            + String(day).padStart(2, '0');
                        el.value = iso;
                        ['input','change','blur'].forEach(ev =>
                            el.dispatchEvent(new Event(ev, {bubbles:true})));
                        return true;
                    }

                    return false;
                    """,
                    element,
                    date.getYear(),
                    date.getMonthValue() - 1, // 0-indexed para JS Date
                    date.getDayOfMonth());
            if (Boolean.TRUE.equals(ok)) {
                pausar(400);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void abrirCalendarioSePossivel(WebDriver driver, WebElement element) {
        scrollCentralizado(driver, element);
        try {
            element.click();
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception ignored) {
                // Campo ainda pode aceitar digitação direta; o fluxo continua
            }
        }
    }

    private boolean calendarioVisivel(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2))
                .until(d -> !d.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORQUESTRADOR PRINCIPAL: ANO → MÊS → DIA
    // ─────────────────────────────────────────────────────────────────────────

    private void selecionarDataNoCalendario(WebDriver driver, LocalDate targetDate, String rawValue) {
        YearMonth targetYearMonth = YearMonth.from(targetDate);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.visibilityOfAllElementsLocatedBy(
                    By.cssSelector(CALENDAR_SELECTOR)));

            // 1. Navega até o ano e mês corretos
            navegarAteMesAno(driver, targetYearMonth, rawValue);

            // 2. Clica no dia — somente após o contexto correto ser confirmado
            clicarNoDia(driver, targetDate.getDayOfMonth(), targetYearMonth);

            aguardarFechamentoCalendario(driver);

        } catch (DatePickerDateNotFoundException | DatePickerCalendarNotOpenedException e) {
            throw e;
        } catch (Exception e) {
            if (!driver.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty()) {
                throw new DatePickerDateNotFoundException(
                    "Erro ao selecionar data no calendário: " + rawValue + " | " + e.getMessage());
            }
            throw new DatePickerCalendarNotOpenedException("O calendário não abriu: " + rawValue);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NAVEGAÇÃO: ANO → MÊS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Navega clicando próximo/anterior até o calendário exibir o {@link YearMonth} alvo.
     * A direção é determinada por {@link YearMonth#compareTo} — sem contagem fixa de cliques.
     * Após cada clique de navegação, aguarda o título do calendário mudar de fato antes
     * de ler o estado novamente — evita leitura prematura com valor desatualizado do DOM.
     */
    private void navegarAteMesAno(WebDriver driver, YearMonth targetYearMonth, String rawValue) {
        // Tentativa rápida: navegar via <select> de mês/ano (jQuery UI changeYear/changeMonth
        // ou React Datepicker com showMonthDropdown/showYearDropdown).
        // Salta diretamente para o mês/ano alvo sem clicar prev/next repetidamente.
        if (navegarViaSelectsCalendario(driver, targetYearMonth)) return;

        final int SAFETY_LIMIT = 120;

        for (int attempt = 0; attempt < SAFETY_LIMIT; attempt++) {
            // Re-busca o estado a cada iteração para evitar StaleElementReferenceException
            YearMonth currentYearMonth = obterMesAnoAtualDoCalendario(driver);

            if (currentYearMonth == null) {
                throw new DatePickerCalendarNotOpenedException(
                    "Não foi possível ler mês/ano do calendário ao navegar para: " + rawValue);
            }

            int comparison = currentYearMonth.compareTo(targetYearMonth);
            if (comparison == 0) {
                return; // Ano e mês corretos — pronto para selecionar o dia
            }

            // Captura o título atual ANTES do clique para detectar mudança real no DOM
            String tituloAntes = lerTextoTituloCalendario(driver);

            // compareTo < 0 → atual está antes do alvo → avança
            // compareTo > 0 → atual está depois do alvo → retrocede
            if (comparison < 0) {
                clicarProximo(driver);
            } else {
                clicarAnterior(driver);
            }

            // Aguarda o título mudar — confirma que o DOM atualizou antes da próxima leitura
            aguardarMudancaTituloCalendario(driver, tituloAntes);
        }

        throw new DatePickerDateNotFoundException(
            "Não foi possível navegar até " + targetYearMonth + " dentro do limite de segurança");
    }

    /**
     * Navega diretamente via elementos <select> de mês e ano — disponíveis quando o datepicker
     * expõe dropdowns (jQuery UI changeYear/changeMonth=true, React Datepicker showMonthDropdown
     * showYearDropdown). Evita navegar mês a mês via prev/next.
     *
     * Pares de seletores suportados:
     *   jQuery UI:       .ui-datepicker-year  /  .ui-datepicker-month
     *   React Datepicker: .react-datepicker__year-select / .react-datepicker__month-select
     */
    private boolean navegarViaSelectsCalendario(WebDriver driver, YearMonth target) {
        String[][] pares = {
            { ".ui-datepicker-year",               ".ui-datepicker-month"              },
            { ".react-datepicker__year-select",    ".react-datepicker__month-select"   }
        };
        for (String[] par : pares) {
            if (tentarSelecionarAnoMes(driver, target, par[0], par[1])) return true;
        }
        return false;
    }

    private boolean tentarSelecionarAnoMes(WebDriver driver, YearMonth target,
                                            String yearCss, String monthCss) {
        try {
            WebElement yearEl  = driver.findElement(By.cssSelector(yearCss));
            WebElement monthEl = driver.findElement(By.cssSelector(monthCss));
            if (!"select".equalsIgnoreCase(yearEl.getTagName())
                    || !"select".equalsIgnoreCase(monthEl.getTagName())) return false;

            // Seleciona o ano — tenta por value numérico, depois por texto visível
            Select yearSel = new Select(yearEl);
            try { yearSel.selectByValue(String.valueOf(target.getYear())); }
            catch (Exception e) { yearSel.selectByVisibleText(String.valueOf(target.getYear())); }
            pausar(300);

            // Re-busca o select de mês (o DOM pode ter sido atualizado após mudança de ano)
            monthEl = driver.findElement(By.cssSelector(monthCss));
            Select monthSel = new Select(monthEl);

            // Tenta selecionar o mês por value 0-indexed (padrão jQuery UI e React Datepicker)
            boolean mesOk = false;
            try { monthSel.selectByValue(String.valueOf(target.getMonthValue() - 1)); mesOk = true; }
            catch (Exception ignored) {}

            // Fallback: percorre as options e compara texto com MONTH_MAP
            if (!mesOk) {
                for (WebElement opt : monthSel.getOptions()) {
                    if (resolverNomeMes(opt.getText()) == target.getMonthValue()) {
                        opt.click();
                        mesOk = true;
                        break;
                    }
                }
            }

            if (!mesOk) return false;
            pausar(300);

            // Confirma que chegamos ao mês/ano correto
            YearMonth atual = obterMesAnoAtualDoCalendario(driver);
            return atual != null && atual.equals(target);
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Lê o texto do título/caption do calendário para detectar mudança após clique de navegação.
     * Tenta seletores específicos por biblioteca em ordem de especificidade.
     */
    private String lerTextoTituloCalendario(WebDriver driver) {
        // jQuery UI — "December 2025" num único span
        try {
            return driver.findElement(By.cssSelector(".ui-datepicker-title")).getText().trim();
        } catch (Exception ignored) {}
        // jQuery UI — spans separados de mês e ano
        try {
            String mes = driver.findElement(By.cssSelector(".ui-datepicker-month")).getText().trim();
            String ano = driver.findElement(By.cssSelector(".ui-datepicker-year")).getText().trim();
            if (!mes.isBlank() && !ano.isBlank()) return mes + " " + ano;
        } catch (Exception ignored) {}
        // React Datepicker
        try {
            return driver.findElement(By.cssSelector(".react-datepicker__current-month")).getText().trim();
        } catch (Exception ignored) {}
        // Flatpickr
        try {
            return driver.findElement(By.cssSelector(".flatpickr-current-month")).getText().trim();
        } catch (Exception ignored) {}
        // Angular Material
        try {
            return driver.findElement(By.cssSelector(".mat-calendar-period-button")).getText().trim();
        } catch (Exception ignored) {}
        // MUI v5
        try {
            return driver.findElement(By.cssSelector(".MuiPickersCalendarHeader-label")).getText().trim();
        } catch (Exception ignored) {}
        // MUI v4
        try {
            return driver.findElement(By.cssSelector(".MuiPickersCalendarHeader-transitionContainer")).getText().trim();
        } catch (Exception ignored) {}
        // Ant Design
        try {
            return driver.findElement(By.cssSelector(".ant-picker-header-view")).getText().trim();
        } catch (Exception ignored) {}
        // Bootstrap Datepicker
        try {
            return driver.findElement(By.cssSelector(".datepicker-days .datepicker-switch")).getText().trim();
        } catch (Exception ignored) {}
        // Pikaday
        try {
            return driver.findElement(By.cssSelector(".pika-title")).getText().trim();
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Aguarda o título do calendário mudar em relação ao valor capturado antes do clique.
     * Isso garante que o DOM foi atualizado antes de tentar ler o novo mês/ano.
     * Fallback: pausa mínima caso o wait expire sem detectar mudança.
     */
    private void aguardarMudancaTituloCalendario(WebDriver driver, String tituloAntes) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(d -> {
                    String tituloAgora = lerTextoTituloCalendario(d);
                    return !tituloAgora.isEmpty() && !tituloAgora.equals(tituloAntes);
                });
        } catch (Exception ignored) {
            pausar(500); // fallback se o seletor não existir (outros frameworks)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEITURA DO MÊS/ANO EXIBIDO NO CALENDÁRIO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tenta ler o mês e ano atuais usando três estratégias em ordem de especificidade:
     * 1. jQuery UI — spans separados (.ui-datepicker-month / .ui-datepicker-year)
     * 2. Atributos data-month/data-year (corrige offset 0-indexed do jQuery UI)
     * 3. Header textual combinado ("December 2025", "Dezembro de 2025")
     */
    private YearMonth obterMesAnoAtualDoCalendario(WebDriver driver) {
        YearMonth result = lerMesAnoJQueryUI(driver);
        if (result != null) return result;

        result = lerMesAnoViaDataAttributes(driver);
        if (result != null) return result;

        return lerMesAnoViaHeaderTextual(driver);
    }

    /**
     * jQuery UI expõe mês e ano via .ui-datepicker-month e .ui-datepicker-year.
     * Esses elementos podem ser <span> (modo padrão) ou <select> (quando
     * changeMonth/changeYear estão ativos). Ambos os casos são tratados.
     */
    private YearMonth lerMesAnoJQueryUI(WebDriver driver) {
        // jQuery UI — .ui-datepicker-month / .ui-datepicker-year (span ou select)
        try {
            WebElement monthEl = driver.findElement(By.cssSelector(".ui-datepicker-month"));
            WebElement yearEl  = driver.findElement(By.cssSelector(".ui-datepicker-year"));
            String monthText = resolverTextoElemento(monthEl);
            String yearText  = resolverTextoElemento(yearEl);
            int month = resolverNomeMes(monthText);
            int year  = Integer.parseInt(yearText.trim());
            if (month > 0 && year > 1900) return YearMonth.of(year, month);
        } catch (Exception ignored) {}

        // React Datepicker com showMonthDropdown + showYearDropdown
        try {
            WebElement monthEl = driver.findElement(By.cssSelector(".react-datepicker__month-select"));
            WebElement yearEl  = driver.findElement(By.cssSelector(".react-datepicker__year-select"));
            String monthText = resolverTextoElemento(monthEl);
            String yearText  = resolverTextoElemento(yearEl);
            int month = resolverNomeMes(monthText);
            int year  = Integer.parseInt(yearText.trim());
            if (month > 0 && year > 1900) return YearMonth.of(year, month);
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Lê o texto relevante de um elemento: se for <select>, devolve o texto da
     * opção selecionada; caso contrário, devolve getText() normalmente.
     */
    private String resolverTextoElemento(WebElement el) {
        try {
            if ("select".equalsIgnoreCase(el.getTagName())) {
                return new Select(el).getFirstSelectedOption().getText().trim();
            }
        } catch (Exception ignored) {}
        return safe(el.getText()).trim();
    }

    /**
     * jQuery UI anota TDs com data-month (0-indexed: 0=Janeiro) e data-year.
     * Incrementa data-month em 1 para converter para convenção 1-indexed.
     * Exclui células de meses adjacentes (ui-datepicker-other-month) para não
     * ler o mês errado quando o calendário exibe dias do mês anterior/posterior.
     */
    private YearMonth lerMesAnoViaDataAttributes(WebDriver driver) {
        try {
            // Restringe a TDs do mês atual, excluindo os dias de outros meses visíveis
            List<WebElement> cells = driver.findElements(By.xpath(
                "//td[@data-month and @data-year" +
                " and not(contains(@class,'ui-datepicker-other-month'))]"
            ));
            for (WebElement el : cells) {
                String dataMonth = el.getAttribute("data-month");
                String dataYear  = el.getAttribute("data-year");
                if (dataMonth == null || dataYear == null) continue;

                int year  = Integer.parseInt(dataYear.trim());
                int month = Integer.parseInt(dataMonth.trim()) + 1; // jQuery UI é 0-indexed
                if (month >= 1 && month <= 12 && year > 1900) {
                    return YearMonth.of(year, month);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Lê o header textual de datepickers que exibem mês e ano num único elemento. */
    private YearMonth lerMesAnoViaHeaderTextual(WebDriver driver) {
        List<By> headerLocators = List.of(
            By.cssSelector(".ui-datepicker-title"),                          // jQuery UI
            By.cssSelector(".pika-title"),                                   // Pikaday
            By.cssSelector(".react-datepicker__current-month"),              // React Datepicker
            By.cssSelector(".flatpickr-current-month"),                      // Flatpickr
            By.cssSelector(".mat-calendar-period-button"),                   // Angular Material
            By.cssSelector(".MuiPickersCalendarHeader-label"),               // MUI v5
            By.cssSelector(".MuiPickersCalendarHeader-transitionContainer"), // MUI v4
            By.cssSelector(".ant-picker-header-view"),                       // Ant Design
            By.cssSelector(".datepicker-days .datepicker-switch"),           // Bootstrap Datepicker
            By.xpath("//*[contains(@class,'datepicker-header')]"),
            By.xpath("//*[contains(@class,'calendar-header') or contains(@class,'month-year')]")
        );

        for (By locator : headerLocators) {
            try {
                for (WebElement el : driver.findElements(locator)) {
                    String text = el.getText().trim();
                    if (!text.isBlank()) {
                        YearMonth ym = parseHeaderText(text);
                        if (ym != null) return ym;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Parseia textos como "December 2025", "Dezembro de 2025" ou "Dec/2025".
     * Extrai o ano via regex de 4 dígitos e o mês via MONTH_MAP com word boundaries,
     * garantindo que abreviações como "jan" não casem dentro de "january" ou "janeiro".
     */
    private YearMonth parseHeaderText(String headerText) {
        String cleaned = headerText
            .replaceAll("(?i)\\bde\\b", " ") // remove conector "de" em português
            .replaceAll("[/,]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        Matcher yearMatcher = YEAR_PATTERN.matcher(cleaned);
        if (!yearMatcher.find()) return null;

        int year = Integer.parseInt(yearMatcher.group(1));
        String normalizedHeader = stripAccents(cleaned.toLowerCase());

        for (Map.Entry<String, Integer> entry : MONTH_MAP.entrySet()) {
            if (normalizedHeader.matches(".*\\b" + entry.getKey() + "\\b.*")) {
                return YearMonth.of(year, entry.getValue());
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESOLUÇÃO DE NOME DE MÊS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converte nome de mês (en/pt, com ou sem acento, qualquer capitalização)
     * para número 1-12. Remove acentos antes de consultar o MONTH_MAP.
     */
    private int resolverNomeMes(String monthName) {
        if (monthName == null || monthName.isBlank()) return -1;
        return MONTH_MAP.getOrDefault(stripAccents(monthName.trim().toLowerCase()), -1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BOTÕES DE NAVEGAÇÃO
    // ─────────────────────────────────────────────────────────────────────────

    private void clicarProximo(WebDriver driver) {
        clicarBotaoNavegacao(driver, List.of(
            // jQuery UI
            By.cssSelector("a.ui-datepicker-next"),
            // Flatpickr
            By.cssSelector(".flatpickr-next-month"),
            // Pikaday
            By.cssSelector(".pika-next"),
            // React Datepicker
            By.cssSelector(".react-datepicker__navigation--next"),
            // Bootstrap Datepicker
            By.cssSelector(".datepicker-days .next"),
            // MUI v5 — botão "go to next month"
            By.cssSelector("[data-testid='ArrowRightIcon']"),
            By.cssSelector("button.MuiPickersCalendarHeader-switchViewButton ~ button"),
            // MUI v4 — ícone de seta direita
            By.xpath("//button[.//*[local-name()='svg' and contains(@data-testid,'ArrowRight')]]"),
            // Angular Material
            By.cssSelector(".mat-calendar-next-button"),
            // Ant Design
            By.cssSelector(".ant-picker-header-next-btn, .ant-picker-header-super-next-btn"),
            // Genérico ARIA / texto
            By.xpath("//button[contains(@aria-label,'Next') or contains(@aria-label,'next') or contains(@aria-label,'Próximo') or contains(@aria-label,'próximo')]"),
            By.xpath("//a[contains(@class,'ui-datepicker-next')]"),
            By.xpath("//button[normalize-space(text())='>' or normalize-space(text())='›' or normalize-space(text())='→']")
        ), "Próximo");
    }

    private void clicarAnterior(WebDriver driver) {
        clicarBotaoNavegacao(driver, List.of(
            // jQuery UI
            By.cssSelector("a.ui-datepicker-prev"),
            // Flatpickr
            By.cssSelector(".flatpickr-prev-month"),
            // Pikaday
            By.cssSelector(".pika-prev"),
            // React Datepicker
            By.cssSelector(".react-datepicker__navigation--previous"),
            // Bootstrap Datepicker
            By.cssSelector(".datepicker-days .prev"),
            // MUI v5 — botão "go to previous month"
            By.cssSelector("[data-testid='ArrowLeftIcon']"),
            // MUI v4
            By.xpath("//button[.//*[local-name()='svg' and contains(@data-testid,'ArrowLeft')]]"),
            // Angular Material
            By.cssSelector(".mat-calendar-previous-button"),
            // Ant Design
            By.cssSelector(".ant-picker-header-prev-btn, .ant-picker-header-super-prev-btn"),
            // Genérico ARIA / texto
            By.xpath("//button[contains(@aria-label,'Previous') or contains(@aria-label,'previous') or contains(@aria-label,'Anterior') or contains(@aria-label,'anterior')]"),
            By.xpath("//a[contains(@class,'ui-datepicker-prev')]"),
            By.xpath("//button[normalize-space(text())='<' or normalize-space(text())='‹' or normalize-space(text())='←']")
        ), "Anterior");
    }

    /** Re-busca o botão a cada chamada para evitar StaleElementReferenceException. */
    private void clicarBotaoNavegacao(WebDriver driver, List<By> locators, String label) {
        for (By locator : locators) {
            try {
                WebElement btn = driver.findElement(locator);
                if (btn.isDisplayed() && btn.isEnabled()) {
                    btn.click();
                    return;
                }
            } catch (NoSuchElementException ignored) {}
        }
        throw new DatePickerCalendarNotOpenedException(
            "Botão de navegação '" + label + "' não encontrado no calendário");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLIQUE NO DIA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clica no dia desejado garantindo que o calendário está no contexto correto.
     *
     * Estratégia 1 — jQuery UI: By.linkText() é o seletor mais simples e direto, pois
     * cada dia é renderizado como {@code <a>} com o número exato. Não há risco de clicar
     * em dias de outros meses porque a navegação já posicionou o calendário no mês certo.
     *
     * Estratégia 2 — XPath específico para jQuery UI com exclusão de outros meses/disabled.
     *
     * Estratégia 3 — Locators genéricos para Flatpickr, React Datepicker e outros.
     */
    private void clicarNoDia(WebDriver driver, int day, YearMonth expectedYearMonth) {
        String dayStr = String.valueOf(day);

        // ── Estratégia 1: jQuery UI — By.linkText() (mais simples e confiável) ──
        try {
            WebElement dayLink = driver.findElement(By.linkText(dayStr));
            if (isElementInteractable(driver, dayLink)) {
                clicarComFallbackJs(driver, dayLink);
                pausar(500);
                return;
            }
        } catch (NoSuchElementException ignored) {}

        // ── Estratégia 2: XPath jQuery UI com exclusão de outros meses ──
        try {
            WebElement dayCell = driver.findElement(By.xpath(
                "//td[@data-handler='selectDay']" +
                "[not(contains(@class,'ui-datepicker-other-month'))]" +
                "[not(contains(@class,'ui-state-disabled'))]" +
                "/a[normalize-space(text())='" + dayStr + "']"
            ));
            if (isElementInteractable(driver, dayCell)) {
                clicarComFallbackJs(driver, dayCell);
                pausar(500);
                return;
            }
        } catch (NoSuchElementException ignored) {}

        // ── Estratégia 3: Locators específicos por biblioteca ──
        List<By> dayLocators = List.of(
            // Flatpickr — dias do mês atual, não desabilitados
            By.cssSelector(".flatpickr-day:not(.disabled):not(.prevMonthDay):not(.nextMonthDay)"),
            // React Datepicker
            By.cssSelector(
                ".react-datepicker__day:not(.react-datepicker__day--disabled)" +
                ":not(.react-datepicker__day--outside-month)"
            ),
            // MUI v5 (MUI Date Pickers v6+)
            By.cssSelector("button.MuiPickersDay-root:not(.Mui-disabled):not(.MuiPickersDay-dayOutsideMonth)"),
            // MUI v4 (legacy @material-ui/pickers)
            By.cssSelector("button.MuiPickersDay-day:not(.MuiPickersDay-dayDisabled)"),
            // Angular Material
            By.cssSelector("button.mat-calendar-body-cell:not(.mat-calendar-body-disabled)"),
            // Ant Design
            By.cssSelector(".ant-picker-cell:not(.ant-picker-cell-disabled) .ant-picker-cell-inner"),
            // Bootstrap Datepicker
            By.cssSelector("td.day:not(.disabled):not(.old):not(.new)"),
            // Pikaday
            By.cssSelector(".pika-button:not(.is-disabled):not(.is-outside-current-month)"),
            // Genérico — qualquer elemento clicável com o número do dia
            By.xpath(
                "//*[self::td or self::button or self::div or self::span or self::a]" +
                "[normalize-space(text())='" + dayStr + "']" +
                "[not(contains(@class,'disabled'))]" +
                "[not(contains(@class,'other-month'))]" +
                "[not(contains(@class,'unavailable'))]" +
                "[not(@disabled)]" +
                "[not(contains(@aria-disabled,'true'))]"
            )
        );

        for (By locator : dayLocators) {
            try {
                for (WebElement candidate : driver.findElements(locator)) {
                    if (!isElementInteractable(driver, candidate)) continue;

                    String text    = safe(candidate.getText()).trim();
                    String aria    = safe(candidate.getAttribute("aria-label"));
                    String dataDay = safe(candidate.getAttribute("data-day"));

                    if (!matchesDay(text, dayStr) && !matchesDay(dataDay, dayStr)
                            && !aria.matches(".*\\b" + dayStr + "\\b.*")) {
                        continue;
                    }

                    scrollCentralizado(driver, candidate);
                    clicarComFallbackJs(driver, candidate);
                    pausar(500);
                    return;
                }
            } catch (Exception ignored) {}
        }

        if (!driver.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty()) {
            throw new DatePickerDateNotFoundException(
                "Dia '" + dayStr + "' não encontrado no calendário para " + expectedYearMonth);
        }
        throw new DatePickerCalendarNotOpenedException(
            "O calendário fechou antes da seleção do dia '" + dayStr + "'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAITS E ESTABILIDADE
    // ─────────────────────────────────────────────────────────────────────────

    private void aguardarFechamentoCalendario(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2))
                .until(d -> d.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty());
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APLICAÇÃO DIRETA NO CAMPO
    // ─────────────────────────────────────────────────────────────────────────

    private void aplicarValorNoCampo(WebDriver driver, WebElement element, String value) {
        // ── Flatpickr: usa a API interna diretamente (mais confiável que sendKeys) ──
        if (driver instanceof JavascriptExecutor js) {
            try {
                Boolean setViaFlatpickr = (Boolean) js.executeScript(
                    "const el = arguments[0], val = arguments[1];" +
                    "if (el._flatpickr) { el._flatpickr.setDate(val, true); return true; }" +
                    "return false;",
                    element, value);
                if (Boolean.TRUE.equals(setViaFlatpickr)) return;
            } catch (Exception ignored) {}
        }

        try {
            element.clear();
        } catch (Exception ignored) {}

        // ── sendKeys padrão ──
        try {
            element.sendKeys(value);
            dispararEventos(driver, element);
            return;
        } catch (Exception ignored) {}

        // ── Fallback JS genérico (remove readonly/disabled, seta value, dispara eventos) ──
        try {
            ((JavascriptExecutor) driver).executeScript(
                "const el = arguments[0], val = arguments[1];" +
                "el.removeAttribute('readonly');" +
                "el.removeAttribute('disabled');" +
                "el.focus();" +
                "el.value = val;" +
                "['input','change','blur'].forEach(e => " +
                "  el.dispatchEvent(new Event(e, {bubbles:true})));",
                element, value);
        } catch (Exception e) {
            throw new DatePickerDateDisabledException(
                "Não foi possível aplicar a data no campo: " + value, e);
        }
    }

    private void validarValor(WebElement element, String expected) {
        String current = safe(element.getAttribute("value")).trim();
        if (current.isEmpty()) current = safe(element.getText()).trim();
        if (current.isEmpty()) return; // campo não expõe valor — não é possível validar

        // Comparação primária: converte ambos para LocalDate (agnóstico de formato e separador)
        LocalDate expectedDate = parseTargetDate(expected);
        LocalDate currentDate  = parseTargetDate(current);
        if (expectedDate != null && currentDate != null) {
            if (!expectedDate.equals(currentDate)) {
                throw new IllegalStateException(
                    "Data selecionada não confere: esperado [" + expected + "] atual [" + current + "]");
            }
            return;
        }

        // Fallback: compara apenas os dígitos (ignora separadores e capitalização)
        String expectedDigits = expected.replaceAll("[^0-9]", "");
        String currentDigits  = current.replaceAll("[^0-9]", "");
        if (!currentDigits.isEmpty()
                && !currentDigits.contains(expectedDigits)
                && !expectedDigits.contains(currentDigits)) {
            throw new IllegalStateException(
                "Data selecionada não confere: esperado [" + expected + "] atual [" + current + "]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITÁRIOS
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isElementInteractable(WebDriver driver, WebElement element) {
        try {
            if (!element.isDisplayed() || !element.isEnabled()) return false;
            return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
                "const r=arguments[0].getBoundingClientRect(); return r.width>0 && r.height>0;",
                element));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesDay(String text, String day) {
        String trimmed = safe(text).trim();
        if (trimmed.equals(day.trim())) return true;
        try {
            return Integer.parseInt(trimmed) == Integer.parseInt(day.trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void clicarComFallbackJs(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    private void dispararEventos(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
                element);
        } catch (Exception ignored) {}
    }

    private void scrollCentralizado(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
        } catch (Exception ignored) {}
    }

    /**
     * Remove acentos e diacríticos via NFD + remoção de Combining Marks.
     * Necessário para normalizar "março" → "marco" antes da comparação com MONTH_MAP.
     */
    private String stripAccents(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                         .replaceAll("\\p{M}+", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void pausar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

// ========== COMBOBOX: SelectExecutionStrategy ==========
@Component
class SelectExecutionStrategy implements ExecutionStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.CUSTOM_COMBOBOX;
    }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            throw new SelectElementNotFoundException("Elemento select nao encontrado");
        }

        String value = step != null && step.getValor() != null ? step.getValor().trim() : "";
        if (value.isBlank()) {
            throw new IllegalArgumentException("Valor para selecao nao informado no step");
        }

        abrirDropdown(driver, element);
        if (tryTypeAhead(driver, element, value)) {
            return;
        }

        TipoSelect tipo = detectarTipoSelect(driver, element);

        // Java 17 pattern matching requires proper syntax
        if (tipo instanceof TipoSelect.SelectNativo) {
            selecionarSelectNativo(driver, element, value);
        } else if (tipo instanceof TipoSelect.CustomizadoDiv) {
            selecionarCustomizadoDiv(driver, element, value);
        } else if (tipo instanceof TipoSelect.CustomizadoUL) {
            selecionarCustomizadoUL(driver, element, value);
        } else if (tipo instanceof TipoSelect.CustomizadoButton) {
            selecionarCustomizadoButton(driver, element, value);
        } else if (tipo instanceof TipoSelect.CustomizadoGenerico) {
            selecionarCustomizadoGenerico(driver, element, value);
        }

        if (!validarSelecao(driver, element, value)) {
            throw new SelectOptionNotFoundException("Selecao nao confirmada no combobox: " + value);
        }
    }

    private TipoSelect detectarTipoSelect(WebDriver driver, WebElement element) {
        String tagName = safe(element.getTagName()).toLowerCase(Locale.ROOT);

        if (tagName.equals("select")) {
            return new TipoSelect.SelectNativo();
        }

        WebElement optionsContainer = procurarContainerOpcoes(driver, element);
        if (optionsContainer != null) {
            String containerTag = safe(optionsContainer.getTagName()).toLowerCase(Locale.ROOT);

            if (containerTag.equals("ul") || containerTag.equals("ol")) {
                return new TipoSelect.CustomizadoUL();
            }

            if (containerTag.equals("div")) {
                try {
                    List<WebElement> buttons = optionsContainer.findElements(By.tagName("button"));
                    if (!buttons.isEmpty()) {
                        return new TipoSelect.CustomizadoButton();
                    }
                } catch (Exception ignored) {
                }
                return new TipoSelect.CustomizadoDiv();
            }
        }

        return new TipoSelect.CustomizadoGenerico();
    }

    private void abrirDropdown(WebDriver driver, WebElement element) {
        scrollCentralizado(driver, element);
        clicarComRetry(driver, element);
        pausar(250);
    }

    private WebElement procurarContainerOpcoes(WebDriver driver, WebElement element) {
        try {
            for (String attr : List.of("aria-controls", "aria-owns")) {
                String ref = safe(element.getAttribute(attr)).trim();
                if (!ref.isBlank()) {
                    try {
                        WebElement found = driver.findElement(By.id(ref));
                        if (found.isDisplayed()) {
                            return found;
                        }
                    } catch (NoSuchElementException ignored) {
                    }
                }
            }

            List<By> localLocators = List.of(
                By.xpath("./ancestor::*[@role='listbox' or @role='menu' or @role='combobox' or contains(@class,'dropdown') or contains(@class,'combo') or contains(@class,'select2')][1]"),
                By.xpath("./following-sibling::*[@role='listbox' or contains(@class,'dropdown') or contains(@class,'menu') or contains(@class,'popup')][1]")
            );
            for (By locator : localLocators) {
                try {
                    WebElement found = element.findElement(locator);
                    if (found.isDisplayed()) {
                        return found;
                    }
                } catch (NoSuchElementException ignored) {
                }
            }

            List<By> globalLocators = List.of(
                By.cssSelector("[role='listbox']"),
                By.cssSelector(".select2-results, .select2-dropdown, .choices__list--dropdown, .dropdown-menu.show, .menu.show, .MuiAutocomplete-popper, .mat-mdc-autocomplete-panel, .ng-dropdown-panel, .vs__dropdown-menu")
            );
            for (By locator : globalLocators) {
                try {
                    for (WebElement candidate : driver.findElements(locator)) {
                        if (candidate.isDisplayed()) {
                            return candidate;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private void selecionarSelectNativo(WebDriver driver, WebElement element, String value) {
        try {
            Select select = new Select(element);

            try {
                select.selectByVisibleText(value);
                return;
            } catch (NoSuchElementException ignored) {
            }

            try {
                select.selectByValue(value);
                return;
            } catch (NoSuchElementException ignored) {
            }

            String expected = normalize(value);
            for (WebElement option : select.getOptions()) {
                String optionText = safe(option.getText());
                String optionValue = safe(option.getAttribute("value"));
                if (normalize(optionText).equals(expected) || normalize(optionValue).equals(expected)) {
                    clicarComRetry(driver, option);
                    return;
                }
            }

            throw new SelectOptionNotFoundException("Opcao nao encontrada: " + value);

        } catch (Exception e) {
            throw new SelectOptionNotFoundException("Erro ao selecionar opcao nativa: " + value, e);
        }
    }

    private void selecionarCustomizadoDiv(WebDriver driver, WebElement element, String value) {
        selecionarOpcaoCustomizada(driver, element, value, List.of(
            By.xpath(".//*[@role='option']"),
            By.xpath(".//*[contains(@class,'option') or contains(@class,'item') or contains(@class,'menu-item') or contains(@class,'select2-results__option')]"),
            By.xpath(".//*[self::div or self::span][normalize-space()]")
        ), "dropdown");
    }

    private void selecionarCustomizadoUL(WebDriver driver, WebElement element, String value) {
        selecionarOpcaoCustomizada(driver, element, value, List.of(
            By.xpath(".//li"),
            By.xpath(".//*[@role='option']"),
            By.xpath(".//*[contains(@class,'option') or contains(@class,'item')]")
        ), "lista");
    }

    private void selecionarCustomizadoButton(WebDriver driver, WebElement element, String value) {
        selecionarOpcaoCustomizada(driver, element, value, List.of(
            By.xpath(".//button"),
            By.xpath(".//*[@role='option']"),
            By.xpath(".//*[contains(@class,'option') or contains(@class,'item')]")
        ), "buttons");
    }

    private void selecionarCustomizadoGenerico(WebDriver driver, WebElement element, String value) {
        selecionarOpcaoCustomizada(driver, element, value, List.of(
            By.xpath(".//*[@role='option']"),
            By.xpath(".//*[contains(@class,'option') or contains(@class,'item') or contains(@class,'menu-item')]"),
            By.xpath(".//*[self::li or self::button or self::div or self::span][normalize-space()]")
        ), "generico");
    }

    private void selecionarOpcaoCustomizada(WebDriver driver, WebElement element, String value, List<By> locators, String origem) {
        abrirDropdown(driver, element);

        WebElement container = procurarContainerOpcoes(driver, element);
        List<WebElement> candidates = coletarCandidatos(container, locators);
        if (candidates.isEmpty()) {
            candidates = coletarCandidatos(driver, locators);
        }

        for (WebElement option : candidates) {
            try {
                if (!isMatchingOption(option, value) || !isElementInteractable(driver, option)) {
                    continue;
                }
                scrollCentralizado(driver, option);
                clicarComRetry(driver, option);
                aguardarFechamentoDropdown(driver);
                if (validarSelecao(driver, element, value)) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        if (tryTypeAheadWithNavigation(driver, element, value)) {
            return;
        }

        throw new SelectOptionNotFoundException("Opcao nao encontrada no modo " + origem + ": " + value
                + " | candidatos: " + descreverCandidatos(candidates));
    }

    private boolean tryTypeAhead(WebDriver driver, WebElement element, String value) {
        try {
            WebElement input = resolveTypingElement(element);
            if (input == null) {
                return false;
            }
            selecionarTexto(input);
            input.sendKeys(value);
            pausar(250);
            return validarSelecao(driver, input, value) || validarSelecao(driver, element, value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryTypeAheadWithNavigation(WebDriver driver, WebElement element, String value) {
        try {
            WebElement input = resolveTypingElement(element);
            if (input == null) {
                return false;
            }
            selecionarTexto(input);
            input.sendKeys(value);
            pausar(250);
            input.sendKeys(Keys.ARROW_DOWN);
            input.sendKeys(Keys.ENTER);
            pausar(250);
            if (validarSelecao(driver, input, value) || validarSelecao(driver, element, value)) {
                return true;
            }
            input.sendKeys(Keys.TAB);
            pausar(250);
            return validarSelecao(driver, input, value) || validarSelecao(driver, element, value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private WebElement resolveTypingElement(WebElement element) {
        try {
            String tag = safe(element.getTagName()).toLowerCase(Locale.ROOT);
            String role = safe(element.getAttribute("role")).toLowerCase(Locale.ROOT);
            if ("input".equals(tag) || "textarea".equals(tag) || role.contains("combobox")) {
                return element;
            }
        } catch (Exception ignored) {
        }

        try {
            return element.findElement(By.cssSelector("input, textarea"));
        } catch (NoSuchElementException ignored) {
            return null;
        }
    }

    private List<WebElement> coletarCandidatos(SearchContext context, List<By> locators) {
        if (context == null) {
            return List.of();
        }

        List<WebElement> candidates = new ArrayList<>();
        for (By locator : locators) {
            try {
                for (WebElement found : context.findElements(locator)) {
                    if (!candidates.contains(found)) {
                        candidates.add(found);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return candidates;
    }

    private boolean isMatchingOption(WebElement option, String expectedValue) {
        try {
            String combined = String.join(" ",
                safe(option.getText()),
                safe(option.getAttribute("value")),
                safe(option.getAttribute("aria-label")),
                safe(option.getAttribute("title")),
                safe(option.getAttribute("data-value"))
            );
            return matches(combined, normalize(expectedValue));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void clicarComRetry(WebDriver driver, WebElement element) {
        try {
            element.click();
        } catch (Exception clickError) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception jsError) {
                throw new SelectOptionNotFoundException("Falha ao clicar na opcao", jsError);
            }
        }
    }

    private boolean isElementInteractable(WebDriver driver, WebElement element) {
        try {
            if (!element.isDisplayed() || !element.isEnabled()) {
                return false;
            }

            String script = "const rect = arguments[0].getBoundingClientRect(); return rect.width > 0 && rect.height > 0;";
            return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(script, element));
        } catch (Exception e) {
            return false;
        }
    }

    private void aguardarFechamentoDropdown(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
            wait.until(d -> {
                List<WebElement> dropdowns = d.findElements(By.xpath("//*[contains(@class,'dropdown') and contains(@class,'open')] | //*[@role='listbox']"));
                return dropdowns.stream().noneMatch(WebElement::isDisplayed);
            });
        } catch (Exception ignored) {
        }
    }

    private boolean validarSelecao(WebDriver driver, WebElement element, String expectedValue) {
        String expected = normalize(expectedValue);
        try {
            if (matches(element.getAttribute("value"), expected)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            if (matches(element.getAttribute("aria-label"), expected)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            if (matches(element.getText(), expected)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            WebElement selected = element.findElement(By.xpath(".//*[contains(@class,'selected') or @selected or contains(@class,'active') or @aria-selected='true']"));
            if (selected.isDisplayed() && isMatchingOption(selected, expectedValue)) {
                return true;
            }
        } catch (NoSuchElementException ignored) {
        }

        try {
            if (driver instanceof JavascriptExecutor js) {
                Object result = js.executeScript("return [arguments[0].value || '', arguments[0].textContent || '', arguments[0].getAttribute('aria-label') || '', arguments[0].getAttribute('title') || ''].join(' ');", element);
                if (result instanceof String text && matches(text, expected)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void scrollCentralizado(WebDriver driver, WebElement element) {
        try {
            String script = "arguments[0].scrollIntoView({block:'center', inline:'nearest'});";
            ((JavascriptExecutor) driver).executeScript(script, element);
        } catch (Exception ignored) {
        }
    }

    private void selecionarTexto(WebElement input) {
        try {
            input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        } catch (Exception ignored) {
        }
    }

    private boolean matches(String actual, String expectedNormalized) {
        String normalizedActual = normalize(actual);
        return !normalizedActual.isBlank()
                && (normalizedActual.equals(expectedNormalized)
                || normalizedActual.contains(expectedNormalized)
                || expectedNormalized.contains(normalizedActual));
    }

    private String normalize(String value) {
        String safeValue = safe(value).replace('\u00A0', ' ');
        String normalized = Normalizer.normalize(safeValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String descreverCandidatos(List<WebElement> candidates) {
        List<String> values = new ArrayList<>();
        for (WebElement candidate : candidates) {
            try {
                String text = safe(candidate.getText()).trim();
                if (!text.isBlank()) {
                    values.add(text);
                }
            } catch (Exception ignored) {
            }
            if (values.size() >= 8) {
                break;
            }
        }
        return values.isEmpty() ? "<nenhum texto visivel>" : String.join(", ", values);
    }

    private void pausar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private sealed interface TipoSelect {
        record SelectNativo() implements TipoSelect {}
        record CustomizadoDiv() implements TipoSelect {}
        record CustomizadoUL() implements TipoSelect {}
        record CustomizadoButton() implements TipoSelect {}
        record CustomizadoGenerico() implements TipoSelect {}
    }
}

