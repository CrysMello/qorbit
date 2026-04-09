package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.exception.DatePickerCalendarNotOpenedException;
import com.qorbit.engine.exception.DatePickerDateDisabledException;
import com.qorbit.engine.exception.DatePickerDateNotFoundException;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

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

    private void trySelecionarDiaNoCalendario(WebDriver driver, String value) {
        String day = extrairDia(value);
        if (day == null) {
            return;
        }

        List<By> enabledLocators = List.of(
                By.xpath("//*[self::td or self::button or self::div or self::span][normalize-space(text())='" + day + "' and not(contains(@class,'disabled')) and not(@disabled)]"),
                By.cssSelector(".ui-datepicker-calendar td a, .flatpickr-day, .react-datepicker__day, .mat-calendar-body-cell-content, .MuiPickersDay-root, .pika-button")
        );

        for (By locator : enabledLocators) {
            try {
                List<WebElement> candidates = driver.findElements(locator);
                for (WebElement candidate : candidates) {
                    if (!candidate.isDisplayed() || !candidate.isEnabled()) {
                        continue;
                    }
                    String text = safe(candidate.getText());
                    String aria = safe(candidate.getAttribute("aria-label"));
                    if (!day.equals(text.trim()) && !aria.contains(day)) {
                        continue;
                    }
                    scrollCentralizado(driver, candidate);
                    candidate.click();
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        if (!driver.findElements(By.cssSelector(CALENDAR_SELECTOR)).isEmpty()) {
            throw new DatePickerDateNotFoundException("Não foi possível localizar a data no calendário: " + value);
        }

        throw new DatePickerCalendarNotOpenedException("O calendário não abriu para seleção da data: " + value);
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

    private String extrairDia(String value) {
        String[] parts = value.split("[/.-]");
        if (parts.length == 0) {
            return null;
        }
        String first = parts[0].trim();
        if (first.isEmpty()) {
            return null;
        }
        try {
            return String.valueOf(Integer.parseInt(first));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizar(String value) {
        return safe(value).trim().replace(" ", "").toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
