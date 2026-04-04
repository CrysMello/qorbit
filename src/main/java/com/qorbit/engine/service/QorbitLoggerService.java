package com.qorbit.engine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * QorbitLoggerService — Sistema de logs em português com formato melhorado.
 *
 * Formato do log de execução:
 *   - Resumo executivo no topo
 *   - Detalhe por step com self-healing
 *   - Secção de acções recomendadas no fundo
 */
@Service
public class QorbitLoggerService {

    @Value("${scanner.logs.path:logs}")
    private String logsPath;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Acumula acções recomendadas por execução
    private final Map<Long, List<String>> acoesCriticas = new HashMap<>();
    private final Map<Long, List<String>> acoesRevisar = new HashMap<>();
    private final Map<Long, List<Integer>> stepsBloqueados = new HashMap<>();

    // ── Início de execução ────────────────────────────────────────────────────

    public void execucaoIniciada(Long execId, String url, int totalSteps) {
        String sep = "═".repeat(60);
        escrever("execucoes", execId, sep);
        escrever("execucoes", execId, "EXECUÇÃO #" + execId + " — " + LocalDateTime.now().format(FMT));
        escrever("execucoes", execId, "URL: " + url);
        escrever("execucoes", execId, "Total de steps: " + totalSteps);
        escrever("execucoes", execId, sep);
        escrever("execucoes", execId, "");
        acoesCriticas.put(execId, new ArrayList<>());
        acoesRevisar.put(execId, new ArrayList<>());
        stepsBloqueados.put(execId, new ArrayList<>());
        info("Execução #" + execId + " iniciada — URL: " + url + " | Steps: " + totalSteps);
    }

