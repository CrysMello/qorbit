CI/CD Build & Deploy
=====================

Este repositório inclui um GitHub Actions workflow (`.github/workflows/ci-cd-deploy.yml`) que:

- Compila o projeto com Maven (`mvn -DskipTests package`).
- Copia o JAR gerado para o servidor de produção via SCP.
- Copia um `systemd` drop-in (`/etc/systemd/system/qorbit.service.d/override.conf`) contendo variáveis de ambiente para o serviço.
- Reinicia `qorbit.service` e exibe os últimos logs.

Secrets necessários (defina em Settings → Secrets na repo):

- `SSH_HOST` — host ou IP do servidor (ex: `qorbit.duckdns.org`).
- `SSH_USER` — usuário SSH com permissão sudo (ex: `root` ou um usuário com sudo sem senha).
- `SSH_KEY` — chave privada SSH (PEM) do usuário acima.
- `SSH_PORT` — porta SSH (opcional, default 22).

Observações importantes
- O workflow copia o JAR para `/tmp/qorbit.jar` e move para `/opt/qorbit/qorbit.jar` no servidor (substitui o JAR existente).
- O drop-in systemd é escrito para `/etc/systemd/system/qorbit.service.d/override.conf`. Se preferir outro caminho/flag, ajuste o workflow.
- O usuário SSH precisa permissão para rodar `sudo mv` e `sudo systemctl daemon-reload/restart`. Configure `/etc/sudoers` apropriadamente (ex: `deploy ALL=(ALL) NOPASSWD: /bin/mv, /bin/systemctl, /bin/cp, /bin/mkdir, /bin/journalctl`).
- Armazene a chave privada como secret; nunca comite chaves privadas no repo.

Customizações
- Se quiser usar um caminho remoto diferente para o JAR ou outras JVM flags, ajuste `ci-cd-deploy.yml` (arquivo `Prepare systemd override file` e comandos SSH).

Próximos passos sugeridos
- Teste o workflow em uma branch separada antes de usar `main`.
- Após validar, monitore `journalctl -u qorbit.service` para confirmar que o serviço subiu e que o chromedriver log (`/var/log/chromedriver.log`) é gerado.
