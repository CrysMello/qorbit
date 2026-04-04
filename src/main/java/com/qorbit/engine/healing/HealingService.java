package com.qorbit.engine.healing;

import com.qorbit.engine.model.Elemento;
import org.openqa.selenium.WebDriver;

/**
 * Serviço de self-healing: quando o seletor original falha,
 * tenta localizar o elemento por estratégias alternativas.
 */
public interface HealingService {

    /**
     * Tenta recuperar o elemento usando o fingerprint gravado em {@link Elemento#getDescricao()}.
     *
     * @param driver    WebDriver ativo
     * @param elemento  registro do elemento na biblioteca (com fingerprint na descricao)
     * @param timeout   tempo máximo em segundos para cada tentativa
     * @return          resultado com o elemento e a estratégia usada, ou {@code null} se nenhuma funcionou
     */
    HealingResult tentar(WebDriver driver, Elemento elemento, int timeout);
}
