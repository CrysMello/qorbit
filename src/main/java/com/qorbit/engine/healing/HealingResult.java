package com.qorbit.engine.healing;

import org.openqa.selenium.WebElement;

/**
 * Resultado de uma tentativa de self-healing.
 * Carrega o elemento recuperado e a estratégia que funcionou.
 */
public class HealingResult {

    private final WebElement elemento;
    private final String     estrategia;
    private final String     novoSeletor;

    public HealingResult(WebElement elemento, String estrategia, String novoSeletor) {
        this.elemento    = elemento;
        this.estrategia  = estrategia;
        this.novoSeletor = novoSeletor;
    }

    public WebElement getElemento()    { return elemento;    }
    public String     getEstrategia()  { return estrategia;  }
    public String     getNovoSeletor() { return novoSeletor; }
}
