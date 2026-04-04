package com.qorbit.engine.signature;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.model.StepTeste;
import org.openqa.selenium.WebElement;

public interface ComponentSignatureService {
    String generate(WebElement element, StepTeste step, ComponentClassification classification);
}
