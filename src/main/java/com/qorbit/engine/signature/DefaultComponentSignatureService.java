package com.qorbit.engine.signature;

import com.qorbit.engine.classification.ComponentClassification;
import com.qorbit.engine.model.StepTeste;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class DefaultComponentSignatureService implements ComponentSignatureService {
    @Override
    public String generate(WebElement element, StepTeste step, ComponentClassification classification) {
        try {
            String raw = String.join("|",
                    nz(step != null ? step.getNomeLogicoElemento() : null),
                    nz(element != null ? element.getTagName() : null),
                    nz(element != null ? element.getAttribute("id") : null),
                    nz(element != null ? element.getAttribute("name") : null),
                    nz(element != null ? element.getAttribute("role") : null),
                    nz(classification != null ? classification.type().name() : null));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception e) {
            return "sig-unknown";
        }
    }

    private String nz(String value) { return value == null ? "" : value; }
}
