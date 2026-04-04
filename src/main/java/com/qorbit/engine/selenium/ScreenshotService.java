package com.qorbit.engine.selenium;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ScreenshotService {

    @Value("${scanner.evidencias.path:evidencias}")
    private String basePath;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * Usado pelo SeleniumWorker: Long execucaoId + label.
     * Salva em: basePath/execucao-{id}/{label}.png
     * Retorna caminho RELATIVO ao basePath: "execucao-{id}/{label}.png"  (usado como nomeArquivo na API)
     */
    public String capturar(WebDriver driver, Long execucaoId, String label) {
        try {
            String subDir   = "execucao-" + execucaoId;
            String arquivo  = sanitizar(label) + ".png";
            Path   dir      = Paths.get(basePath, subDir);
            Files.createDirectories(dir);
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(dir.resolve(arquivo), bytes);
            return subDir + "/" + arquivo;   // relativo
        } catch (Exception e) {
            System.err.println("[Screenshot] Erro: " + e.getMessage());
            return null;
        }
    }

    /**
     * Usado pelos testes unitários: String execucaoId + nomeTeste + step + status.
     * Salva em: basePath/{execucaoId}_{nomeSanitizado}/step-{NN}-{STATUS}_{hora}.png
     * Retorna o CAMINHO ABSOLUTO do arquivo criado (para o teste verificar Files.exists).
     */
    public String capturar(WebDriver driver, String execucaoId, String nomeTeste,
                           int numeroStep, String status) throws Exception {
        String hora    = LocalDateTime.now().format(FMT);
        String subDir  = execucaoId + "_" + sanitizar(nomeTeste);
        String arquivo = String.format("step-%02d-%s_%s.png", numeroStep, status, hora);
        Path   dir     = Paths.get(basePath, subDir);
        Files.createDirectories(dir);
        byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        Path   dest  = dir.resolve(arquivo);
        Files.write(dest, bytes);
        return dest.toAbsolutePath().toString();   // absoluto — testes fazem Paths.get(caminho)
    }

    private String sanitizar(String texto) {
        if (texto == null) return "screenshot";
        return texto.replaceAll("[:/\\\\!?*<>|\"'\\s]", "_").replaceAll("_+", "_");
    }
}
