package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.exception.DatePickerCalendarNotOpenedException;
import com.qorbit.engine.exception.DatePickerDateDisabledException;
import com.qorbit.engine.exception.DatePickerDateNotFoundException;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DateInputExecutionStrategy implements ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(DateInputExecutionStrategy.class);

    private static final String CALENDAR_SELECTOR =
            ".ui-datepicker, .flatpickr-calendar.open, .react-datepicker__month-container, " +
            ".mat-datepicker-content, .MuiPickersPopper-root, .MuiPickersLayout-root, .MuiDateCalendar-root, " +
            ".ant-picker-dropdown:not(.ant-picker-dropdown-hidden), " +
            ".pika-single, .picker__holder.picker__holder--opened, .dp-popup, " +
            ".air-datepicker, .air-datepicker-body, .daterangepicker.show-calendar, " +
            "[data-testid*='datepicker'][class*='open'], " +
            "[class*='datepicker-popup'], [class*='date-picker-popup'], " +
            "[class*='calendar-dropdown'][class*='open'], " +
            "[class*='calendar-container']:not([style*='display: none']), " +
            "[class*='calendar'][class*='open'], [class*='picker'][class*='open']";

    private static final String NEXT_BTN_SELECTOR =
            ".ui-datepicker-next, .flatpickr-next-month, .react-datepicker__navigation--next, " +
            ".mat-calendar-next-button, .MuiPickersArrowSwitcher-button[title*='Next'], .pika-next, " +
            "[aria-label*='Next month'], [aria-label*='next month'], " +
            "[aria-label*='Próximo'], [aria-label*='Proximo'], [aria-label*='próximo'], [aria-label*='proximo'], " +
            "button[class*='next-month'], button[class*='next-btn'], " +
            "[class*='calendar-nav-right'], [class*='arrow-right']:not(input), .rdp-nav_button_next";

    private static final String PREV_BTN_SELECTOR =
            ".ui-datepicker-prev, .flatpickr-prev-month, .react-datepicker__navigation--previous, " +
            ".mat-calendar-previous-button, .MuiPickersArrowSwitcher-button[title*='Previous'], .pika-prev, " +
            "[aria-label*='Previous month'], [aria-label*='previous month'], " +
            "[aria-label*='Anterior'], [aria-label*='anterior'], " +
            "button[class*='prev-month'], button[class*='prev-btn'], " +
            "[class*='calendar-nav-left'], [class*='arrow-left']:not(input), .rdp-nav_button_prev";

    private static final String DAY_ENABLED_SELECTOR =
            "td[data-handler='selectDay']:not(.ui-datepicker-unselectable), " +
            ".flatpickr-day:not(.disabled):not(.flatpickr-disabled):not(.prevMonthDay):not(.nextMonthDay), " +
            ".react-datepicker__day:not(.react-datepicker__day--disabled):not(.react-datepicker__day--outside-month), " +
            ".mat-calendar-body-cell:not(.mat-calendar-body-disabled), " +
            ".MuiPickersDay-root:not(.Mui-disabled), " +
            ".pika-button:not([disabled]), " +
            ".rdp-day:not(.rdp-day_disabled):not(.rdp-day_outside), " +
            "td[class*='day']:not([class*='disabled']):not([class*='off']):not([class*='other-month']):not([class*='muted']):not([class*='blocked'])";

    private static final String DAY_ALL_SELECTOR =
            "td[data-handler='selectDay'], .flatpickr-day:not(.hidden), " +
            ".react-datepicker__day, .mat-calendar-body-cell, .MuiPickersDay-root, " +
            ".pika-button, .rdp-day, " +
            "td[class*='day']:not([class*='name']):not([class*='header']):not([class*='label'])";

    private static final String YEAR_SELECT_SELECTOR =
            "select[class*='year'], select[aria-label*='year'], select[aria-label*='Year'], " +
            "select[aria-label*='ano'], select[aria-label*='Ano'], " +
            "select[name*='year'], select[id*='year'], " +
            ".ui-datepicker-year, .react-datepicker__year-select, " +
            "input[class*='numInput'][class*='cur-year']";

    private static final String MONTH_SELECT_SELECTOR =
            "select[class*='month'], select[aria-label*='month'], select[aria-label*='Month'], " +
            "select[aria-label*='mes'], select[aria-label*='Mes'], " +
            "select[name*='month'], select[id*='month'], " +
            ".ui-datepicker-month, .react-datepicker__month-select";

    private static final String HEADER_SELECTOR =
            ".ui-datepicker-title, .flatpickr-current-month, .react-datepicker__current-month, " +
            ".mat-calendar-period-button, .MuiPickersCalendarHeader-label, .pika-title, .rdp-caption_label, " +
            "[class*='calendar-title'], [class*='month-year-header'], " +
            "[class*='current-month'], [class*='datepicker-header'] span, " +
            "[class*='picker-header'] span, [class*='calendar-header-title']";

    private static final String PERIOD_BTN_SELECTOR =
            ".mat-calendar-period-button, .MuiPickersCalendarHeader-switchViewButton, " +
            "[class*='period-button'], [class*='year-view-toggle']";

    private static final String YEAR_CELL_SELECTOR =
            ".mat-calendar-body-cell-content, .MuiPickersYear-yearButton, " +
            "[class*='year-cell'], [class*='year-option'], [class*='year-item']";

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("ddMMyyyy")
    );

    private static final Map<String, Integer> MONTH_NAMES = new LinkedHashMap<>();
    static {
        MONTH_NAMES.put("janeiro", 1);   MONTH_NAMES.put("jan", 1);
        MONTH_NAMES.put("fevereiro", 2); MONTH_NAMES.put("fev", 2);
        MONTH_NAMES.put("marco", 3);     MONTH_NAMES.put("mar", 3);
        MONTH_NAMES.put("abril", 4);     MONTH_NAMES.put("abr", 4);
        MONTH_NAMES.put("maio", 5);      MONTH_NAMES.put("mai", 5);
        MONTH_NAMES.put("junho", 6);     MONTH_NAMES.put("jun", 6);
        MONTH_NAMES.put("julho", 7);     MONTH_NAMES.put("jul", 7);
        MONTH_NAMES.put("agosto", 8);    MONTH_NAMES.put("ago", 8);
        MONTH_NAMES.put("setembro", 9);  MONTH_NAMES.put("set", 9);
        MONTH_NAMES.put("outubro", 10);  MONTH_NAMES.put("out", 10);
        MONTH_NAMES.put("novembro", 11); MONTH_NAMES.put("nov", 11);
        MONTH_NAMES.put("dezembro", 12); MONTH_NAMES.put("dez", 12);
        MONTH_NAMES.put("january", 1);   MONTH_NAMES.put("february", 2);
        MONTH_NAMES.put("march", 3);     MONTH_NAMES.put("april", 4);
        MONTH_NAMES.put("may", 5);       MONTH_NAMES.put("june", 6);
        MONTH_NAMES.put("july", 7);      MONTH_NAMES.put("august", 8);
        MONTH_NAMES.put("september", 9); MONTH_NAMES.put("october", 10);
        MONTH_NAMES.put("november", 11); MONTH_NAMES.put("december", 12);
    }

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(\\d{4})\\b");
    private static final Pattern MM_YYYY_PATTERN = Pattern.compile("(\\d{1,2})[/-](\\d{4})");

    @Override
    public StrategyType type() { return StrategyType.DATE_INPUT; }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) throws Exception {
        if (element == null) throw new IllegalArgumentException("Elemento nao encontrado para data");

        String rawValue = step.getValor() != null ? step.getValor().trim() : "";
        String executionId = step.getId() != null ? String.valueOf(step.getId()) : "?";

        log.info("[datepicker][execId={}] Iniciando preenchimento — valor alvo: '{}'", executionId, rawValue);

        esconderWidgetsFlutuantesSeNaoForTesteDeChat(driver, step);

        LocalDate targetDate = parseDate(rawValue);
        boolean isReadonly = isReadonly(element);

        log.info("[datepicker][execId={}] Campo readonly={} | Data parseada={}", executionId, isReadonly, targetDate);

        if (isNativeDateInput(element)) {
            log.info("[datepicker][execId={}] tipo=input-date-nativo — usando estrategia JS direto", executionId);
            if (targetDate == null) {
                throw new IllegalStateException(
                        "[datepicker][execId=" + executionId + "] input[type=date] requer data parseavel. Valor recebido: '" + rawValue + "'");
            }
            applyNativeDateInput(driver, element, targetDate, rawValue, executionId);
            return;
        }

        if (!isReadonly) {
            if (trySendKeys(driver, element, rawValue, executionId)) {
                log.info("[datepicker][execId={}] estrategia=digitacao resultado=SUCESSO", executionId);
                return;
            }
            log.info("[datepicker][execId={}] estrategia=digitacao resultado=FALHA — acionando fallback visual", executionId);
        } else {
            log.info("[datepicker][execId={}] Campo readonly — estrategia=digitacao ignorada", executionId);
        }

        if (targetDate != null) {
            runCalendarStrategy(driver, element, rawValue, targetDate, executionId);
        } else {
            log.warn("[datepicker][execId={}] Formato de data nao reconhecido: '{}' — tentando JS value", executionId, rawValue);
            if (!tryJsValue(driver, element, rawValue, executionId)) {
                throw new IllegalStateException(
                        "[datepicker][execId=" + executionId + "] Falha ao aplicar data. Formato nao reconhecido e JS fallback falhou. Valor: '" + rawValue + "'");
            }
        }
    }

    private boolean isStepDeChat(StepTeste step) {
        if (step == null) return false;

        String nome = step.getNomeLogicoElemento();
        String valor = step.getValor();

        String base = ((nome == null ? "" : nome) + " " + (valor == null ? "" : valor)).toLowerCase(Locale.ROOT);

        return base.contains("chat")
                || base.contains("whatsapp")
                || base.contains("atendimento")
                || base.contains("contato")
                || base.contains("canal");
    }

    private void esconderWidgetsFlutuantesSeNaoForTesteDeChat(WebDriver driver, StepTeste step) {
        if (isStepDeChat(step)) return;

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "Array.from(document.querySelectorAll('button, a, div, span, iframe')).forEach(function(el) {" +
                    "  try {" +
                    "    const txt = (el.innerText || el.textContent || '').toLowerCase();" +
                    "    const aria = (el.getAttribute('aria-label') || '').toLowerCase();" +
                    "    const cls = (el.getAttribute('class') || '').toLowerCase();" +
                    "    if (" +
                    "      txt.includes('whatsapp') || " +
                    "      txt.includes('canais de contato') || " +
                    "      txt.includes('fale sobre seguros') || " +
                    "      txt.includes('central de atendimento') || " +
                    "      aria.includes('chat') || " +
                    "      cls.includes('chat') || " +
                    "      cls.includes('whatsapp')" +
                    "    ) {" +
                    "      el.style.setProperty('display', 'none', 'important');" +
                    "      el.style.setProperty('visibility', 'hidden', 'important');" +
                    "      el.style.setProperty('pointer-events', 'none', 'important');" +
                    "    }" +
                    "  } catch(e) {}" +
                    "});"
            );
        } catch (Exception ignored) { }
    }

    private boolean isNativeDateInput(WebElement element) {
        try {
            String tag = element.getTagName();
            String type = element.getAttribute("type");
            if (!"input".equalsIgnoreCase(tag)) return false;
            return "date".equalsIgnoreCase(type)
                    || "datetime-local".equalsIgnoreCase(type)
                    || "month".equalsIgnoreCase(type)
                    || "week".equalsIgnoreCase(type);
        } catch (Exception e) {
            return false;
        }
    }

    private void applyNativeDateInput(WebDriver driver, WebElement element,
                                      LocalDate date, String rawValue, String execId) {
        String isoValue = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            if (driver instanceof JavascriptExecutor js) {
                js.executeScript(
                        "arguments[0].value = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input', {bubbles:true}));" +
                        "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                        element, isoValue);

                if (validateField(element, isoValue) || validateDateInField(element, date)) {
                    log.info("[datepicker][execId={}] tipo=input-date-nativo estrategia=js-iso resultado=SUCESSO valor={}", execId, isoValue);
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("[datepicker][execId={}] JS direto falhou: {}", execId, e.getMessage());
        }

        try {
            element.click();
            element.sendKeys(Keys.HOME);

            String dd = String.format("%02d", date.getDayOfMonth());
            String mm = String.format("%02d", date.getMonthValue());
            String yyyy = String.valueOf(date.getYear());

            element.sendKeys(dd);
            String partial = element.getAttribute("value");
            if (partial != null && partial.startsWith(dd)) {
                element.sendKeys(Keys.ARROW_RIGHT);
                element.sendKeys(mm);
                element.sendKeys(Keys.ARROW_RIGHT);
                element.sendKeys(yyyy);
            } else {
                element.clear();
                element.sendKeys(Keys.HOME);
                element.sendKeys(mm);
                element.sendKeys(Keys.ARROW_RIGHT);
                element.sendKeys(dd);
                element.sendKeys(Keys.ARROW_RIGHT);
                element.sendKeys(yyyy);
            }

            dispatchEvents(driver, element);

            if (validateField(element, isoValue) || validateDateInField(element, date)) {
                log.info("[datepicker][execId={}] tipo=input-date-nativo estrategia=sendkeys-segmentado resultado=SUCESSO", execId);
                return;
            }
        } catch (Exception e) {
            log.debug("[datepicker][execId={}] sendKeys segmentado falhou: {}", execId, e.getMessage());
        }

        throw new IllegalStateException(
                "[datepicker][execId=" + execId + "] Falha ao preencher input[type=date]. JS e sendKeys segmentado falharam. Valor ISO tentado: '" + isoValue + "'");
    }

    private boolean trySendKeys(WebDriver driver, WebElement element, String value, String execId) {
        try {
            element.click();
            try { element.clear(); } catch (Exception ignored) { }
            try { element.sendKeys(Keys.chord(Keys.CONTROL, "a")); } catch (Exception ignored) { }
            element.sendKeys(value);
            dispatchEvents(driver, element);
            if (validateField(element, value)) return true;
            element.sendKeys(Keys.TAB);
            return validateField(element, value);
        } catch (Exception e) {
            log.debug("[datepicker][execId={}] sendKeys — erro: {}", execId, e.getMessage());
            return false;
        }
    }

    private boolean tryJsValue(WebDriver driver, WebElement element, String value, String execId) {
        try {
            if (driver instanceof JavascriptExecutor js) {
                js.executeScript(
                        "arguments[0].value = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input', {bubbles:true}));" +
                        "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                        element, value);
                boolean ok = validateField(element, value);
                if (ok) log.info("[datepicker][execId={}] estrategia=js-value resultado=SUCESSO", execId);
                else log.warn("[datepicker][execId={}] estrategia=js-value resultado=FALHA_VALIDACAO", execId);
                return ok;
            }
        } catch (Exception e) {
            log.debug("[datepicker][execId={}] JS value — erro: {}", execId, e.getMessage());
        }
        return false;
    }

    private void runCalendarStrategy(WebDriver driver, WebElement element,
                                     String rawValue, LocalDate targetDate, String execId) {
        log.info("[datepicker][execId={}] estrategia=calendario alvo={}/{}/{}",
                execId, targetDate.getDayOfMonth(), targetDate.getMonthValue(), targetDate.getYear());

        WebElement calendar = openCalendar(driver, element, execId);

        List<WebElement> allCals = findVisibleCalendars(driver);
        if (allCals.size() >= 2) {
            log.info("[datepicker][execId={}] Range detectado — {} calendarios no DOM", execId, allCals.size());
            calendar = resolveCalendarForField(driver, element, allCals, execId);
        }

        navigateToYearMonth(driver, calendar, targetDate, execId);
        selectDay(driver, calendar, targetDate, execId);
        fecharCalendarioResidual(driver);

        boolean validated = validateField(element, rawValue) || validateDateInField(element, targetDate);
        if (!validated) {
            log.warn("[datepicker][execId={}] Validacao pos-selecao falhou — tentando JS fallback", execId);
            if (!tryJsValue(driver, element, rawValue, execId)) {
                throw new IllegalStateException(
                        "[datepicker][execId=" + execId + "] Data selecionada mas valor do campo nao corresponde. Esperado: '" + rawValue + "'");
            }
        }

        log.info("[datepicker][execId={}] estrategia=calendario resultado=SUCESSO", execId);
    }

    private WebElement openCalendar(WebDriver driver, WebElement element, String execId) {
        log.info("[datepicker][execId={}] Abrindo calendario", execId);

        try { element.click(); } catch (Exception ignored) { }

        if (isCalendarVisible(driver)) {
            return findVisibleCalendars(driver).get(0);
        }

        WebElement trigger = findCalendarTrigger(driver, element);
        if (trigger != null) {
            try {
                trigger.click();
                log.debug("[datepicker][execId={}] Clique no trigger associado ao campo", execId);
            } catch (Exception ignored) { }
        }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(5)).until(this::isCalendarVisible);
        } catch (TimeoutException e) {
            throw new DatePickerCalendarNotOpenedException(
                    "Calendario nao ficou visivel apos clicar no campo. execId=" + execId);
        }

        List<WebElement> cals = findVisibleCalendars(driver);
        if (cals.isEmpty()) {
            throw new DatePickerCalendarNotOpenedException(
                    "Nenhum conteiner de calendario visivel no DOM. execId=" + execId);
        }
        return cals.get(0);
    }

    private WebElement findCalendarTrigger(WebDriver driver, WebElement element) {
        try {
            return element.findElement(By.xpath(
                    "./following-sibling::*[contains(@class,'icon') or contains(@class,'btn') or contains(@class,'trigger') or contains(@class,'toggle') or @type='button'][1]"));
        } catch (Exception ignored) { }
        try {
            return element.findElement(By.xpath(
                    "./parent::*//button[contains(@class,'calendar') or contains(@class,'date')] | ./parent::*//*[@data-toggle='datepicker']"));
        } catch (Exception ignored) { }
        try {
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                return driver.findElement(By.cssSelector(
                        "[aria-controls='" + id + "'], button[data-input='" + id + "'], [data-target='#" + id + "']"));
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean isCalendarVisible(WebDriver driver) {
        return !findVisibleCalendars(driver).isEmpty();
    }

    private List<WebElement> findVisibleCalendars(WebDriver driver) {
        try {
            List<WebElement> found = driver.findElements(By.cssSelector(CALENDAR_SELECTOR));
            List<WebElement> filtered = new ArrayList<>();

            for (WebElement el : found) {
                try {
                    if (el.isDisplayed() && isCalendarioReal(el)) {
                        filtered.add(el);
                    }
                } catch (Exception ignored) { }
            }
            return filtered;
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isCalendarioReal(WebElement el) {
        try {
            String html = Optional.ofNullable(el.getAttribute("outerHTML")).orElse("").toLowerCase(Locale.ROOT);
            String text = Optional.ofNullable(el.getText()).orElse("").toLowerCase(Locale.ROOT);
            String cls = Optional.ofNullable(el.getAttribute("class")).orElse("").toLowerCase(Locale.ROOT);

            boolean temAssinaturaCalendario =
                    html.contains("datepicker") ||
                    html.contains("calendar") ||
                    html.contains("pickers") ||
                    cls.contains("datepicker") ||
                    cls.contains("calendar") ||
                    cls.contains("pickers") ||
                    text.contains("janeiro") || text.contains("fevereiro") || text.contains("março") || text.contains("marco") ||
                    text.contains("january") || text.contains("february");

            boolean pareceChat =
                    html.contains("whatsapp") ||
                    html.contains("central de atendimento") ||
                    html.contains("canais de contato") ||
                    html.contains("fale sobre seguros") ||
                    cls.contains("chat");

            return temAssinaturaCalendario && !pareceChat;
        } catch (Exception e) {
            return false;
        }
    }

    private void fecharCalendarioResidual(WebDriver driver) {
        try {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        } catch (Exception ignored) { }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(3)).until(d -> findVisibleCalendars(d).isEmpty());
        } catch (Exception ignored) { }
    }

    private WebElement resolveCalendarForField(WebDriver driver, WebElement field,
                                               List<WebElement> calendars, String execId) {
        try {
            String controls = field.getAttribute("aria-controls");
            if (controls != null) {
                for (WebElement cal : calendars) {
                    if (controls.equals(cal.getAttribute("id"))) {
                        log.info("[datepicker][execId={}] Range: calendario resolvido via aria-controls='{}'", execId, controls);
                        return cal;
                    }
                }
            }
        } catch (Exception ignored) { }

        try {
            String cls = field.getAttribute("class");
            String name = field.getAttribute("name");
            boolean isEnd = (cls != null && (cls.contains("end") || cls.contains("fim")))
                    || (name != null && (name.contains("end") || name.contains("fim")));
            WebElement chosen = isEnd ? calendars.get(1) : calendars.get(0);
            log.info("[datepicker][execId={}] Range: calendario por posicao (isEnd={})", execId, isEnd);
            return chosen;
        } catch (Exception ignored) { }

        return calendars.get(0);
    }

    private void navigateToYearMonth(WebDriver driver, WebElement calendar, LocalDate target, String execId) {
        boolean yearHandled = tryNavigateYear(driver, calendar, target.getYear(), execId);
        navigateMonth(driver, calendar, target, execId);
        if (!yearHandled) {
            log.debug("[datepicker][execId={}] Ano navegado via botoes next/prev junto com mes", execId);
        }
    }

    private boolean tryNavigateYear(WebDriver driver, WebElement calendar, int targetYear, String execId) {
        try {
            List<WebElement> yearEls = searchIn(calendar, driver, YEAR_SELECT_SELECTOR);
            if (!yearEls.isEmpty()) {
                WebElement yearEl = yearEls.get(0);
                String tag = yearEl.getTagName();
                if ("select".equalsIgnoreCase(tag)) {
                    new Select(yearEl).selectByValue(String.valueOf(targetYear));
                    log.info("[datepicker][execId={}] navegacao=ano via=select valor={}", execId, targetYear);
                    return true;
                }
                if ("input".equalsIgnoreCase(tag)) {
                    yearEl.clear();
                    yearEl.sendKeys(String.valueOf(targetYear));
                    yearEl.sendKeys(Keys.ENTER);
                    log.info("[datepicker][execId={}] navegacao=ano via=input-numerico valor={}", execId, targetYear);
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("[datepicker][execId={}] Seletor de ano falhou: {}", execId, e.getMessage());
        }

        try {
            List<WebElement> periodBtns = searchIn(calendar, driver, PERIOD_BTN_SELECTOR);
            if (!periodBtns.isEmpty() && periodBtns.get(0).isDisplayed()) {
                periodBtns.get(0).click();
                pause(400);
                List<WebElement> yearCells = driver.findElements(By.cssSelector(YEAR_CELL_SELECTOR));
                for (WebElement yc : yearCells) {
                    if (String.valueOf(targetYear).equals(yc.getText().trim())) {
                        yc.click();
                        log.info("[datepicker][execId={}] navegacao=ano via=year-view valor={}", execId, targetYear);
                        pause(400);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[datepicker][execId={}] Year view falhou: {}", execId, e.getMessage());
        }

        return false;
    }

    private void navigateMonth(WebDriver driver, WebElement calendar, LocalDate target, String execId) {
        final int MAX_ITERS = 36;
        for (int i = 0; i < MAX_ITERS; i++) {
            int[] current = detectCurrentMonthYear(driver, calendar);
            if (current == null) break;

            int curMonth = current[0];
            int curYear = current[1];

            if (curMonth == target.getMonthValue() && curYear == target.getYear()) {
                log.info("[datepicker][execId={}] navegacao=mes resultado=OK mes={} ano={}", execId, target.getMonthValue(), target.getYear());
                return;
            }

            boolean goNext = isTargetAfterCurrent(target, curMonth, curYear);

            clickNavigationButton(driver, calendar,
                    goNext ? NEXT_BTN_SELECTOR : PREV_BTN_SELECTOR,
                    goNext ? "next" : "prev", execId);
            pause(250);
        }
    }

    private int[] detectCurrentMonthYear(WebDriver driver, WebElement calendar) {
        int[] fromSelects = tryReadFromSelects(calendar, driver);
        if (fromSelects != null) return fromSelects;

        try {
            List<WebElement> headers = searchIn(calendar, driver, HEADER_SELECTOR);
            for (WebElement h : headers) {
                if (!h.isDisplayed()) continue;
                String text = h.getText().trim().toLowerCase(Locale.ROOT);
                if (text.isBlank()) continue;
                int[] parsed = parseMonthYearText(text);
                if (parsed != null) return parsed;
            }
            try {
                String combined = calendar.findElement(
                        By.cssSelector("[class*='header'], [class*='title'], [class*='caption']"))
                        .getText().trim().toLowerCase(Locale.ROOT);
                return parseMonthYearText(combined);
            } catch (Exception ignored) { }
        } catch (Exception ignored) { }

        return null;
    }

    private int[] tryReadFromSelects(WebElement calendar, WebDriver driver) {
        try {
            List<WebElement> monthSels = searchIn(calendar, driver, MONTH_SELECT_SELECTOR);
            List<WebElement> yearSels = searchIn(calendar, driver, YEAR_SELECT_SELECTOR);
            if (!monthSels.isEmpty() && !yearSels.isEmpty()) {
                int rawMonth = Integer.parseInt(
                        new Select(monthSels.get(0)).getFirstSelectedOption().getAttribute("value").trim());
                int year = Integer.parseInt(
                        new Select(yearSels.get(0)).getFirstSelectedOption().getAttribute("value").trim());
                int month = (rawMonth == 0 || rawMonth > 12) ? rawMonth + 1 : rawMonth;
                return new int[]{month, year};
            }
        } catch (Exception ignored) { }
        return null;
    }

    private int[] parseMonthYearText(String text) {
        Matcher yearMatcher = YEAR_PATTERN.matcher(text);
        int year = -1;
        if (yearMatcher.find()) year = Integer.parseInt(yearMatcher.group(1));

        for (Map.Entry<String, Integer> entry : MONTH_NAMES.entrySet()) {
            if (text.contains(entry.getKey()) && year > 0) {
                return new int[]{entry.getValue(), year};
            }
        }

        Matcher numMatcher = MM_YYYY_PATTERN.matcher(text);
        if (numMatcher.find()) {
            return new int[]{Integer.parseInt(numMatcher.group(1)), Integer.parseInt(numMatcher.group(2))};
        }
        return null;
    }

    private boolean isTargetAfterCurrent(LocalDate target, int curMonth, int curYear) {
        if (target.getYear() != curYear) return target.getYear() > curYear;
        return target.getMonthValue() > curMonth;
    }

    private void selectDay(WebDriver driver, WebElement calendar, LocalDate target, String execId) {
        String dayStr = String.valueOf(target.getDayOfMonth());

        List<WebElement> allDays = findDayCells(driver, calendar, DAY_ALL_SELECTOR);
        boolean dayPresent = allDays.stream().anyMatch(d -> matchesDay(d, dayStr));

        if (dayPresent) {
            List<WebElement> enabledDays = findDayCells(driver, calendar, DAY_ENABLED_SELECTOR);
            Optional<WebElement> dayCell = enabledDays.stream()
                    .filter(d -> matchesDay(d, dayStr)).findFirst();

            if (dayCell.isPresent()) {
                clickDay(driver, dayCell.get(), dayStr, execId);
                return;
            }

            throw new DatePickerDateDisabledException(
                    "Data " + target + " esta desabilitada no calendario. execId=" + execId);
        }

        throw new DatePickerDateNotFoundException(
                "Dia " + dayStr + " nao encontrado no calendario. Data alvo: " + target + ". execId=" + execId);
    }

    private void clickDay(WebDriver driver, WebElement cell, String dayStr, String execId) {
        try {
            cell.click();
            log.info("[datepicker][execId={}] Dia {} clicado", execId, dayStr);
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cell);
                log.info("[datepicker][execId={}] Dia {} clicado via JS", execId, dayStr);
            } catch (Exception e2) {
                throw new DatePickerDateNotFoundException(
                        "Clique no dia " + dayStr + " falhou. execId=" + execId, e2);
            }
        }
    }

    private List<WebElement> findDayCells(WebDriver driver, WebElement calendar, String selector) {
        try {
            List<WebElement> cells = calendar.findElements(By.cssSelector(selector));
            if (cells.isEmpty()) cells = driver.findElements(By.cssSelector(selector));
            return cells;
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean matchesDay(WebElement cell, String dayStr) {
        try {
            if (dayStr.equals(cell.getText().trim())) return true;
            if (dayStr.equals(cell.getAttribute("data-day"))) return true;
            String dataDate = cell.getAttribute("data-date");
            if (dataDate != null) {
                String padded = dayStr.length() == 1 ? "0" + dayStr : dayStr;
                if (dataDate.endsWith("-" + padded) || dataDate.endsWith("-" + dayStr)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isReadonly(WebElement element) {
        try {
            if (element.getAttribute("readonly") != null) return true;
            if ("true".equals(element.getAttribute("aria-readonly"))) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value.trim(), fmt);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private void dispatchEvents(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('input', {bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                    element);
        } catch (Exception ignored) { }
    }

    private boolean validateField(WebElement element, String expectedValue) {
        try {
            String current = element.getAttribute("value");
            if (current == null || current.isBlank()) current = element.getText();
            if (current == null || current.isBlank()) return false;
            String a = current.trim();
            String b = expectedValue.trim();
            return a.equals(b) || a.contains(b) || b.contains(a);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateDateInField(WebElement element, LocalDate target) {
        try {
            String current = element.getAttribute("value");
            if (current == null) current = element.getText();
            if (current == null || current.isBlank()) return false;
            return target.equals(parseDate(current.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    private List<WebElement> searchIn(WebElement calendar, WebDriver driver, String cssSelector) {
        try {
            List<WebElement> found = calendar.findElements(By.cssSelector(cssSelector));
            if (!found.isEmpty()) return found;
        } catch (Exception ignored) { }
        try {
            return driver.findElements(By.cssSelector(cssSelector));
        } catch (Exception e) {
            return List.of();
        }
    }

    private void clickNavigationButton(WebDriver driver, WebElement calendar,
                                       String selector, String label, String execId) {
        List<WebElement> btns = searchIn(calendar, driver, selector);
        for (WebElement btn : btns) {
            try {
                if (btn.isDisplayed() && btn.isEnabled()) {
                    btn.click();
                    log.debug("[datepicker][execId={}] navegacao=mes botao={}", execId, label);
                    return;
                }
            } catch (Exception ignored) { }
        }
        log.warn("[datepicker][execId={}] Botao de navegacao '{}' nao encontrado ou nao clicavel", execId, label);
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}