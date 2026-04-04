package com.qorbit.engine.diagnostic;

import org.springframework.stereotype.Service;

@Service
public class DefaultDiagnosticService implements DiagnosticService {
    @Override
    public String summarize(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) return "Erro não identificado";
        String message = throwable.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("elemento não encontrado na biblioteca") || lower.contains("elemento não encontrado")) {
            return "Falha de modelagem: elemento não cadastrado ou nome lógico incorreto";
        }
        if (lower.contains("invalid element state")) {
            return "Falha de estratégia: ação incompatível com o tipo do componente";
        }
        if (lower.contains("not interactable") || lower.contains("element click intercepted")) {
            return "Falha de interagibilidade: elemento existe, mas não está pronto para interação";
        }
        if (lower.contains("visible") || lower.contains("visibility") || lower.contains("clicável")) {
            return "Falha de sincronização: elemento localizado, mas ainda não interagível";
        }
        if (lower.contains("cannot locate option")) {
            return "Falha de seleção: componente não é select nativo ou a opção não está disponível";
        }
        if (lower.contains("valor não aplicado")) {
            return "Falha de validação: a execução não confirmou que o valor foi aplicado no componente";
        }
        if (lower.contains("datepicker") || lower.contains("data")) {
            return "Falha de estratégia de data: o componente exige interação diferente de input simples";
        }
        if (lower.contains("combobox") || lower.contains("listbox") || lower.contains("dropdown")) {
            return "Falha de estratégia de combobox: dropdown customizado exige abertura e seleção contextual";
        }
        return "Falha de execução do step";
    }
}
