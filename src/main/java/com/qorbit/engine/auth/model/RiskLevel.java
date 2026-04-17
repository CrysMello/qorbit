package com.qorbit.engine.auth.model;

/** Classificação de risco das ações auditadas. */
public enum RiskLevel {
    LOW,      // leitura, navegação
    MEDIUM,   // login, alteração de dados próprios
    HIGH,     // reset de senha, alteração de e-mail, ativação de MFA
    CRITICAL  // ações destrutivas, gestão de admins, export de dados
}
