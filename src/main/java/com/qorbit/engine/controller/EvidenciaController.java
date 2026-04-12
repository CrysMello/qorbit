package com.qorbit.engine.controller;

import com.qorbit.engine.model.Evidencia;
import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.repository.ExecucaoRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/evidencias")
public class EvidenciaController {

    @Autowired private ExecucaoRepository execucaoRepo;
    @Autowired private com.qorbit.engine.repository.EvidenciaRepository evidenciaRepo;

    @Value("${scanner.evidencias.path:evidencias}")
    private String basePath;

    @GetMapping("/execucao/{execucaoId}")
    public ResponseEntity<?> listarPorExecucao(@PathVariable Long execucaoId) {
        return execucaoRepo.findById(execucaoId).map(exec -> {
            List<Map<String, Object>> result = new ArrayList<>();
            if (exec.getEvidencias() != null) {
                exec.getEvidencias().forEach(ev -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",          ev.getId());
                    m.put("numeroStep",  ev.getNumeroStep());
                    m.put("nomeStep",    ev.getNomeStep());
                    m.put("statusStep",  ev.getStatusStep());
                    m.put("nomeArquivo", ev.getNomeArquivo()); // ex: "execucao-5/step-1-ok-t1.png"
                    m.put("motivoFalha", ev.getMotivoFalha());
                    m.put("capturadoEm", ev.getCapturadoEm());
                    result.add(m);
                });
            }
            // fallback: varre pasta em disco se banco vazio
            if (result.isEmpty()) result.addAll(varrerPasta(execucaoId));
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Serve imagem usando execucaoId + filename como path variables separados.
     * GET /api/evidencias/imagem/{execucaoId}/{filename}
     * Evita problema de encodeURIComponent("/") → "%2F"
     */
    @GetMapping("/imagem/{execucaoId}/{filename:.+}")
    public ResponseEntity<Resource> getImagem(
            @PathVariable String execucaoId,
            @PathVariable String filename) {
        try {
            // Estrategia 1: caminho absoluto salvo no banco (mais confiavel no Windows).
            // O caminhoArquivo e gravado pelo SeleniumWorker usando o basePath injetado
            // pelo Spring, evitando dependencia do CWD da JVM.
            String nomeRelativo = "execucao-" + execucaoId + "/" + filename;
            Path file = evidenciaRepo.findAll().stream()
                    .filter(ev -> nomeRelativo.equals(ev.getNomeArquivo()))
                    .map(ev -> ev.getCaminhoArquivo())
                    .filter(p -> p != null && !p.isBlank())
                    .map(p -> Paths.get(p))
                    .filter(Files::exists)
                    .findFirst()
                    // Estrategia 2: reconstroi a partir do basePath relativo (fallback)
                    .orElseGet(() -> {
                        Path base = Paths.get(basePath).toAbsolutePath().normalize();
                        return base.resolve("execucao-" + execucaoId).resolve(filename).normalize();
                    });

            // Segurança: bloqueia path traversal relativo ao basePath
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            if (!file.toAbsolutePath().normalize().startsWith(base)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new FileSystemResource(file);
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String ct = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(ct))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Download ZIP */
    @GetMapping("/download/{execucaoId}")
    public ResponseEntity<byte[]> downloadZip(@PathVariable Long execucaoId) throws IOException {
        Optional<Execucao> opt = execucaoRepo.findById(execucaoId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Execucao exec = opt.get();

        // Monta logs com caminho relativo para as imagens dentro do ZIP
        List<com.qorbit.engine.service.ExecutionReportService.StepLog> logs = new ArrayList<>();
        List<com.qorbit.engine.model.Evidencia> evList =
                evidenciaRepo.findByExecucaoIdOrderByNumeroStepAsc(execucaoId);

        for (com.qorbit.engine.model.Evidencia ev : evList) {
            com.qorbit.engine.service.ExecutionReportService.StepLog log =
                    new com.qorbit.engine.service.ExecutionReportService.StepLog();
            log.numero  = ev.getNumeroStep();
            log.nome    = ev.getNomeStep();
            log.acao    = ev.getNomeStep();
            log.status  = ev.getStatusStep();
            log.detalhe = ev.getMotivoFalha();
            // caminho relativo ao HTML dentro do ZIP: evidencias/nome.png
            if (ev.getNomeArquivo() != null) {
                log.screenshot = "evidencias/" + Paths.get(ev.getNomeArquivo()).getFileName();
            }
            logs.add(log);
        }

        String htmlRelatorio = gerarRelatorioHtmlCompleto(exec, logs);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            // Relatório HTML
            zip.putNextEntry(new ZipEntry("relatorio.html"));
            zip.write(htmlRelatorio.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();

            // Imagens como arquivos separados na pasta evidencias/
            Path pastaExec = Paths.get(basePath, "execucao-" + execucaoId);
            if (Files.exists(pastaExec)) {
                Files.walk(pastaExec)
                        .filter(p -> !Files.isSymbolicLink(p))
                        .filter(p -> p.toString().endsWith(".png") || p.toString().endsWith(".jpg"))
                        .forEach(p -> {
                            try {
                                zip.putNextEntry(new ZipEntry("evidencias/" + p.getFileName()));
                                zip.write(Files.readAllBytes(p));
                                zip.closeEntry();
                            } catch (Exception ignored) {}
                        });
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"evidencias-execucao-" + execucaoId + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> varrerPasta(Long execucaoId) {
        List<Map<String, Object>> lista = new ArrayList<>();
        try {
            Path pasta = Paths.get(basePath, "execucao-" + execucaoId);
            if (!Files.exists(pasta)) return lista;
            final int[] n = {1};
            Files.walk(pasta).filter(p -> p.toString().endsWith(".png")).sorted().forEach(p -> {
                String nome   = p.getFileName().toString();
                String status = nome.toUpperCase().contains("FALHA") ? "FALHOU" : "PASSOU";
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", null);
                m.put("numeroStep",  n[0]++);
                m.put("nomeStep",    nome.replace(".png", ""));
                m.put("statusStep",  status);
                m.put("nomeArquivo", "execucao-" + execucaoId + "/" + nome);
                m.put("motivoFalha", null);
                m.put("capturadoEm", null);
                lista.add(m);
            });
        } catch (Exception e) {
            System.err.println("Erro ao varrer pasta: " + e.getMessage());
        }
        return lista;
    }

    private String gerarRelatorioHtmlCompleto(Execucao exec,
            List<com.qorbit.engine.service.ExecutionReportService.StepLog> logs) {
        int p   = exec.getStepsPAssou() != null ? exec.getStepsPAssou() : 0;
        int f   = exec.getStepsFalhou() != null ? exec.getStepsFalhou() : 0;
        int t   = p + f;
        double pct = t > 0 ? (p * 100.0 / t) : 0;
        String corPct = pct >= 80 ? "#16A34A" : pct >= 50 ? "#D97706" : "#DC2626";
        String duracao = exec.getTempoExecucaoSegundos() != null
                ? exec.getTempoExecucaoSegundos() + "s" : "—";

        // Labels e dados para Chart.js
        StringBuilder labels = new StringBuilder("[");
        StringBuilder dPass  = new StringBuilder("[");
        StringBuilder dFail  = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            var l = logs.get(i);
            boolean ok = "PASSOU".equalsIgnoreCase(l.status);
            labels.append("'Step ").append(l.numero).append("'");
            dPass.append(ok ? 1 : 0);
            dFail.append(ok ? 0 : 1);
            if (i < logs.size() - 1) { labels.append(","); dPass.append(","); dFail.append(","); }
        }
        labels.append("]"); dPass.append("]"); dFail.append("]");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='pt-BR'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>Relatório de Execução #").append(exec.getId()).append("</title>")
          .append("<script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>")
          .append("<style>")
          .append("*{box-sizing:border-box;margin:0;padding:0}")
          .append("body{font-family:'Segoe UI',Arial,sans-serif;background:#F1F5F9;color:#1E293B}")
          .append(".header{background:linear-gradient(135deg,#1F4E79 0%,#2563EB 100%);color:white;padding:32px 40px}")
          .append(".header h1{font-size:24px;font-weight:700;margin-bottom:6px}")
          .append(".header .meta{font-size:13px;opacity:.85}")
          .append(".header .meta span{margin-right:20px}")
          .append(".body{max-width:1100px;margin:0 auto;padding:32px 24px}")
          .append(".cards{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:28px}")
          .append(".card{background:white;border-radius:12px;padding:20px 24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}")
          .append(".card-label{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.8px;color:#64748B;margin-bottom:6px}")
          .append(".card-value{font-size:32px;font-weight:700}")
          .append(".charts{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:24px}")
          .append(".chart-box{background:white;border-radius:12px;padding:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}")
          .append(".chart-box h2{font-size:15px;font-weight:600;margin-bottom:16px;color:#1E293B}")
          .append(".section{background:white;border-radius:12px;padding:24px;margin-bottom:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}")
          .append(".section h2{font-size:15px;font-weight:600;margin-bottom:16px;padding-bottom:10px;border-bottom:2px solid #E2E8F0}")
          .append("table{width:100%;border-collapse:collapse;font-size:13px}")
          .append("thead th{background:#F8FAFC;color:#475569;font-size:11px;text-transform:uppercase;padding:10px 14px;text-align:left;border-bottom:2px solid #E2E8F0}")
          .append("tbody td{padding:11px 14px;border-bottom:1px solid #F1F5F9;vertical-align:top}")
          .append("tbody tr:hover{background:#F8FAFC}")
          .append(".badge{display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:600}")
          .append(".passou{background:#DCFCE7;color:#166534}")
          .append(".falhou{background:#FEE2E2;color:#DC2626}")
          .append(".step-box{border:1px solid #E2E8F0;border-radius:8px;margin-bottom:12px;overflow:hidden}")
          .append(".step-head{display:flex;align-items:center;gap:10px;padding:12px 16px;background:#F8FAFC;border-bottom:1px solid #E2E8F0}")
          .append(".step-num{width:26px;height:26px;border-radius:50%;background:#1F4E79;color:white;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0}")
          .append(".step-body{padding:12px 16px}")
          .append(".erro{color:#DC2626;background:#FEF2F2;padding:8px;border-radius:6px;font-size:12px;margin-top:6px;font-family:monospace}")
          .append(".step-body img{max-width:100%;border:1px solid #E2E8F0;border-radius:6px;margin-top:8px}")
          .append(".bar{height:8px;background:#E2E8F0;border-radius:4px;overflow:hidden;margin-top:8px}")
          .append(".bar-fill{height:100%;border-radius:4px;background:linear-gradient(90deg,#16A34A,#22C55E)}")
          .append(".footer{text-align:center;padding:24px;color:#94A3B8;font-size:12px}")
          .append("</style></head><body>")

          // Header
          .append("<div class='header'><h1>&#128203; Relatório de Execução #").append(exec.getId()).append("</h1>")
          .append("<div class='meta'>")
          .append("<span>&#128279; ").append(exec.getUrlAlvo()).append("</span>")
          .append("<span>&#128336; ").append(exec.getIniciadoEm() != null ? exec.getIniciadoEm().replace("T"," ").substring(0,19) : "—").append("</span>")
          .append("<span>&#9200; Duração: ").append(duracao).append("</span>")
          .append("</div></div>")

          .append("<div class='body'>")

          // Cards
          .append("<div class='cards'>")
          .append("<div class='card'><div class='card-label'>Total</div><div class='card-value' style='color:#2563EB'>").append(t).append("</div></div>")
          .append("<div class='card'><div class='card-label'>Passou</div><div class='card-value' style='color:#16A34A'>").append(p).append("</div></div>")
          .append("<div class='card'><div class='card-label'>Falhou</div><div class='card-value' style='color:#DC2626'>").append(f).append("</div></div>")
          .append("<div class='card'><div class='card-label'>% Sucesso</div><div class='card-value' style='color:").append(corPct).append("'>")
          .append(String.format("%.0f%%", pct)).append("</div>")
          .append("<div class='bar'><div class='bar-fill' style='width:").append(String.format("%.0f%%", pct)).append("'></div></div></div>")
          .append("</div>"); // end cards

        // Charts
        sb.append("<div class='charts'>")
          .append("<div class='chart-box'><h2>&#128202; Resultado por Step</h2><canvas id='c1'></canvas></div>")
          .append("<div class='chart-box'><h2>&#128200; Resumo</h2><canvas id='c2'></canvas></div>")
          .append("</div>");

        // Tabela
        sb.append("<div class='section'><h2>&#128203; Tabela de Steps</h2>")
          .append("<table><thead><tr><th>#</th><th>Descrição</th><th>Status</th><th>Detalhe da falha</th></tr></thead><tbody>");
        for (var log : logs) {
            boolean ok = "PASSOU".equalsIgnoreCase(log.status);
            sb.append("<tr>")
              .append("<td><b>").append(log.numero).append("</b></td>")
              .append("<td>").append(log.nome != null ? HtmlUtils.htmlEscape(log.nome) : "—").append("</td>")
              .append("<td><span class='badge ").append(ok ? "passou" : "falhou").append("'>")
              .append(ok ? "&#10003; PASSOU" : "&#10007; FALHOU").append("</span></td>")
              .append("<td style='color:#DC2626;font-size:12px'>").append(log.detalhe != null ? HtmlUtils.htmlEscape(log.detalhe) : "—").append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table></div>");

        // Screenshots
        boolean temScreenshots = logs.stream().anyMatch(l -> l.screenshot != null);
        if (temScreenshots) {
            sb.append("<div class='section'><h2>&#128247; Evidências</h2>");
            for (var log : logs) {
                boolean ok = "PASSOU".equalsIgnoreCase(log.status);
                sb.append("<div class='step-box'>")
                  .append("<div class='step-head'>")
                  .append("<div class='step-num'>").append(log.numero).append("</div>")
                  .append("<div style='flex:1;font-size:13px;font-weight:500'>").append(log.nome != null ? HtmlUtils.htmlEscape(log.nome) : "Step " + log.numero).append("</div>")
                  .append("<span class='badge ").append(ok ? "passou" : "falhou").append("'>")
                  .append(ok ? "&#10003; PASSOU" : "&#10007; FALHOU").append("</span>")
                  .append("</div><div class='step-body'>");
                if (log.detalhe != null) {
                    sb.append("<div class='erro'>").append(HtmlUtils.htmlEscape(log.detalhe)).append("</div>");
                }
                if (log.screenshot != null) {
                    // screenshot path no zip: "evidencias/nome.png" → src relativo ao HTML na raiz do zip
                    sb.append("<img src='").append(log.screenshot).append("' alt='Step ").append(log.numero).append("'>");
                }
                sb.append("</div></div>");
            }
            sb.append("</div>");
        }

        sb.append("<div class='footer'>Gerado pelo Qorbit v2.0</div></div>")
          .append("<script>")
          .append("new Chart(document.getElementById('c1').getContext('2d'),{type:'bar',data:{labels:").append(labels)
          .append(",datasets:[{label:'Passou',data:").append(dPass).append(",backgroundColor:'#22C55E'},{label:'Falhou',data:").append(dFail).append(",backgroundColor:'#EF4444'}]},options:{responsive:true,scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{position:'top'}}}});")
          .append("new Chart(document.getElementById('c2').getContext('2d'),{type:'bar',data:{labels:['Passou','Falhou'],datasets:[{label:'Steps',data:[").append(p).append(",").append(f)
          .append("],backgroundColor:['#22C55E','#EF4444']}]},options:{responsive:true,indexAxis:'y',scales:{x:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{display:false}}}});")
          .append("</script></body></html>");

        return sb.toString();
    }
}
