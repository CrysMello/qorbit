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
        if ("input".equals(tag) && "date".equals(type)) return true;
        if (placeholder.contains("dd/mm") || placeholder.contains("mm/yyyy") || placeholder.contains("aaaa")) return true;
        if (classes.contains("date") || classes.contains("calendar") || classes.contains("datepicker")) return true;
        if (ariaHasPopup.contains("dialog") || ariaHasPopup.contains("grid")) return true;
        if ("readonly".equals(readonly) && (classes.contains("date") || inputMode.contains("numeric"))) return true;
        if (driver instanceof JavascriptExecutor js) {
            try {
                Object result = js.executeScript("""
                        const el = arguments[0];
                        const hasCalendarIcon = !!(el.closest('.datepicker, .date-picker, .calendar')
                            || el.parentElement?.querySelector('[class*=calendar], [class*=date], .fa-calendar, .bi-calendar'));
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
        if (role.contains("combobox") || ariaHasPopup.contains("listbox")) return true;
        if (classes.contains("select2") || classes.contains("dropdown") || classes.contains("combo")) return true;
        if ("input".equals(tag) && !ariaExpanded.isBlank() && !ariaHasPopup.isBlank()) return true;
        if (driver instanceof JavascriptExecutor js) {
            try {
                Object result = js.executeScript("""
                        const el = arguments[0];
                        const cls = (el.className || '').toString().toLowerCase();
                        const parent = el.closest('[role=combobox], .select2, .dropdown, .combo, .choices, .multiselect');
                        return !!parent || cls.includes('select2') || cls.includes('dropdown') || cls.includes('combo');
                        """, element);
                return Boolean.TRUE.equals(result);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
