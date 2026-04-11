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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ========== CALENDÁRIO: DateInputExecutionStrategy ==========
@Component
public class DateInputExecutionStrategy implements ExecutionStrategy {

    private static final String CALENDAR_SELECTOR =
            ".ui-datepicker, .flatpickr-calendar.open, .react-datepicker, .react-datepicker-popper, " +
            ".mat-datepicker-content, .MuiPickersPopper-root, .MuiPickersLayout-root, " +
            ".ant-picker-dropdown:not(.ant-picker-dropdown-hidden), .pika-single, " +
            ".daterangepicker.show-calendar, [role='dialog'] [role='grid']";

    @Override
    public StrategyType type() {
        return StrategyType.DATE_INPUT;
    }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            throw new IllegalArgumentException("Elemento não encontrado para preenchimento de data");
        }

        String value = step.getValor() != null ? step.getValor().trim() : "";
        if (value.isBlank()) {
            throw new IllegalArgumentException("Valor da data não informado no step");
        }

        abrirCalendarioSePossivel(driver, element);

        if (calendarioVisivel(driver)) {
            trySelecionarDiaNoCalendario(driver, value);
        }

        aplicarValorNoCampo(driver, element, value);
        validarValor(element, value);
    }

    private void abrirCalendarioSePossivel(WebDriver driver, WebElement element) {
        scrollCentralizado(driver, element);
        try {
            element.click();
        } catch (Exception clickError) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception jsError) {
                // o campo ainda pode aceitar digitação direta; segue o fluxo
            }
        }
    }

    private boolean calendarioVisivel(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
            wait.until(d -> !d.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ========== ⭐ NOVO: SELEÇÃO COM NAVEGAÇÃO ANO/MÊS/DIA ==========
    private void trySelecionarDiaNoCalendario(WebDriver driver, String value) {
        DateParts parts = extrairPartesDaData(value);
        if (parts == null) {
            return;
        }

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
            wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(
                By.cssSelector(CALENDAR_SELECTOR)
            ));

            navegarAteMesAno(driver, parts.month, parts.year);
            clicarNoDia(driver, parts.day);
            aguardarFechamentoCalendario(driver);

        } catch (Exception e) {
            if (!driver.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty()) {
                throw new DatePickerDateNotFoundException(
                    "Erro ao selecionar data no calendário: " + value + " | " + e.getMessage()
                );
            }
            throw new DatePickerCalendarNotOpenedException(
                "O calendário não abriu: " + value
            );
        }
    }

    // ========== ⭐ EXTRAIR PARTES DA DATA (DD/MM/YYYY e YYYY-MM-DD) ==========
    private DateParts extrairPartesDaData(String value) {
        String[] parts = value.split("[/.-]");
        if (parts.length < 3) {
            return null;
        }

        try {
            String first = parts[0].trim();
            String second = parts[1].trim();
            String third = parts[2].trim();

            int day, month, year;

            if (third.length() == 4) {
                day = Integer.parseInt(first);
                month = Integer.parseInt(second);
                year = Integer.parseInt(third);
            } else {
                year = Integer.parseInt(first);
                month = Integer.parseInt(second);
                day = Integer.parseInt(third);
            }

            return new DateParts(day, month, year);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ========== ⭐ NAVEGAR ATÉ MÊS/ANO CORRETO ==========
    private void navegarAteMesAno(WebDriver driver, int targetMonth, int targetYear) {
        int maxAttempts = 24;
        int attempts = 0;

        while (attempts < maxAttempts) {
            try {
                MesAnoAtual atual = obterMesAnoAtual(driver);

                if (atual == null) {
                    break;
                }

                if (atual.month == targetMonth && atual.year == targetYear) {
                    return;
                }

                if (atual.year < targetYear || 
                    (atual.year == targetYear && atual.month < targetMonth)) {
                    clicarProximo(driver);
                } else {
                    clicarAnterior(driver);
                }

                attempts++;
                Thread.sleep(300);

            } catch (InterruptedException ignored) {
            }
        }
    }

    // ========== ⭐ OBTER MÊS/ANO ATUAL DO CALENDÁRIO (LÊ HEADER) ==========
    private MesAnoAtual obterMesAnoAtual(WebDriver driver) {
        try {
            By headerLocator = By.xpath(
                "//*[contains(@class,'datepicker-header') or " +
                "contains(@class,'pika-title') or " +
                "contains(@class,'flatpickr-monthDropdown-months') or " +
                "contains(@class,'react-datepicker__current-month') or " +
                "contains(@class,'mat-calendar-body-label')]"
            );

            List<WebElement> headers = driver.findElements(headerLocator);
            for (WebElement header : headers) {
                String text = header.getText();
                if (!text.isEmpty()) {
                    return parseHeaderText(text);
                }
            }

            List<WebElement> dataElements = driver.findElements(
                By.xpath("//*[@data-month or @data-year]")
            );
            for (WebElement el : dataElements) {
                String month = el.getAttribute("data-month");
                String year = el.getAttribute("data-year");
                if (month != null && year != null) {
                    try {
                        return new MesAnoAtual(Integer.parseInt(month), Integer.parseInt(year));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    // ========== ⭐ PARSEAR HEADER DO CALENDÁRIO (CONVERTER "December 2025") ==========
    private MesAnoAtual parseHeaderText(String text) {
        String[] parts = text.split(" ");
        if (parts.length >= 2) {
            try {
                String monthStr = parts[0].toLowerCase();
                int year = Integer.parseInt(parts[parts.length - 1]);
                int month = monthFromName(monthStr);
                if (month > 0) {
                    return new MesAnoAtual(month, year);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // ========== ⭐ CONVERTER NOME DO MÊS PARA NÚMERO ==========
    private int monthFromName(String name) {
        String[] months = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
        };
        for (int i = 0; i < months.length; i++) {
            if (months[i].startsWith(name)) {
                return i + 1;
            }
        }
        return -1;
    }

    // ========== ⭐ CLICAR PRÓXIMO MÊS ==========
    private void clicarProximo(WebDriver driver) {
        List<By> nextLocators = List.of(
            By.xpath("//button[contains(@class,'next') or contains(@aria-label,'next') or contains(@aria-label,'Next')]"),
            By.cssSelector(".pika-next, .flatpickr-next-month, .react-datepicker__navigation--next"),
            By.xpath("//a[contains(@class,'ui-datepicker-next')]"),
            By.xpath("//button[contains(text(),'>') or contains(text(),'→')]")
        );

        for (By locator : nextLocators) {
            try {
                WebElement btn = driver.findElement(locator);
                if (btn.isDisplayed()) {
                    btn.click();
                    return;
                }
            } catch (NoSuchElementException ignored) {
            }
        }

        throw new DatePickerCalendarNotOpenedException("Não encontrou botão 'Próximo' no calendário");
    }

    // ========== ⭐ CLICAR MÊS ANTERIOR ==========
    private void clicarAnterior(WebDriver driver) {
        List<By> prevLocators = List.of(
            By.xpath("//button[contains(@class,'prev') or contains(@aria-label,'prev') or contains(@aria-label,'Previous')]"),
            By.cssSelector(".pika-prev, .flatpickr-prev-month, .react-datepicker__navigation--previous"),
            By.xpath("//a[contains(@class,'ui-datepicker-prev')]"),
            By.xpath("//button[contains(text(),'<') or contains(text(),'←')]")
        );

        for (By locator : prevLocators) {
            try {
                WebElement btn = driver.findElement(locator);
                if (btn.isDisplayed()) {
                    btn.click();
                    return;
                }
            } catch (NoSuchElementException ignored) {
            }
        }

        throw new DatePickerCalendarNotOpenedException("Não encontrou botão 'Anterior' no calendário");
    }

    // ========== ⭐ CLICAR NO DIA ==========
    private void clicarNoDia(WebDriver driver, int day) {
        String dayStr = String.valueOf(day);
        
        List<By> dayLocators = List.of(
            By.xpath(
                "//*[self::td or self::button or self::div or self::span or self::a]" +
                "[normalize-space(text())='" + dayStr + "']" +
                "[not(contains(@class,'disabled'))]" +
                "[not(contains(@class,'unavailable'))]" +
                "[not(@disabled)]" +
                "[not(contains(@aria-disabled,'true'))]"
            ),
            By.xpath("//*[contains(@aria-label, '" + dayStr + "')]" +
                "[not(contains(@aria-disabled,'true'))]" +
                "[not(contains(@class,'disabled'))]"),
            By.xpath(
                "//*[contains(@data-date, '-" + String.format("%02d", day) + "') " +
                "or contains(@data-day, '" + dayStr + "')]" +
                "[not(contains(@class,'disabled'))]"
            ),
            By.cssSelector(".flatpickr-day:not(.disabled)"),
            By.cssSelector(".react-datepicker__day:not(.react-datepicker__day--disabled)"),
            By.cssSelector(".mat-calendar-body-cell:not(.mat-calendar-body-disabled) .mat-calendar-body-cell-content")
        );

        for (By locator : dayLocators) {
            try {
                List<WebElement> candidates = driver.findElements(locator);
                for (WebElement candidate : candidates) {
                    if (!isElementInteractable(driver, candidate)) {
                        continue;
                    }

                    String text = safe(candidate.getText()).trim();
                    String aria = safe(candidate.getAttribute("aria-label"));
                    String dataDay = safe(candidate.getAttribute("data-day"));

                    if (matchesDay(text, dayStr) || aria.contains(dayStr) || dataDay.equals(dayStr)) {
                        scrollCentralizado(driver, candidate);
                        
                        try {
                            candidate.click();
                        } catch (Exception clickError) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
                        }
                        
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                        }
                        
                        return;
                    }
                }
            } catch (NoSuchElementException ignored) {
            } catch (Exception e) {
                // Continua tentando
            }
        }

        if (!driver.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty()) {
            throw new DatePickerDateNotFoundException(
                "Não foi possível localizar/clicar o dia '" + dayStr + "' no calendário aberto"
            );
        }

        throw new DatePickerCalendarNotOpenedException(
            "O calendário não abriu ou fechou antes da seleção"
        );
    }

    // ========== ⭐ VERIFICAR SE ELEMENTO É CLICÁVEL ==========
    private boolean isElementInteractable(WebDriver driver, WebElement element) {
        try {
            if (!element.isDisplayed()) {
                return false;
            }

            if (!element.isEnabled()) {
                return false;
            }

            return (boolean) ((JavascriptExecutor) driver).executeScript(
                "const rect = arguments[0].getBoundingClientRect();" +
                "return rect.width > 0 && rect.height > 0;",
                element
            );
        } catch (Exception e) {
            return false;
        }
    }

    // ========== ⭐ COMPARAR DIA COM FLEXIBILIDADE (13 == "13") ==========
    private boolean matchesDay(String text, String day) {
        String normalized = text.trim().replace(" ", "");
        String dayNormalized = day.trim();
        
        if (normalized.equals(dayNormalized)) {
            return true;
        }
        
        try {
            return Integer.parseInt(normalized) == Integer.parseInt(dayNormalized);
        } catch (NumberFormatException ignored) {
        }
        
        return false;
    }

    // ========== ⭐ AGUARDAR FECHAMENTO DO CALENDÁRIO ==========
    private void aguardarFechamentoCalendario(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
            wait.until(d -> d.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty());
        } catch (Exception ignored) {
        }
    }

    private void aplicarValorNoCampo(WebDriver driver, WebElement element, String value) {
        try {
            element.clear();
        } catch (Exception ignored) {
        }

        try {
            element.sendKeys(value);
            dispararEventos(driver, element);
            return;
        } catch (Exception ignored) {
        }

        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].focus();" +
                    "arguments[0].value = arguments[1];" +
                    "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
                    element, value
            );
        } catch (Exception e) {
            throw new DatePickerDateDisabledException("Não foi possível aplicar a data no campo: " + value, e);
        }
    }

    private void validarValor(WebElement element, String expected) {
        String current = safe(element.getAttribute("value")).trim();
        if (current.isEmpty()) {
            current = safe(element.getText()).trim();
        }
        if (current.isEmpty()) {
            return;
        }
        if (!normalizar(current).contains(normalizar(expected)) && !normalizar(expected).contains(normalizar(current))) {
            throw new IllegalStateException("Data selecionada não confere: esperado [" + expected + "] atual [" + current + "]");
        }
    }

    private void dispararEventos(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                    "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
                    element
            );
        } catch (Exception ignored) {
        }
    }

    private void scrollCentralizado(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
                    element
            );
        } catch (Exception ignored) {
        }
    }

    private String normalizar(String value) {
        return safe(value).trim().replace(" ", "").toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // ========== CLASSES INTERNAS: CALENDÁRIO ==========
    private static class DateParts {
        int day, month, year;

        DateParts(int day, int month, int year) {
            this.day = day;
            this.month = month;
            this.year = year;
        }
    }

    private static class MesAnoAtual {
        int month, year;

        MesAnoAtual(int month, int year) {
            this.month = month;
            this.year = year;
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

