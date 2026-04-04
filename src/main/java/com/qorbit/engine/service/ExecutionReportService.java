package com.qorbit.engine.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.qorbit.engine.repository.EvidenciaRepository;

@Service
public class ExecutionReportService {

    @Value("${scanner.report.path:reports}")
    private String reportPath;

    @Value("${scanner.evidencias.path:evidencias}")
    private String evidenciasPath;

    @Autowired(required = false)
    private EvidenciaRepository evidenciaRepo;

    /**
     * Lê a screenshot do disco e converte para Base64.
     * Isso torna o relatório HTML completamente autónomo —
     * funciona offline, como PDF e sem servidor a correr.
     */
    private String screenshotParaBase64(String caminhoRelativo) {
        if (caminhoRelativo == null || caminhoRelativo.isBlank()) return null;
        try {
            // Estrategia 1: caminho absoluto salvo no banco — o mais confiavel.
            // Evita depender do CWD da JVM para reconstruir o caminho (problema no Windows).
            if (evidenciaRepo != null) {
                java.util.Optional<com.qorbit.engine.model.Evidencia> ev =
                    evidenciaRepo.findAll().stream()
                        .filter(e -> caminhoRelativo.equals(e.getNomeArquivo()))
                        .filter(e -> e.getCaminhoArquivo() != null && !e.getCaminhoArquivo().isBlank())
                        .findFirst();
                if (ev.isPresent()) {
                    Path absoluto = Path.of(ev.get().getCaminhoArquivo());
                    if (Files.exists(absoluto)) {
                        byte[] bytes = Files.readAllBytes(absoluto);
                        return "data:image/png;base64,"
                                + java.util.Base64.getEncoder().encodeToString(bytes);
                    }
                }
            }
            // Estrategia 2: reconstroi a partir do evidenciasPath (basePath relativo)
            Path caminho = Path.of(evidenciasPath).toAbsolutePath().resolve(caminhoRelativo);
            if (!Files.exists(caminho)) {
                // Estrategia 3: tenta o valor como caminho absoluto direto
                caminho = Path.of(caminhoRelativo);
            }
            if (!Files.exists(caminho)) return null;
            byte[] bytes = Files.readAllBytes(caminho);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            System.err.println("[Relatório] Erro ao carregar screenshot: " + e.getMessage());
            return null;
        }
    }

    public static class StepLog {
        public Integer numero;
        public String  nome;
        public String  acao;
        public String  status;
        public String  detalhe;
        public String  screenshot;
    }

    public List<StepLog> novaLista() { return new ArrayList<>(); }

    public String gerarRelatorio(Long execucaoId, String status, String url,
                                  Integer passou, Integer falhou,
                                  String iniciadoEm, String finalizadoEm,
                                  List<StepLog> logs) throws IOException {

        int total = (passou == null ? 0 : passou) + (falhou == null ? 0 : falhou);
        int p     = passou == null ? 0 : passou;
        int f     = falhou == null ? 0 : falhou;
        double pct = total > 0 ? (p * 100.0 / total) : 0;

        Path pasta = Path.of(reportPath, "execucao-" + execucaoId);
        Files.createDirectories(pasta);

        String html = buildHtml(execucaoId, status, url, p, f, total, pct,
                                iniciadoEm, finalizadoEm, logs);

        Path arquivo = pasta.resolve("relatorio.html");
        Files.writeString(arquivo, html, StandardCharsets.UTF_8);
        return arquivo.toString();
    }

