package com.qorbit.engine.service;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.repository.ElementoRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CapturaService {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private CapturaInteligenteService capturaInteligenteService;

    /**
     * Navega até a URL e extrai elementos interativos visíveis com seleção de seletor mais estável.
     */
    public List<Elemento> capturarElementos(String url, QorbitUser usuario) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1200");
        WebDriver driver = new ChromeDriver(opts);
        List<Elemento> capturados = new ArrayList<>();

        try {
            capturaInteligenteService.aplicarCookies(driver, url, null);
            driver.get(url);
            capturados.addAll(capturaInteligenteService.capturarElementos(driver, url));

            for (Elemento elemento : capturados) {
                boolean existe = elementoRepo.findAll().stream()
                        .anyMatch(ex -> ex.getTipoSeletor().equalsIgnoreCase(elemento.getTipoSeletor())
                                && ex.getSeletorTecnico().equals(elemento.getSeletorTecnico())
                                && ((usuario == null && ex.getUsuario() == null) ||
                                    (usuario != null && ex.getUsuario() != null && ex.getUsuario().getId().equals(usuario.getId()))));
                if (!existe) {
                    // ✅ SETAR O USUÁRIO DO ELEMENTO
                    elemento.setUsuario(usuario);
                    elementoRepo.save(elemento);
                }
            }
        } finally {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
        }

        return capturados;
    }
}
