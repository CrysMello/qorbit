package com.qorbit.engine.service;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ShadowDomScannerService {

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> capturarNoContextoAtual(WebDriver driver) {
        Object raw = ((JavascriptExecutor) driver).executeScript(buildScript());
        if (raw instanceof List<?>) {
            return (List<Map<String, Object>>) raw;
        }
        return Collections.emptyList();
    }

    private String buildScript() {
        return """
            function cssEscape(value) {
                if (!value) return value;
                if (window.CSS && CSS.escape) return CSS.escape(value);
                return String(value).replace(/([ #;?%&,.+*~\\':\"!^$\\[\\]()=>|\\/])/g,'\\\\$1');
            }

            function cleanText(value) {
                return String(value || '').replace(/\s+/g, ' ').trim();
            }

            function visible(el) {
                if (!el || !el.getBoundingClientRect) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                if (el.closest('[hidden], [aria-hidden="true"]')) return false;
                return rect.width > 0 && rect.height > 0;
            }

            function safeQuery(root, selector) {
                try { return root.querySelector(selector); } catch (e) { return null; }
            }

            function firstNonEmpty(values) {
                for (const value of values) {
                    const text = cleanText(value);
                    if (text) return text;
                }
                return '';
            }

            function nearestLabel(el) {
                if (!el) return '';
                if (el.labels && el.labels.length) return cleanText(el.labels[0].innerText || el.labels[0].textContent);
                const ariaLabelledBy = el.getAttribute('aria-labelledby');
                if (ariaLabelledBy) {
                    const ref = document.getElementById(ariaLabelledBy.split(' ')[0]);
                    if (ref) return cleanText(ref.innerText || ref.textContent);
                }
                if (el.id) {
                    const label = safeQuery(document, 'label[for="' + cssEscape(el.id) + '"]');
                    if (label) return cleanText(label.innerText || label.textContent);
                }
                const parentLabel = el.closest ? el.closest('label') : null;
                if (parentLabel) return cleanText(parentLabel.innerText || parentLabel.textContent);
                let previous = el.previousElementSibling;
                while (previous) {
                    const text = cleanText(previous.innerText || previous.textContent);
                    if (text && ['LABEL','SPAN','DIV','P','STRONG'].includes(previous.tagName)) return text;
                    previous = previous.previousElementSibling;
                }
                return '';
            }

            function containerContext(el) {
                const modal = el.closest ? el.closest('[role="dialog"], [role="alertdialog"], [aria-modal="true"], .modal, .MuiDialog-root, .ant-modal, .ReactModalPortal') : null;
                if (modal) return firstNonEmpty([
                    modal.getAttribute && modal.getAttribute('aria-label'),
                    modal.getAttribute && modal.getAttribute('data-testid'),
                    cleanText(((modal.querySelector && modal.querySelector('h1,h2,h3,[data-testid*="title"],.modal-title')) || {}).innerText)
                ]) || 'modal';

                const form = el.closest ? el.closest('form') : null;
                if (form) return firstNonEmpty([
                    form.getAttribute && form.getAttribute('aria-label'),
                    form.getAttribute && form.getAttribute('name'),
                    form.getAttribute && form.getAttribute('id')
                ]) || 'form';

                const section = el.closest ? el.closest('section, article, aside, table, [role="region"], [role="tabpanel"], [data-testid], .card, .panel') : null;
                if (section) return firstNonEmpty([
                    section.getAttribute && section.getAttribute('aria-label'),
                    section.getAttribute && section.getAttribute('data-testid'),
                    section.getAttribute && section.getAttribute('id'),
                    cleanText(((section.querySelector && section.querySelector('h1,h2,h3,h4,legend,caption')) || {}).innerText)
                ]);
                return '';
            }

            function getShadowPath(el) {
                const parts = [];
                let current = el;
                while (current) {
                    if (current.id) {
                        parts.unshift('#' + current.id);
                    } else if (current.parentElement) {
                        const siblings = Array.from(current.parentElement.children).filter(x => x.tagName === current.tagName);
                        const index = siblings.indexOf(current) + 1;
                        parts.unshift(current.tagName.toLowerCase() + ':nth-of-type(' + index + ')');
                    } else if (current.tagName) {
                        parts.unshift(current.tagName.toLowerCase());
                    }
                    const root = current.getRootNode ? current.getRootNode() : null;
                    if (root && root.host) {
                        parts.unshift('shadow-host(' + (root.host.id ? '#' + root.host.id : root.host.tagName.toLowerCase()) + ')');
                        current = root.host;
                    } else {
                        current = current.parentElement;
                    }
                }
                return parts.join(' > ');
            }

            function selectorInfo(el) {
                const testId = el.getAttribute('data-testid') || el.getAttribute('data-test') || el.getAttribute('data-qa');
                if (testId) return { tipo: 'CSS', valor: "[data-testid='" + testId + "']", backup: getShadowPath(el) };
                if (el.id) return { tipo: 'CSS', valor: '#' + cssEscape(el.id), backup: getShadowPath(el) };
                if (el.getAttribute('name')) return { tipo: 'CSS', valor: el.tagName.toLowerCase() + "[name='" + cssEscape(el.getAttribute('name')) + "']", backup: getShadowPath(el) };
                const ariaLabel = el.getAttribute('aria-label');
                if (ariaLabel) {
                    // Verifica se o elemento está dentro de um container identificável
                    // para gerar um selector mais específico e evitar ambiguidade
                    const parent = el.closest('[id],[data-testid],[data-slick-index],[aria-roledescription]');
                    if (parent && parent !== el) {
                        const parentId = parent.id || parent.getAttribute('data-testid');
                        if (parentId) {
                            return {
                                tipo: 'XPATH',
                                valor: '//*[@id="' + parentId + '"]//' + el.tagName.toLowerCase() + '[@aria-label="' + String(ariaLabel).replace(/"/g, '\\"') + '"]',
                                backup: getShadowPath(el)
                            };
                        }
                    }
                    // Sem container identificável — usa posição relativa ao slick-slider pai
                    const slider = el.closest('.slick-slider,.owl-carousel,.swiper-container,[class*="carousel"],[class*="slider"]');
                    if (slider) {
                        const sliders = Array.from(document.querySelectorAll('.slick-slider,.owl-carousel,.swiper-container,[class*="carousel"],[class*="slider"]'));
                        const sliderIndex = sliders.indexOf(slider);
                        if (sliderIndex >= 0) {
                            return {
                                tipo: 'XPATH',
                                valor: '(//' + el.tagName.toLowerCase() + '[@aria-label="' + String(ariaLabel).replace(/"/g, '\\"') + '"])[' + (sliderIndex + 1) + ']',
                                backup: getShadowPath(el)
                            };
                        }
                    }
                    return { tipo: 'XPATH', valor: '//' + el.tagName.toLowerCase() + '[@aria-label="' + String(ariaLabel).replace(/"/g, '\\"') + '"]', backup: getShadowPath(el) };
                }
                // Para links <a> com href unico (ex: buy_now, product pages), usa o href como selector
                const href = el.getAttribute('href');
                if (el.tagName.toLowerCase() === 'a' && href && href.length > 1 && !href.startsWith('#')) {
                    // Extrai slug do produto para selector mais legivel
                    const hrefParts = href.split('/').filter(Boolean);
                    const slug = hrefParts[hrefParts.length - 1] || '';
                    const cleanSlug = slug.split('?')[0].substring(0, 60);
                    if (cleanSlug) {
                        return { tipo: 'CSS', valor: "a[href*='" + cleanSlug + "']", backup: getShadowPath(el) };
                    }
                    return { tipo: 'CSS', valor: "a[href='" + href.replace(/'/g, "\\'" ) + "']", backup: getShadowPath(el) };
                }
                return { tipo: 'SHADOW_CSS', valor: getShadowPath(el), backup: '' };
            }

            function detectComponentType(el) {
                const tag = (el.tagName || '').toLowerCase();
                const type = (el.getAttribute('type') || '').toLowerCase();
                const role = (el.getAttribute('role') || '').toLowerCase();
                const classes = cleanText(el.className || '').toLowerCase();
                const ariaAutocomplete = (el.getAttribute('aria-autocomplete') || '').toLowerCase();
                const hasPopup = (el.getAttribute('aria-haspopup') || '').toLowerCase();

                if (el.closest && el.closest('[role="dialog"], [role="alertdialog"], [aria-modal="true"], .modal, .MuiDialog-root, .ant-modal, .ReactModalPortal')) {
                    if (tag === 'button' || role === 'button') return 'MODAL_ACTION';
                    if (tag === 'input' || role === 'textbox' || role === 'combobox') return 'MODAL_FIELD';
                    return 'MODAL_CONTAINER';
                }
                if (tag === 'select') return 'SELECT';
                if (role === 'combobox' || classes.includes('select') || classes.includes('dropdown') || hasPopup === 'listbox') return 'CUSTOM_SELECT';
                if (ariaAutocomplete || classes.includes('autocomplete') || role === 'searchbox') return 'AUTOCOMPLETE';
                if (type === 'date' || classes.includes('date') || classes.includes('calendar') || classes.includes('datepicker') || el.getAttribute('aria-haspopup') === 'dialog') return 'DATEPICKER';
                if (tag === 'textarea') return 'TEXTAREA';
                if (tag === 'button' || type === 'button' || type === 'submit' || role === 'button') return 'BUTTON';
                if (tag === 'a') return 'LINK';
                if (tag === 'input' && ['email','password','checkbox','radio','number','tel','search'].includes(type)) return type.toUpperCase();
                if (tag === 'input' || role === 'textbox') return 'TEXT_INPUT';
                return 'INTERACTIVE';
            }

            function baseName(el, componentType) {
                const raw = firstNonEmpty([
                    el.getAttribute('data-testid'),
                    el.getAttribute('data-test'),
                    el.getAttribute('data-qa'),
                    nearestLabel(el),
                    el.getAttribute('aria-label'),
                    el.getAttribute('placeholder'),
                    el.getAttribute('name'),
                    el.id,
                    cleanText(el.innerText || el.textContent)
                ]);
                let normalized = cleanText(raw).replace(/[^\\p{L}\\p{N}]+/gu, ' ').trim();
                if (!normalized) normalized = componentType || (el.tagName || 'elemento');
                // Para links com href de produto, adiciona o nome do produto ao nome logico
                const href = el.getAttribute('href');
                if (el.tagName.toLowerCase() === 'a' && href && href.includes('/')) {
                    const parts = href.split('/').filter(Boolean);
                    const slug = parts[parts.length - 1] || '';
                    const productSlug = slug.split('?')[0];
                    if (productSlug && productSlug.length > 3) {
                        // Converte slug para nome legivel (ex: royal-london-41003 -> Royal London 41003)
                        const productName = productSlug
                            .replace(/-/g, ' ')
                            .replace(/[^a-zA-Z0-9 ]/g, '')
                            .substring(0, 40)
                            .trim();
                        // Limita a 3 palavras para evitar nomes gigantes
                        const words = productName.trim().split(/\s+/).slice(0, 3).join(' ');
                        if (words) return normalized + ' ' + words;
                    }
                }
                return normalized;
            }

            function interactive(el) {
                if (!el || !el.tagName || !visible(el)) return false;
                const tag = el.tagName.toLowerCase();
                const role = (el.getAttribute('role') || '').toLowerCase();
                if (['input','button','a','select','textarea','option'].includes(tag)) return true;
                return ['button','textbox','combobox','listbox','option','dialog','searchbox','switch','checkbox','radio'].includes(role);
            }

            function collectFromRoot(root, result, seen, source) {
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
                let current = walker.currentNode;
                while (current) {
                    if (interactive(current)) {
                        const selector = selectorInfo(current);
                        const componentType = detectComponentType(current);
                        const uniqueKey = [selector.tipo, selector.valor, componentType, source].join('|');
                        if (!seen.has(uniqueKey)) {
                            seen.add(uniqueKey);
                            result.push({
                                tag: current.tagName,
                                type: current.getAttribute('type') || '',
                                role: current.getAttribute('role') || '',
                                name: current.getAttribute('name') || '',
                                id: current.id || '',
                                placeholder: current.getAttribute('placeholder') || '',
                                text: cleanText(current.innerText || current.textContent).substring(0, 120),
                                ariaLabel: current.getAttribute('aria-label') || '',
                                ariaExpanded: current.getAttribute('aria-expanded') || '',
                                ariaAutocomplete: current.getAttribute('aria-autocomplete') || '',
                                dataTestId: current.getAttribute('data-testid') || current.getAttribute('data-test') || current.getAttribute('data-qa') || '',
                                containerContext: containerContext(current),
                                componentType: componentType,
                                sourceContext: source,
                                nomeBase: baseName(current, componentType),
                                seletorPrincipal: selector.valor,
                                tipoSeletorPrincipal: selector.tipo,
                                seletorBackup: selector.backup,
                                inShadowDom: !!(current.getRootNode && current.getRootNode() instanceof ShadowRoot)
                            });
                        }
                    }
                    if (current.shadowRoot) {
                        collectFromRoot(current.shadowRoot, result, seen, source + '>shadow(' + (current.id || current.tagName.toLowerCase()) + ')');
                    }
                    current = walker.nextNode();
                }
            }

            const result = [];
            const seen = new Set();
            collectFromRoot(document, result, seen, 'document');
            return result;
        """;
    }
}