    private String buildHtml(Long id, String status, String url,
                              int passou, int falhou, int total, double pct,
                              String inicio, String fim, List<StepLog> logs) {

        String corStatus = switch (status == null ? "" : status) {
            case "CONCLUIDO" -> "#166534";
            case "ERRO"      -> "#DC2626";
            default          -> "#92400E";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Relatório de Execução #""").append(id).append("""
              </title>
              <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
              <style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:'Segoe UI',Arial,sans-serif;background:#F1F5F9;color:#1E293B;padding:0}
                .header{background:linear-gradient(135deg,#1F4E79 0%,#2563EB 100%);color:white;padding:32px 40px}
                .header h1{font-size:26px;font-weight:700;margin-bottom:6px}
                .header .meta{font-size:13px;opacity:.85}
                .header .meta span{margin-right:24px}
                .body{max-width:1100px;margin:0 auto;padding:32px 24px}
                .cards-row{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:28px}
                .card{background:white;border-radius:12px;padding:20px 24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}
                .card-label{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.8px;color:#64748B;margin-bottom:6px}
                .card-value{font-size:32px;font-weight:700}
                .card-value.green{color:#16A34A}
                .card-value.red{color:#DC2626}
                .card-value.blue{color:#2563EB}
                .section{background:white;border-radius:12px;padding:24px;margin-bottom:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}
                .section h2{font-size:16px;font-weight:600;color:#1E293B;margin-bottom:20px;padding-bottom:10px;border-bottom:2px solid #E2E8F0}
                .chart-row{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:24px}
                .chart-wrap{background:white;border-radius:12px;padding:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}
                .chart-wrap h2{font-size:16px;font-weight:600;color:#1E293B;margin-bottom:16px}
                table{width:100%;border-collapse:collapse;font-size:13px}
                thead th{background:#F8FAFC;color:#475569;font-weight:600;font-size:11px;text-transform:uppercase;letter-spacing:.5px;padding:10px 14px;text-align:left;border-bottom:2px solid #E2E8F0}
                tbody td{padding:12px 14px;border-bottom:1px solid #F1F5F9;vertical-align:top}
                tbody tr:last-child td{border-bottom:none}
                tbody tr:hover{background:#F8FAFC}
                .badge{display:inline-flex;align-items:center;gap:4px;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:600}
                .badge-passou{background:#DCFCE7;color:#166534}
                .badge-falhou{background:#FEE2E2;color:#DC2626}
                .step-box{border:1px solid #E2E8F0;border-radius:8px;margin-bottom:12px;overflow:hidden}
                .step-header{display:flex;align-items:center;gap:10px;padding:12px 16px;background:#F8FAFC;border-bottom:1px solid #E2E8F0}
                .step-num{width:26px;height:26px;border-radius:50%;background:#1F4E79;color:white;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0}
                .step-nome{font-weight:500;flex:1;font-size:13px}
                .step-body{padding:12px 16px}
                .step-body p{font-size:12px;color:#64748B;margin-bottom:4px}
                .step-body .erro-msg{color:#DC2626;background:#FEF2F2;padding:8px 10px;border-radius:6px;font-size:12px;margin-top:6px;font-family:monospace}
                .step-body img{max-width:100%;border:1px solid #E2E8F0;border-radius:6px;margin-top:8px;cursor:pointer}
                .progress-bar{height:8px;background:#E2E8F0;border-radius:4px;overflow:hidden;margin-top:8px}
                .progress-fill{height:100%;border-radius:4px;background:linear-gradient(90deg,#16A34A,#22C55E)}
                .footer{text-align:center;padding:24px;color:#94A3B8;font-size:12px}
                @media(max-width:700px){.cards-row{grid-template-columns:repeat(2,1fr)}.chart-row{grid-template-columns:1fr}}
              </style>
            </head>
            <body>
            <div class="header">
              <h1>Relatório de Execução #""").append(id).append("</h1>")
          .append("<div class='meta'>")
          .append("<span>&#128279; ").append(url).append("</span>")
          .append("<span>&#128337; Iniciado: ").append(inicio != null ? inicio : "—").append("</span>")
          .append("<span>&#9989; Finalizado: ").append(fim != null ? fim : "—").append("</span>")
          .append("<span style='background:rgba(255,255,255,.2);padding:2px 10px;border-radius:20px;font-weight:600;color:")
          .append(corStatus).append("'>").append(status).append("</span>")
          .append("</div></div>")

          .append("<div class='body'>")

          // Cards resumo
          .append("<div class='cards-row'>")
          .append("<div class='card'><div class='card-label'>Total de Steps</div><div class='card-value blue'>").append(total).append("</div></div>")
          .append("<div class='card'><div class='card-label'>Passou</div><div class='card-value green'>").append(passou).append("</div></div>")
          .append("<div class='card'><div class='card-label'>Falhou</div><div class='card-value red'>").append(falhou).append("</div></div>")
          .append("<div class='card'><div class='card-label'>% Sucesso</div><div class='card-value' style='color:").append(pct >= 80 ? "#16A34A" : pct >= 50 ? "#D97706" : "#DC2626").append("'>").append(String.format("%.0f%%", pct)).append("</div>")
          .append("<div class='progress-bar'><div class='progress-fill' style='width:").append(String.format("%.0f%%", pct)).append("'></div></div></div>")
          .append("</div>");

        // Gráficos de barras
        sb.append("<div class='chart-row'>")
          .append("<div class='chart-wrap'><h2>&#128202; Resultado por Step</h2><canvas id='chartBar' height='200'></canvas></div>")
          .append("<div class='chart-wrap'><h2>&#128200; Resumo da Execução</h2><canvas id='chartResumo' height='200'></canvas></div>")
          .append("</div>");

        // Tabela resumo
        sb.append("<div class='section'><h2>&#128203; Tabela de Steps</h2>")
          .append("<table><thead><tr>")
          .append("<th>#</th><th>Descrição</th><th>Ação</th><th>Status</th><th>Detalhe</th>")
          .append("</tr></thead><tbody>");

        for (StepLog log : logs) {
            boolean ok = "PASSOU".equalsIgnoreCase(log.status);
            sb.append("<tr>")
              .append("<td><b>").append(log.numero).append("</b></td>")
              .append("<td>").append(log.nome != null ? log.nome : "—").append("</td>")
              .append("<td><code>").append(log.acao != null ? log.acao : "—").append("</code></td>")
              .append("<td><span class='badge ").append(ok ? "badge-passou" : "badge-falhou").append("'>")
              .append(ok ? "&#10003; PASSOU" : "&#10007; FALHOU").append("</span></td>")
              .append("<td>").append(log.detalhe != null ? log.detalhe : "—").append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table></div>");

        // Detalhes com screenshots
        if (logs.stream().anyMatch(l -> l.screenshot != null)) {
            sb.append("<div class='section'><h2>&#128247; Evidências (Screenshots)</h2>");
            for (StepLog log : logs) {
                boolean ok = "PASSOU".equalsIgnoreCase(log.status);
                sb.append("<div class='step-box'>")
                  .append("<div class='step-header'>")
                  .append("<div class='step-num'>").append(log.numero).append("</div>")
                  .append("<div class='step-nome'>").append(log.nome != null ? log.nome : log.acao).append("</div>")
                  .append("<span class='badge ").append(ok ? "badge-passou" : "badge-falhou").append("'>")
                  .append(ok ? "&#10003; PASSOU" : "&#10007; FALHOU").append("</span>")
                  .append("</div><div class='step-body'>");
                if (log.detalhe != null) {
                    sb.append("<div class='erro-msg'>").append(log.detalhe).append("</div>");
                }
                if (log.screenshot != null) {
                    String imgData = screenshotParaBase64(log.screenshot);
                    if (imgData != null) {
                        sb.append("<img src=\'").append(imgData)
                          .append("\' alt=\'screenshot step ").append(log.numero).append("\' style=\'max-width:100%;border:1px solid #E2E8F0;border-radius:6px;margin-top:8px;\'>");
                    } else {
                        sb.append("<p style=\'color:#94A3B8;font-size:11px;margin-top:6px;\'>Screenshot não disponível: ").append(log.screenshot).append("</p>");
                    }
                }
                sb.append("</div></div>");
            }
            sb.append("</div>");
        }

        sb.append("<div class='footer'>Gerado pelo Qorbit v2.0</div></div>");

        // Labels e dados para os gráficos
        StringBuilder labels = new StringBuilder("[");
        StringBuilder dataPassou = new StringBuilder("[");
        StringBuilder dataFalhou = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            StepLog l = logs.get(i);
            labels.append("'Step ").append(l.numero).append("'");
            boolean ok = "PASSOU".equalsIgnoreCase(l.status);
            dataPassou.append(ok ? 1 : 0);
            dataFalhou.append(ok ? 0 : 1);
            if (i < logs.size() - 1) { labels.append(","); dataPassou.append(","); dataFalhou.append(","); }
        }
        labels.append("]"); dataPassou.append("]"); dataFalhou.append("]");

        sb.append("<script>")
          .append("const ctx1=document.getElementById('chartBar').getContext('2d');")
          .append("new Chart(ctx1,{type:'bar',data:{labels:").append(labels)
          .append(",datasets:[{label:'Passou',data:").append(dataPassou)
          .append(",backgroundColor:'#22C55E'},{label:'Falhou',data:").append(dataFalhou)
          .append(",backgroundColor:'#EF4444'}]},options:{responsive:true,scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{position:'top'}}}});")

          .append("const ctx2=document.getElementById('chartResumo').getContext('2d');")
          .append("new Chart(ctx2,{type:'bar',data:{labels:['Passou','Falhou'],datasets:[{label:'Steps',data:[")
          .append(passou).append(",").append(falhou)
          .append("],backgroundColor:['#22C55E','#EF4444']}]},options:{responsive:true,indexAxis:'y',scales:{x:{beginAtZero:true,ticks:{stepSize:1}}},plugins:{legend:{display:false}}}});")
          .append("</script></body></html>");

        return sb.toString();
    }
}
