package com.qorbit.engine.service;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class IframeScannerService {

    private static final int MAX_DEPTH = 3;

    public void percorrerFrames(WebDriver driver, BiConsumer<WebDriver, String> consumer) {
        driver.switchTo().defaultContent();
        percorrerRecursivo(driver, consumer, "root", 0);
        driver.switchTo().defaultContent();
    }

    private void percorrerRecursivo(WebDriver driver, BiConsumer<WebDriver, String> consumer, String caminho, int profundidade) {
        consumer.accept(driver, caminho);
        if (profundidade >= MAX_DEPTH) {
            return;
        }

        List<WebElement> frames = new ArrayList<>(driver.findElements(By.cssSelector("iframe,frame")));
        for (int i = 0; i < frames.size(); i++) {
            try {
                WebElement frame = frames.get(i);
                String frameName = frame.getAttribute("name");
                String frameId = frame.getAttribute("id");
                String identificador = !isBlank(frameName) ? frameName : (!isBlank(frameId) ? frameId : "index-" + i);
                driver.switchTo().frame(frame);
                percorrerRecursivo(driver, consumer, caminho + ">" + identificador, profundidade + 1);
                driver.switchTo().parentFrame();
            } catch (Exception ignored) {
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ignoredAgain) {
                    driver.switchTo().defaultContent();
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
