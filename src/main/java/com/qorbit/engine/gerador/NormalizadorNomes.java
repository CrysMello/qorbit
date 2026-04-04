package com.qorbit.engine.gerador;

import com.qorbit.engine.model.Elemento;
import com.qorbit.engine.model.StepTeste;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class NormalizadorNomes {

    private static final Pattern NON_ASCII = Pattern.compile("[^\\p{ASCII}]");
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract","assert","boolean","break","byte","case","catch","char","class","const",
            "continue","default","do","double","else","enum","extends","final","finally","float",
            "for","goto","if","implements","import","instanceof","int","interface","long","native",
            "new","package","private","protected","public","return","short","static","strictfp",
            "super","switch","synchronized","this","throw","throws","transient","try","void",
            "volatile","while","record","sealed","permits","non-sealed","var","yield"
    );

    private final Map<String, String> cacheNomes = new ConcurrentHashMap<>();

    // Detecta strings que sao valores de data ou numeros puros — nao devem virar nomes de elemento
    private static final java.util.regex.Pattern PARECE_VALOR_DATA = java.util.regex.Pattern.compile(
            "^\\d{4,8}$"
            + "|^\\d{1,2}[/\\-.\\s]\\d{1,2}[/\\-.\\s]\\d{2,4}$"
            + "|^\\d{4}[/\\-]\\d{2}[/\\-]\\d{2}$"
    );

    public String normalizarNomeLogico(String valor, String prefixoPadrao) {
        // Se o valor inteiro parecer uma data ou numero puro, descarta e usa so o prefixo.
        // Isso evita que "campo20150604" e "campo20150624" sejam tratados como elementos
        // distintos na biblioteca quando sao o mesmo campo com valores diferentes.
        String valorTrim = valor != null ? valor.trim().replaceAll("\\s+", "") : "";
        if (!valorTrim.isEmpty() && PARECE_VALOR_DATA.matcher(valorTrim).matches()) {
            valor = null;
        }
        String base = sanitizeToCamel(valor, false);
        if (base.isBlank()) {
            base = sanitizeToCamel(prefixoPadrao, false);
        }
        if (base.isBlank()) {
            base = "elemento";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = sanitizeToCamel(prefixoPadrao, false) + capitalize(base);
        }
        if (JAVA_KEYWORDS.contains(base)) {
            base = base + "Campo";
        }
        return lowerFirst(base);
    }

    public String normalizarClasse(String texto, String fallback) {
        String base = sanitizeToCamel(texto, true);
        if (base.isBlank()) {
            base = sanitizeToCamel(fallback, true);
        }
        if (base.isBlank()) {
            base = "Pagina";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "Pagina" + base;
        }
        if (JAVA_KEYWORDS.contains(base.toLowerCase(Locale.ROOT))) {
            base = "Pagina" + base;
        }
        return capitalize(base);
    }

    public String nomeMetodoParaElemento(Elemento elemento, String fallbackPrefix) {
        String chave = String.join("|",
                Optional.ofNullable(elemento.getPagina()).orElse(""),
                Optional.ofNullable(elemento.getNomeLogico()).orElse(""),
                Optional.ofNullable(elemento.getTipoSeletor()).orElse(""),
                Optional.ofNullable(elemento.getSeletorTecnico()).orElse(""));
        return cacheNomes.computeIfAbsent(chave, k -> normalizarNomeLogico(elemento.getNomeLogico(), fallbackPrefix));
    }

    public String nomeMetodoParaStep(StepTeste step, String fallbackPrefix) {
        String base = step != null ? step.getNomeLogicoElemento() : null;
        return normalizarNomeLogico(base, fallbackPrefix);
    }

    public String tornarUnico(String base, Set<String> usados) {
        String candidato = base;
        int contador = 2;
        while (usados.contains(candidato)) {
            candidato = base + contador;
            contador++;
        }
        usados.add(candidato);
        return candidato;
    }

    public String literalJava(String texto) {
        if (texto == null) return "";
        // Normaliza: desfaz escapes extras que o banco possa ter acumulado
        String s = texto;
        String anterior;
        do {
            anterior = s;
            s = s.replace("\\\"", "\"").replace("\\\\", "\\");
        } while (!s.equals(anterior));
        // Re-escapa do zero para uso em string literal Java
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    public String stripAccents(String texto) {
        if (texto == null) return "";
        String normalized = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return NON_ASCII.matcher(normalized).replaceAll("");
    }

    // Palavras sem significado semântico a ignorar nos nomes
    private static final Set<String> PALAVRAS_IGNORAR = Set.of(
        "de", "da", "do", "dos", "das", "e", "em", "o", "a", "os", "as",
        "um", "uma", "para", "com", "por", "que", "se", "na", "no",
        "the", "of", "and", "or", "in", "to", "an", "for", "is"
    );
    private static final int MAX_PALAVRAS_NOME = 4;

    private String sanitizeToCamel(String texto, boolean upperFirst) {
        String limpo = stripAccents(Optional.ofNullable(texto).orElse(""))
                .replaceAll("[^a-zA-Z0-9]+", " ")
                .trim();
        if (limpo.isBlank()) return "";
        String[] todasPartes = limpo.split("\\s+");

        // Melhoria 7: filtra palavras sem significado e limita a MAX_PALAVRAS_NOME
        List<String> partesFiltradas = new java.util.ArrayList<>();
        for (String p : todasPartes) {
            if (p.isBlank()) continue;
            if (!PALAVRAS_IGNORAR.contains(p.toLowerCase(Locale.ROOT))) {
                partesFiltradas.add(p);
            }
            if (partesFiltradas.size() >= MAX_PALAVRAS_NOME) break;
        }
        // Se após filtrar ficou vazio, usa as primeiras palavras sem filtro
        if (partesFiltradas.isEmpty()) {
            for (String p : todasPartes) {
                if (!p.isBlank()) partesFiltradas.add(p);
                if (partesFiltradas.size() >= MAX_PALAVRAS_NOME) break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partesFiltradas.size(); i++) {
            String p = partesFiltradas.get(i);
            String token = p.toLowerCase(Locale.ROOT);
            if (i == 0 && !upperFirst) {
                sb.append(token);
            } else {
                sb.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
            }
        }
        return sb.toString();
    }

    private String capitalize(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String lowerFirst(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }
}
