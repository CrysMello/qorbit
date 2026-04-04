# Política de Privacidade — Qorbit

*Última actualização: Abril 2026*

---

## 1. Dados Recolhidos

O Qorbit opera **100% localmente**. Os dados armazenados são:

| Dado | Onde | Para quê |
|---|---|---|
| Elementos capturados | `scanner.db` (local) | Biblioteca de elementos POM |
| Casos de teste | `scanner.db` (local) | Execução e geração de código |
| Execuções e evidências | `evidencias/` (local) | Relatórios e screenshots |
| Configuração da IA | `application.properties` (local) | Endpoint e chave da API |
| Metadados de elementos | `scanner.db` (local) | id, name, texto, tag — para Self-Healing |

---

## 2. Dados NÃO Recolhidos

O Qorbit **não** recolhe, transmite nem armazena em servidores externos:

- Dados pessoais dos utilizadores
- Credenciais de acesso às aplicações testadas
- Screenshots ou evidências de execução
- Chaves de API configuradas
- Telemetria ou analytics de uso

---

## 3. Comunicações Externas

O Qorbit comunica com serviços externos **apenas quando configurado pelo utilizador:**

- **API de IA** (Groq, OpenAI, etc.) — apenas quando o Plugin IA está activo
- **Selenium WebDriver** — comunicação local com o browser instalado
- **WebDriverManager** — download automático de drivers de browser (Maven Central)

---

## 4. Dados Enviados à IA

Quando o Plugin IA está activo, os seguintes dados podem ser enviados à API configurada:

- HTML parcial de páginas (para detecção de elementos e auto-healing)
- Mensagens de erro do Selenium (para diagnóstico de falhas)
- Nomes e selectores de elementos (para naming inteligente)

**Nenhum dado pessoal** das aplicações testadas é intencionalmente enviado à IA.

---

## 5. Preparação para SaaS (futuro)

Esta política será actualizada quando o Qorbit evoluir para SaaS,
incluindo: isolamento de dados por tenant, conformidade LGPD/GDPR,
retenção e eliminação de dados.

---

*O Qorbit foi desenvolvido com respeito à privacidade como princípio de design.*
