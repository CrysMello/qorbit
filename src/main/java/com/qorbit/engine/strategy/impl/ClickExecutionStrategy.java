package com.qorbit.engine.strategy.impl;

import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.strategy.ExecutionStrategy;
import com.qorbit.engine.strategy.StrategyType;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ClickExecutionStrategy implements ExecutionStrategy {

    @Override
    public StrategyType type() { return StrategyType.CLICK; }

    @Override
    public void execute(WebDriver driver, WebElement element, StepTeste step) {
        if (element == null) throw new IllegalArgumentException("Elemento não encontrado para clique");

        // Scroll para garantir que o elemento está na viewport antes de qualquer tentativa
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
            Thread.sleep(200);
        } catch (Exception ignored) {}

        // Tentativa 1: clique direto
        try {
            element.click();
            return;
        } catch (ElementClickInterceptedException intercepted) {
            // Overlay/modal/cookiebar na frente — aguarda até 3s para sumir e tenta de novo
            try {
                new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.elementToBeClickable(element));
                element.click();
                return;
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        // Tentativa 2: radio/checkbox customizado — label associado via for=id
        try {
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                WebElement label = driver.findElement(By.cssSelector("label[for='" + id + "']"));
                label.click();
                return;
            }
        } catch (Exception ignored) {}

        // Tentativa 3: clica no pai imediato (wrapper do componente customizado)
        try {
            WebElement parent = element.findElement(By.xpath("./.."));
            parent.click();
            return;
        } catch (Exception ignored) {}

        // Tentativa 4: elemento visual irmão (ex: <span class="checkmark">, <div class="toggle">)
        try {
            WebElement sibling = element.findElement(
                By.xpath("./following-sibling::span | ./following-sibling::div | " +
                         "./preceding-sibling::span | ./preceding-sibling::div"));
            sibling.click();
            return;
        } catch (Exception ignored) {}

        // Tentativa 5: JavaScript click — contorna overlay, pointer-events:none e hidden
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            return;
        } catch (Exception ignored) {}

        throw new IllegalStateException(
            "Falha ao clicar no elemento '" +
            (step.getNomeLogicoElemento() != null ? step.getNomeLogicoElemento() : "?") +
            "' após 5 tentativas");
    }
}