package com.qorbit.engine.learning;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "qorbit.learning.sqlite.enabled", havingValue = "true", matchIfMissing = true)
public class SQLiteLearningService implements LearningService {

    private final JdbcTemplate jdbc;

    public SQLiteLearningService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        init();
    }

    private void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS estrategias_sucesso (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    assinatura_componente TEXT NOT NULL,
                    tipo_componente TEXT,
                    estrategia_usada TEXT,
                    sucessos INTEGER DEFAULT 0,
                    falhas INTEGER DEFAULT 0,
                    ultimo_uso TEXT,
                    UNIQUE(assinatura_componente, estrategia_usada)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cache_componente (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hash_componente TEXT UNIQUE,
                    tipo_funcional TEXT,
                    estrategia_recomendada TEXT,
                    confianca REAL DEFAULT 0.50,
                    falhas_consecutivas INTEGER DEFAULT 0,
                    ultimo_uso TEXT
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS estatisticas_tipo (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    tipo_componente TEXT UNIQUE,
                    total_execucoes INTEGER DEFAULT 0,
                    total_sucessos INTEGER DEFAULT 0,
                    total_falhas INTEGER DEFAULT 0
                )
                """);
    }

    @Override
    public void onCacheLookup(String signature) {
        jdbc.update("UPDATE cache_componente SET ultimo_uso = CURRENT_TIMESTAMP WHERE hash_componente = ?", signature);
    }

    @Override
    public String recommendStrategy(String signature, String componentType, String defaultStrategy) {
        List<Map<String, Object>> cacheRows = jdbc.queryForList(
                "SELECT estrategia_recomendada, confianca, falhas_consecutivas FROM cache_componente WHERE hash_componente = ?",
                signature);
        if (!cacheRows.isEmpty()) {
            Map<String, Object> row = cacheRows.get(0);
            Number confidence = (Number) row.get("confianca");
            Number consecutiveFailures = (Number) row.get("falhas_consecutivas");
            String cachedStrategy = (String) row.get("estrategia_recomendada");
            if (cachedStrategy != null && confidence != null && confidence.doubleValue() >= 0.60
                    && consecutiveFailures != null && consecutiveFailures.intValue() < 2) {
                System.out.println("[Qorbit Engine] cache hit para assinatura " + signature + " -> " + cachedStrategy);
                return cachedStrategy;
            }
        }
        System.out.println("[Qorbit Engine] cache miss para assinatura " + signature);

        List<Map<String, Object>> historicRows = jdbc.queryForList(
                "SELECT estrategia_usada, sucessos, falhas FROM estrategias_sucesso WHERE assinatura_componente = ? ORDER BY sucessos DESC, falhas ASC",
                signature);
        if (!historicRows.isEmpty()) {
            Map<String, Object> row = historicRows.get(0);
            int successes = ((Number) row.get("sucessos")).intValue();
            int failures = ((Number) row.get("falhas")).intValue();
            int total = successes + failures;
            if (total >= 5 && successes > failures) {
                return (String) row.get("estrategia_usada");
            }
        }

        return defaultStrategy;
    }

    @Override
    public void onSuccess(String signature, String strategy, String componentType) {
        upsertStrategy(signature, strategy, componentType, true);
        upsertStats(componentType, true);
        upsertCache(signature, componentType, strategy, true);
    }

    @Override
    public void onFailure(String signature, String strategy, String componentType, String reason) {
        upsertStrategy(signature, strategy, componentType, false);
        upsertStats(componentType, false);
        upsertCache(signature, componentType, strategy, false);
    }

    private void upsertStrategy(String signature, String strategy, String componentType, boolean success) {
        int updated = jdbc.update("""
                UPDATE estrategias_sucesso
                SET sucessos = sucessos + ?, falhas = falhas + ?, ultimo_uso = CURRENT_TIMESTAMP, tipo_componente = ?
                WHERE assinatura_componente = ? AND estrategia_usada = ?
                """, success ? 1 : 0, success ? 0 : 1, componentType, signature, strategy);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO estrategias_sucesso (assinatura_componente, tipo_componente, estrategia_usada, sucessos, falhas, ultimo_uso)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, signature, componentType, strategy, success ? 1 : 0, success ? 0 : 1);
        }
    }

    private void upsertStats(String componentType, boolean success) {
        int updated = jdbc.update("""
                UPDATE estatisticas_tipo
                SET total_execucoes = total_execucoes + 1,
                    total_sucessos = total_sucessos + ?,
                    total_falhas = total_falhas + ?
                WHERE tipo_componente = ?
                """, success ? 1 : 0, success ? 0 : 1, componentType);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO estatisticas_tipo (tipo_componente, total_execucoes, total_sucessos, total_falhas)
                    VALUES (?, 1, ?, ?)
                    """, componentType, success ? 1 : 0, success ? 0 : 1);
        }
    }

    private void upsertCache(String signature, String componentType, String strategy, boolean success) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT confianca, falhas_consecutivas FROM cache_componente WHERE hash_componente = ?",
                signature);

        if (rows.isEmpty()) {
            jdbc.update("""
                    INSERT INTO cache_componente (hash_componente, tipo_funcional, estrategia_recomendada, confianca, falhas_consecutivas, ultimo_uso)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, signature, componentType, strategy, success ? 0.65 : 0.40, success ? 0 : 1);
            return;
        }

        Map<String, Object> row = rows.get(0);
        double confidence = ((Number) row.get("confianca")).doubleValue();
        int failures = ((Number) row.get("falhas_consecutivas")).intValue();

        if (success) {
            confidence = Math.min(0.95, confidence + 0.08);
            failures = 0;
        } else {
            confidence = Math.max(0.20, confidence - 0.12);
            failures = failures + 1;
        }

        String strategyToSave = failures >= 2 ? null : strategy;
        jdbc.update("""
                UPDATE cache_componente
                SET tipo_funcional = ?, estrategia_recomendada = ?, confianca = ?, falhas_consecutivas = ?, ultimo_uso = CURRENT_TIMESTAMP
                WHERE hash_componente = ?
                """, componentType, strategyToSave, confidence, failures, signature);
    }

    @Override
    public String buildTextReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Qorbit Engine — Relatório de acurácia\n");
        sb.append("====================================\n\n");

        List<Map<String, Object>> stats = jdbc.queryForList(
                "SELECT tipo_componente, total_execucoes, total_sucessos, total_falhas FROM estatisticas_tipo ORDER BY total_execucoes DESC");
        sb.append("Taxa por tipo:\n");
        if (stats.isEmpty()) {
            sb.append("- Nenhum dado de aprendizado disponível.\n\n");
        } else {
            for (Map<String, Object> row : stats) {
                int total = ((Number) row.get("total_execucoes")).intValue();
                int success = ((Number) row.get("total_sucessos")).intValue();
                int failures = ((Number) row.get("total_falhas")).intValue();
                double rate = total > 0 ? (success * 100.0 / total) : 0;
                sb.append(String.format("- %s: %d execuções | %d sucessos | %d falhas | %.0f%%\n",
                        row.get("tipo_componente"), total, success, failures, rate));
            }
            sb.append("\n");
        }

        sb.append("Estratégias mais usadas:\n");
        List<Map<String, Object>> strategies = jdbc.queryForList(
                "SELECT estrategia_usada, SUM(sucessos) as sucessos, SUM(falhas) as falhas FROM estrategias_sucesso GROUP BY estrategia_usada ORDER BY sucessos DESC, falhas ASC");
        if (strategies.isEmpty()) {
            sb.append("- Nenhuma estratégia registrada.\n");
        } else {
            for (Map<String, Object> row : strategies) {
                int success = ((Number) row.get("sucessos")).intValue();
                int failures = ((Number) row.get("falhas")).intValue();
                int total = success + failures;
                double rate = total > 0 ? (success * 100.0 / total) : 0;
                sb.append(String.format("- %s: %d sucessos | %d falhas | %.0f%%\n",
                        row.get("estrategia_usada"), success, failures, rate));
            }
        }
        return sb.toString();
    }

    @Override
    public String buildJsonReport() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("componentes", jdbc.queryForList("SELECT tipo_componente, total_execucoes, total_sucessos, total_falhas FROM estatisticas_tipo ORDER BY total_execucoes DESC"));
        payload.put("estrategias", jdbc.queryForList("SELECT estrategia_usada, SUM(sucessos) as sucessos, SUM(falhas) as falhas FROM estrategias_sucesso GROUP BY estrategia_usada ORDER BY sucessos DESC, falhas ASC"));
        payload.put("cache", jdbc.queryForList("SELECT hash_componente, tipo_funcional, estrategia_recomendada, confianca, falhas_consecutivas FROM cache_componente ORDER BY ultimo_uso DESC LIMIT 20"));
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"erro\":\"falha ao gerar json\"}";
        }
    }
}
