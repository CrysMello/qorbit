package com.qorbit.engine.classification;

import com.qorbit.engine.model.StepTeste;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public interface ComponentClassifier {
    ComponentClassification classify(WebDriver driver, WebElement element, StepTeste step);
}
