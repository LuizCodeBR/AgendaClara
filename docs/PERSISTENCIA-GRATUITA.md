# Persistência online gratuita — Agenda Clara 0.7.0

## Configuração adotada em 13/11/2026
O usuário autorizou somente planos gratuitos. O site continua no Render Free (My Workspace) e o banco foi criado no Neon, em organização com plano Free. Nenhum PostgreSQL pago, disco, worker ou tarefa de nuvem paga foi contratado. A proposta paga do Render foi recusada.

| Componente | Configuração |
| --- | --- |
| Site e API | Render Free, serviço agenda-clara-luizcodebr, Virginia |
| Banco | PostgreSQL 17, projeto Agenda Clara no Neon Free, AWS us-east-1 |
| Capacidade de processamento | mínimo e máximo 0,25 CU, sem aumento automático de capacidade |
| Conexão | host direto, URL JDBC, sslmode=require e channelBinding=require |
| Migrações | seis migrações Flyway aplicadas na inicialização; conexão direta, sem intermediário de transações |
| Conexões da aplicação | Hikari máximo 5 e mínimo ocioso 0 |
| Armazenamento | STORAGE_MODE=persistent e SPRING_PROFILES_ACTIVE=postgres |
| Demonstração | APP_DEMO_ENABLED=true, APP_PUBLIC_DEMO=true e MAIL_MODE=demo |
| Chave dos e-mails | MAIL_ENCRYPTION_KEY estável, mantida fora do banco e do Git |
| Web/PWA | mesmo servidor e banco; a cópia local permanece independente |

O serviço existente é configurado por mesclagem de variáveis no Render. O render.yaml acompanha os valores não sigilosos; DB_URL, DB_USER, DB_PASSWORD e MAIL_ENCRYPTION_KEY são entradas privadas com sync:false. Não há Blueprint vinculado ou criação de infraestrutura adicional por este arquivo nesta operação. Uma futura implantação deve fornecer essas entradas, sem copiar valores para o repositório.

## Persistência e transição
O banco começou vazio, com os exemplos fictícios. Não houve conversão nem transferência automática dos dados H2 temporários do Render. Os dados criados no novo banco ficam fora do disco descartável do servidor web e sobrevivem à sua substituição.

A autenticação usa sessões de servidor; uma nova implantação pode exigir entrar novamente. Isso não representa perda da conta. A chave da fila precisa permanecer a mesma para recuperar conteúdos criptografados. Nenhum e-mail real é enviado enquanto MAIL_MODE=demo.

## Limites gratuitos e recuperação
Valores oficiais consultados em 13/11/2026: Neon Free oferece 1 GB por projeto, 100 CU-h de processamento por mês e 5 GB de transferência pública por mês. A recuperação instantânea alcança até seis horas, limitada também a 1 GB de histórico; há uma snapshot manual por projeto. Não há snapshots agendadas no Free. O projeto foi criado com history_retention_seconds=21600.

A documentação do Neon informa suspensão após cinco minutos de inatividade no Free, sem opção de desativar. A API retornou suspend_timeout_seconds=0 para este projeto, e recusou personalizar o intervalo na criação; nenhum ajuste de plano foi feito. Esse campo não foi tomado como prova de disponibilidade contínua. O Render Free também suspende por inatividade. Não são usados acessos artificiais para mantê-los ativos.

Atingir cotas gratuitas pode suspender processamento ou impedir novas gravações. Isso exige acompanhamento; a solução não é um upgrade automático. A fila só é processada enquanto a aplicação estiver ativa. As regras próprias de expiração de tokens e retenção de mensagens continuam valendo. Um serviço de uso comercial requer operação e recuperação adequadas ao negócio.

Snapshot e histórico do provedor pertencem à mesma conta; uma cópia exportada, criptografada e guardada fora dela continua necessária. O backup da aplicação atende conjuntos de até 64 MiB; o limite gratuito de 1 GB não amplia esse limite da ferramenta.

## Arquivos privados no D:
A configuração e os segredos operacionais ficam em D:/Projetos/AgendaClara/.cache/neon-private, com acesso restrito ao usuário Windows atual. A pasta .cache é ignorada pelo Git e não entra nos pacotes públicos.

- connection.json: configuração privada do PostgreSQL; não compartilhar.
- mail-outbox.key: chave estável dos e-mails; não apagar ou substituir.
- backup-password.txt: senha exclusiva da cópia criptografada; guardar também em um gerenciador seguro, separada do backup.
- neon-2026-11-13.acbackup: exportação criptografada do banco e da chave.
- persistence-proof-v2.json: dados fictícios e prova de acesso utilizados na validação; não publicar.

Guardar senha, chave e backup somente neste disco não protege contra a perda do computador. Faça uma segunda cópia protegida em outro local. Não envie esses arquivos para o GitHub nem cole seus conteúdos em conversas.

Os clientes oficiais PostgreSQL 17.12 foram extraídos em D:/Projetos/AgendaClara/.cache/postgres-tools/pgsql. São ferramentas portáteis; não foi instalado serviço do Windows, extensão do VS Code nem modificado o PATH permanente. Para rodar o pacote local H2, permanece suficiente Java 21 a 27.

## Repetir backup e restauração
Consulte [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md) para uso de backup.cmd, variáveis privadas e restauração. Para o Neon, use a conexão direta com TLS e PG_DUMP_PATH apontando ao pg_dump.exe portátil. Não coloque senha na linha de comando. Preserve a chave original. Verifique a cópia e restaure-a apenas em banco vazio e exclusivo de teste, com PG_RESTORE_PATH configurado; nunca use o banco online como destino de ensaio.

A exportação nativa pode ocorrer com o site em execução. A restauração não usa --clean, recusa destino preenchido e não converte PostgreSQL em H2. O teste de recuperação deve incluir login e leitura da mesma mensagem criptografada, além da integridade do arquivo.

Nenhuma tarefa recorrente de backup, exclusão automática de cópias, alerta externo ou contratação foi criada. Os próximos backups são operações manuais até ser definida uma rotina com destino e retenção apropriados.

## Fontes
- [Plano gratuito e preços do Neon](https://neon.com/pricing).
- [Cotas, snapshots e recuperação do Neon](https://neon.com/docs/introduction/plans).
- [Conexão Java e TLS](https://neon.com/docs/guides/java).
- [Conexões diretas e pooling](https://neon.com/docs/connect/connection-pooling).
- [Limites do Render Free](https://render.com/docs/free).
- [Clientes Windows oficiais](https://www.postgresql.org/download/windows/) e [arquivos portáteis EDB](https://www.enterprisedb.com/download-postgresql-binaries).
