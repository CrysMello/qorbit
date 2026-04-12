package com.qorbit.engine.classification;

import com.qorbit.engine.model.StepTeste;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DefaultComponentClassifier implements ComponentClassifier {

    @Override
    public ComponentClassification classify(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) {
            return ComponentClassification.undefined("elemento ausente");
        }

        String tag = safe(element.getTagName());
        String type = safe(element.getAttribute("type"));
        String role = safe(element.getAttribute("role"));
        String placeholder = safe(element.getAttribute("placeholder"));
        String classes = safe(element.getAttribute("class"));
        String ariaHasPopup = safe(element.getAttribute("aria-haspopup"));
        String ariaExpanded = safe(element.getAttribute("aria-expanded"));
        String readonly = safe(element.getAttribute("readonly"));
        String inputMode = safe(element.getAttribute("inputmode"));
        String action = step != null && step.getAcao() != null ? step.getAcao().toUpperCase(Locale.ROOT) : "";

        List<String> evidences = new ArrayList<>();
        evidences.add("tag=" + tag);
        if (!type.isBlank()) evidences.add("type=" + type);
        if (!role.isBlank()) evidences.add("role=" + role);
        if (!placeholder.isBlank()) evidences.add("placeholder=" + placeholder);

        if ("select".equals(tag)) {
            return new ComponentClassification(ComponentType.NATIVE_SELECT, 0.95, evidences);
        }
        if ("textarea".equals(tag)) {
            return new ComponentClassification(ComponentType.TEXTAREA, 0.95, evidences);
        }
        if ("input".equals(tag) && "checkbox".equals(type)) {
            return new ComponentClassification(ComponentType.CHECKBOX, 0.98, evidences);
        }
        if ("input".equals(tag) && "radio".equals(type)) {
            return new ComponentClassification(ComponentType.RADIO, 0.98, evidences);
        }
        if ("button".equals(tag) || "button".equals(role) || type.equals("button") || type.equals("submit")) {
            return new ComponentClassification(ComponentType.BUTTON, 0.92, evidences);
        }
        if ("a".equals(tag) && !(role.contains("combobox") || ariaHasPopup.contains("listbox"))) {
            return new ComponentClassification(ComponentType.LINK, 0.90, evidences);
        }

        if (isDatePicker(driver, element, tag, type, placeholder, classes, ariaHasPopup, readonly, inputMode)) {
            evidences.add("heuristica=datepicker");
            return new ComponentClassification(ComponentType.DATEPICKER, 0.86, evidences);
        }
        if (isComboBox(driver, element, tag, role, classes, ariaHasPopup, ariaExpanded)) {
            evidences.add("heuristica=combobox");
            return new ComponentClassification(ComponentType.COMBOBOX, 0.84, evidences);
        }
        if ("input".equals(tag)) {
            return new ComponentClassification(ComponentType.TEXT_INPUT, 0.90, evidences);
        }
        if (action.contains("CLICAR") || action.contains("CLICK")) {
            evidences.add("fallback=generic-click");
            return new ComponentClassification(ComponentType.GENERIC, 0.55, evidences);
        }
        return new ComponentClassification(ComponentType.UNDEFINED, 0.30, evidences);
    }

    private boolean isDatePicker(WebDriver driver,
                                 WebElement element,
                                 String tag,
                                 String type,
                                 String placeholder,
                                 String classes,
                                 String ariaHasPopup,
                                 String readonly,
                                 String inputMode) {

        // ── Tipo HTML nativo de data ──────────────────────────────────────────
        if ("input".equals(tag) && ("date".equals(type) || "datetime-local".equals(type))) return true;

        // ── Placeholder com padrão de data (português e inglês) ──────────────
        if (placeholder.contains("dd/mm") || placeholder.contains("mm/dd")
                || placeholder.contains("mm/yyyy") || placeholder.contains("dd/yyyy")
                || placeholder.contains("yyyy") || placeholder.contains("aaaa")
                || placeholder.contains("date") || placeholder.contains("data")) return true;

        // ── Classes CSS explícitas de datepicker ──────────────────────────────
        // "hasdatepicker" = classe adicionada pelo jQuery UI ao inicializar
        if (classes.contains("date") || classes.contains("calendar")
                || classes.contains("datepicker") || classes.contains("hasdatepicker")
                || classes.contains("date-input") || classes.contains("date-field")) return true;

        // ── ARIA attributes ───────────────────────────────────────────────────
        if (ariaHasPopup.contains("dialog") || ariaHasPopup.contains("grid")) return true;

        // ── Data attributes comuns de datepicker (Bootstrap, jQuery plugins) ──
        try {
            String dataDatepicker = safe(element.getAttribute("data-datepicker"));
            String dataProvide    = safe(element.getAttribute("data-provide"));
            String dataDate       = safe(element.getAttribute("data-date"));
            String dataTarget     = safe(element.getAttribute("data-target"));
            if (!dataDatepicker.isBlank() || dataProvide.contains("datepicker")
                    || !dataDate.isBlank() || dataTarget.contains("datepick")) return true;
        } catch (Exception ignored) {}

        // ── Readonly + indicador numérico ─────────────────────────────────────
        if ("readonly".equals(readonly) && (classes.contains("date") || inputMode.contains("numeric"))) return true;

        // ── Heurística JavaScript (estrutura DOM e inicialização jQuery UI) ───
        if (driver instanceof JavascriptExecutor js) {
            try {
                Object result = js.executeScript("""
                        const el = arguments[0];
                        const cls = (el.className || '').toString().toLowerCase();

                        // jQuery UI: adiciona 'hasDatepicker' ao input ao inicializar
                        if (cls.includes('hasdatepicker') || cls.includes('datepicker')) return true;

                        // jQuery UI: verifica se o datepicker está vinculado via $.data
                        if (window.$ && window.$.data) {
                            try { if (window.$.data(el, 'datepicker')) return true; } catch(e) {}
                        }

                        // jQuery UI: o div #ui-datepicker-div existe E o input tem id referenciado
                        if (document.querySelector('#ui-datepicker-div') && el.id) return true;

                        // Bootstrap datepicker: data-provide ou data-datepicker
                        const dp = (el.getAttribute('data-provide') || '').toLowerCase();
                        if (dp.includes('datepicker')) return true;

                        // Placeholder com padrão de data
                        const ph = (el.getAttribute('placeholder') || '').toLowerCase();
                        if (ph.includes('dd/') || ph.includes('/yyyy') || ph.includes('/aaaa')
                                || ph.includes('date') || ph.includes('data')) return true;

                        // Ícone de calendário no parent imediato ou container próximo
                        const hasCalendarIcon = !!(
                            el.closest('.datepicker, .date-picker, .calendar, [data-datepicker], [data-provide=datepicker]')
                            || el.parentElement?.querySelector(
                                '[class*=calendar], [class*=date-icon], .fa-calendar, '
                                + '.bi-calendar, .glyphicon-calendar, [data-toggle=datepicker]'
                            )
                        );

                        const aria = (el.getAttribute('aria-haspopup') || '').toLowerCase();
                        return hasCalendarIcon || aria === 'dialog' || aria === 'grid';
                        """, element);
                return Boolean.TRUE.equals(result);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean isComboBox(WebDriver driver,
                               WebElement element,
                               String tag,
                               String role,
                               String classes,
                               String ariaHasPopup,
                               String ariaExpanded) {
        // ===== CRITÉRIO 1: NUNCA retorna true para <select> nativo =====
        // <select> é SEMPRE classificado como NATIVE_SELECT, nunca como COMBOBOX
        if ("select".equals(tag)) return false;
        
        // ===== CRITÉRIO 2: CUSTOM COMBOBOX - Padrões explícitos =====
        // Critério 2.1: ARIA attributes indicam combobox customizado
        if (role.contains("combobox")) return true;  // role="combobox" é definitivo
        if (ariaHasPopup.contains("listbox")) return true;  // aria-haspopup="listbox" é definitivo
        
        // Critério 2.2: Classes CSS indicam bibliotecas de select customizado
        if (classes.contains("select2") || 
            classes.contains("dropdown") || 
            classes.contains("combo") ||
            classes.contains("multiselect") ||
            classes.contains("choices") ||
            classes.contains("selectpicker")) return true;
        
        // Critério 2.3: <input> com ambos aria-haspopup e aria-expanded (combobox customizado)
        if ("input".equals(tag) && !ariaExpanded.isBlank() && !ariaHasPopup.isBlank()) return true;
        
        // ===== CRITÉRIO 3: JavaScript - Verificação dinâmica de estrutura DOM =====
        if (driver instanceof JavascriptExecutor js) {
            try {
                Object result = js.executeScript("""
                        const el = arguments[0];
                        const cls = (el.className || '').toString().toLowerCase();
                        
                        // Verifica se está dentro de um container de combobox customizado
                        const parent = el.closest('[role=combobox], .select2, .dropdown, .combo, .choices, .multiselect, .selectpicker');
                        const hasComboboxParent = !!parent;
                        
                        // Verifica se tem lista/popup associada
                        const hasListbox = !!(el.getAttribute('aria-controls') && 
                                              document.getElementById(el.getAttribute('aria-controls')));
                        
                        // Verifica padrões de combobox customizado
                        const hasComboboxClass = cls.includes('select2') || 
                                                 cls.includes('dropdown') || 
                                                 cls.includes('combo') ||
                                                 cls.includes('multiselect');
                        
                        return hasComboboxParent || hasListbox || hasComboboxClass;
                        """, element);
                return Boolean.TRUE.equals(result);
            } catch (Exception ignored) {
            }
        }
        
        // ===== FALLBACK: Se não se encaixa em nenhum critério, NÃO é combobox =====
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
