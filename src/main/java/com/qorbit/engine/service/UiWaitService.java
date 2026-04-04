package com.qorbit.engine.service;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class UiWaitService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(12);

    public void aguardarPaginaPronta(WebDriver driver) {
        new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
            Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
            return "complete".equals(String.valueOf(state)) || "interactive".equals(String.valueOf(state));
        });
    }

    public void aguardarUiEstavel(WebDriver driver) {
        new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
            Object stable = ((JavascriptExecutor) d).executeScript("""
                const loading = document.querySelector('[aria-busy="true"], .loading, .loader, .spinner, .ant-spin-spinning, .MuiCircularProgress-root, .mat-mdc-progress-spinner, .ngx-spinner-overlay');
                if (loading) return false;
                if (document.readyState !== 'complete' && document.readyState !== 'interactive') return false;
                const animations = document.getAnimations ? document.getAnimations().filter(a => a.playState === 'running') : [];
                return animations.length === 0;
            """);
            return Boolean.TRUE.equals(stable);
        });
    }

    public void aguardarComponenteEstavel(WebDriver driver, String tipoComponente) {
        try {
            switch ((tipoComponente == null ? "" : tipoComponente).toUpperCase()) {
                case "CUSTOM_SELECT", "AUTOCOMPLETE" -> new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
                    Object ready = ((JavascriptExecutor) d).executeScript("""
                        const opened = document.querySelector('[aria-expanded="true"], [role="listbox"], .select__menu, .dropdown-menu.show, .MuiAutocomplete-popper, .mat-mdc-autocomplete-panel');
                        return !!opened || document.readyState === 'complete' || document.readyState === 'interactive';
                    """);
                    return Boolean.TRUE.equals(ready);
                });
                case "DATEPICKER" -> new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
                    Object ready = ((JavascriptExecutor) d).executeScript("""
                        const calendar = document.querySelector('[role="dialog"] [role="grid"], .datepicker, .flatpickr-calendar, .react-datepicker, .MuiPickersPopper-root, .mat-datepicker-content');
                        return !!calendar || document.readyState === 'complete' || document.readyState === 'interactive';
                    """);
                    return Boolean.TRUE.equals(ready);
                });
                case "MODAL_ACTION", "MODAL_FIELD", "MODAL_CONTAINER" -> new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
                    Object ready = ((JavascriptExecutor) d).executeScript("""
                        const modal = document.querySelector('[role="dialog"], [role="alertdialog"], [aria-modal="true"], .modal.show, .MuiDialog-root, .ant-modal-root');
                        return !!modal || document.readyState === 'complete' || document.readyState === 'interactive';
                    """);
                    return Boolean.TRUE.equals(ready);
                });
                default -> aguardarUiEstavel(driver);
            }
        } catch (TimeoutException ignored) {
            aguardarUiEstavel(driver);
        }
    }

    public void aguardarElementoClicavel(WebDriver driver, WebElement element) {
        try {
            new WebDriverWait(driver, DEFAULT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(element));
        } catch (Exception ignored) {
        }
    }

    public void aguardarFramesProntos(WebDriver driver) {
        try {
            new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
                Object count = ((JavascriptExecutor) d).executeScript("return document.querySelectorAll('iframe,frame').length;");
                return count != null;
            });
        } catch (Exception ignored) {
        }
    }

    public void pequenaPausa() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
