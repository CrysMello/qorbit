# Roadmap

## Entrega 1
- reestruturação modular da arquitetura
- pacote base migrado para `com.qorbit.engine`
- pipeline de execução preparado para classificação e estratégia

## Entrega 2
- classificação funcional real
- strategy engine com fallback compatível
- aprendizado persistente em SQLite
- cache por assinatura
- relatórios CLI
- reforço de segurança para variáveis de ambiente da IA

## Entrega 2.2
- integração de `recommendStrategy()` no `StepExecutionPipeline` — aprendizado influencia execução
- suporte real a autocomplete com `AutocompleteExecutionStrategy`
- detecção de autocomplete por `aria-autocomplete`, datalist, e classes comuns
- remoção de referências internas no placeholder do front

## Entrega 2.3
- reescrita completa da `DateInputExecutionStrategy` com suporte robusto a datepickers
- detecção de campo readonly: pula digitação e aciona direto a estratégia visual
- tentativa de sendKeys com validação real + fallback via JavaScript
- abertura de calendário: clique no campo ou trigger associado com espera de visibilidade no DOM
- navegação de ano via seletor dedicado (`<select>`, input numérico) ou visão de anos (Angular Material)
- navegação de mês via botões next/prev com detecção do cabeçalho de mês/ano atual
- seleção de dia com distinção entre dia desabilitado e dia não encontrado
- suporte a range datepickers (dois calendários): resolução por aria-controls ou posição no DOM
- validação pós-seleção com fallback JS se o campo não refletir o valor esperado
- três exceções específicas: `DatePickerCalendarNotOpenedException`, `DatePickerDateNotFoundException`, `DatePickerDateDisabledException`
- logs estruturados com execId, estratégia usada, navegação realizada e motivo de falha
- compatibilidade validada com: jQuery UI, Flatpickr, React Datepicker, Angular Material, Ant Design, Pikaday, Air Datepicker e datepickers customizados com ARIA
- suíte de testes unitários com 12 cenários cobrindo todos os critérios de aceite

## Próximos passos sugeridos
- calibrar heurísticas de datepicker por biblioteca visual
- refinar suporte a autocomplete assíncrono
- adicionar painel administrativo de acurácia
- melhorar exportação e limpeza do banco de aprendizado