    public void execucaoConcluida(Long execId, int passou, int falhou, int bloqueados, long duracaoSeg) {
        String sep = "─".repeat(60);
        int total = passou + falhou + bloqueados;
        int pct = total > 0 ? (passou * 100 / total) : 0;
        escrever("execucoes", execId, "");
        escrever("execucoes", execId, sep);
        escrever("execucoes", execId, "RESUMO FINAL");
        escrever("execucoes", execId, sep);
        escrever("execucoes", execId, "✅ Passaram:   " + passou);
        escrever("execucoes", execId, "❌ Falharam:   " + falhou);
        escrever("execucoes", execId, "⏭ Bloqueados: " + bloqueados + " (dependiam de steps que falharam)");
        escrever("execucoes", execId, "⏱ Duração:    " + duracaoSeg + "s");
        escrever("execucoes", execId, "📊 Sucesso:    " + pct + "%");

        // Acções recomendadas
        List<String> criticas = acoesCriticas.getOrDefault(execId, List.of());
        List<String> revisar = acoesRevisar.getOrDefault(execId, List.of());
        List<Integer> bloq = stepsBloqueados.getOrDefault(execId, List.of());

        if (!criticas.isEmpty() || !revisar.isEmpty() || !bloq.isEmpty()) {
            escrever("execucoes", execId, "");
            escrever("execucoes", execId, "═".repeat(60));
            escrever("execucoes", execId, "ACÇÕES RECOMENDADAS");
            escrever("execucoes", execId, "═".repeat(60));

            if (!criticas.isEmpty()) {
                escrever("execucoes", execId, "");
                escrever("execucoes", execId, "🔴 CRÍTICO — Corrigir antes da próxima execução:");
                criticas.forEach(a -> escrever("execucoes", execId, "   → " + a));
            }

            if (!revisar.isEmpty()) {
                escrever("execucoes", execId, "");
                escrever("execucoes", execId, "⚠️  REVISAR — Elementos sem metadados suficientes:");
                revisar.forEach(a -> escrever("execucoes", execId, "   → " + a));
            }

            if (!bloq.isEmpty()) {
                escrever("execucoes", execId, "");
                escrever("execucoes", execId, "ℹ️  INFO — Steps bloqueados por cascata (não são falhas reais):");
                escrever("execucoes", execId, "   → Steps " + bloq.toString().replace("[","").replace("]",""));
                escrever("execucoes", execId, "   Corrigir o step que originou a cascata resolve automaticamente.");
            }
            escrever("execucoes", execId, "═".repeat(60));
        }
        info("Execução #" + execId + " concluída | passou=" + passou + " falhou=" + falhou + " bloqueados=" + bloqueados + " | " + pct + "%");
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    public void stepIniciado(Long execId, int num, String acao, String nomeElemento) {
        escrever("execucoes", execId, "");
        escrever("execucoes", execId, "[" + LocalDateTime.now().format(FMT_HORA) + "] "
                + "Step " + num + " — " + acao
                + (nomeElemento != null && !nomeElemento.isBlank() ? " [" + nomeElemento + "]" : ""));
    }

    public void stepPassou(Long execId, int num, String nomeElemento) {
        escrever("execucoes", execId, "           ✅ PASSOU");
    }

    public void stepFalhou(Long execId, int num, String nomeElemento, String motivoSelenium) {
        String motivo = simplificarErroSelenium(motivoSelenium);
        escrever("execucoes", execId, "           ❌ FALHOU — " + motivo);
        if (nomeElemento != null && !nomeElemento.isBlank()) {
            acoesCriticas.getOrDefault(execId, new ArrayList<>()).add(
                nomeElemento + " (step " + num + ") — " + motivo
            );
        }
    }

    public void stepBloqueado(Long execId, int num, String nomeElemento, int stepCausaRaiz) {
        escrever("execucoes", execId, "");
        escrever("execucoes", execId, "[" + LocalDateTime.now().format(FMT_HORA) + "] "
                + "Step " + num + " — ⏭ BLOQUEADO");
        escrever("execucoes", execId, "           Motivo: step " + stepCausaRaiz + " falhou (pré-requisito não cumprido)");
        escrever("execucoes", execId, "           Acção: não executado");
        stepsBloqueados.getOrDefault(execId, new ArrayList<>()).add(num);
    }

    // ── Self-Healing ──────────────────────────────────────────────────────────

    public void healingInicio(Long execId, int stepNum, String nomeElemento, String seletor) {
        escrever("execucoes", execId, "");
        escrever("execucoes", execId, "           🔄 Self-Healing activado");
        escrever("execucoes", execId, "           Selector original: " + seletor);
    }

    public void healingEstrategia(Long execId, int prioridade, String nome, boolean sucesso, String detalhe) {
        String icone = sucesso ? "✅" : "❌";
        boolean isUltima = prioridade == 7;
        String prefixo = isUltima ? "           └─" : "           ├─";
        String linha = String.format("%s Prioridade %d (%-20s) → %s%s",
                prefixo, prioridade, nome, icone,
                detalhe != null && !detalhe.isBlank() ? " — " + detalhe : "");
        escrever("execucoes", execId, linha);
    }

    public void healingCurado(Long execId, int prioridade, String estrategia, String novoSeletor) {
        escrever("execucoes", execId, "           └─ Selector curado e guardado no DB ✅");
        escrever("execucoes", execId, "           ✅ Recuperado via: " + estrategia);
        if (novoSeletor != null && !novoSeletor.isBlank()) {
            escrever("execucoes", execId, "           Novo selector: " + novoSeletor);
        }
    }

    public void healingFalhou(Long execId, String nomeElemento) {
        escrever("execucoes", execId, "           ❌ Self-Healing esgotou todas as estratégias");
        escrever("execucoes", execId, "           Sugestão: inspeccione o elemento no F12 e edite o selector em Elementos");
        if (nomeElemento != null) {
            acoesRevisar.getOrDefault(execId, new ArrayList<>()).add(
                nomeElemento + " — sem metadados suficientes para healing local"
            );
        }
    }

    public void healingIAFiltrado(Long execId, String seletorRuim, String motivo) {
        escrever("execucoes", execId, "           ⚠️  Selector da IA rejeitado automaticamente: " + motivo);
        escrever("execucoes", execId, "              Selector rejeitado: " + seletorRuim.substring(0, Math.min(80, seletorRuim.length())));
    }

    public void healingDiagnostico(Long execId, String diagnostico) {
        if (diagnostico != null && !diagnostico.isBlank()) {
            escrever("execucoes", execId, "           💡 Diagnóstico IA: " + diagnostico.substring(0, Math.min(200, diagnostico.length())));
        }
    }

    // ── Log do sistema ────────────────────────────────────────────────────────

    public void info(String mensagem) { escreverSistema("INFO ", mensagem); }
    public void warn(String mensagem) { escreverSistema("AVISO", mensagem); }
    public void erro(String mensagem) { escreverSistema("ERRO ", mensagem); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String simplificarErroSelenium(String erro) {
        if (erro == null) return "Erro desconhecido";
        if (erro.contains("visibility of element")) {
            int idx = erro.indexOf("By.cssSelector:");
            if (idx >= 0) {
                String sel = erro.substring(idx + 15).split("\\(tried")[0].trim();
                return "Elemento não visível após 10s — selector: " + sel;
            }
            return "Elemento não visível na página após 10s";
        }
        if (erro.contains("no such element")) return "Elemento não existe na página";
        if (erro.contains("element click intercepted")) return "Elemento bloqueado por outro elemento (modal/overlay)";
        if (erro.contains("stale element")) return "Elemento desapareceu após carregamento da página";
        if (erro.contains("timeout")) return "Tempo limite excedido";
        if (erro.contains("Elemento não encontrado")) return "Elemento não encontrado na biblioteca";
        // Remove stack trace - pegar só a primeira linha
        return erro.split("\n")[0].substring(0, Math.min(150, erro.split("\n")[0].length()));
    }

    private void escrever(String subpasta, Long execId, String linha) {
        try {
            Path dir = Paths.get(logsPath, subpasta);
            Files.createDirectories(dir);
            String nomeFicheiro = "execucao-" + execId + ".log";
            Path ficheiro = dir.resolve(nomeFicheiro);
            Files.writeString(ficheiro, linha + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[QorbitLogger] Erro ao escrever log: " + e.getMessage());
        }
    }

    private void escreverSistema(String nivel, String mensagem) {
        try {
            Path dir = Paths.get(logsPath, "sistema");
            Files.createDirectories(dir);
            String nomeFicheiro = "qorbit-" + LocalDate.now().format(FMT_DATA) + ".log";
            Path ficheiro = dir.resolve(nomeFicheiro);
            String linha = "[" + LocalDateTime.now().format(FMT) + "] " + nivel + " " + mensagem;
            Files.writeString(ficheiro, linha + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[QorbitLogger] Erro ao escrever log sistema: " + e.getMessage());
        }
    }
}
