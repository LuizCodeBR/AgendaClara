# Armazenamento e backups — 0.7.0

## O que está disponível
- Local H2: dados em arquivo e backup criptografado do banco junto da chave original dos e-mails.
- PostgreSQL: exportação nativa consistente com pg_dump e restauração com pg_restore em banco vazio.
- Web e mobile/PWA: usam o mesmo servidor e refletem o modo de armazenamento configurado. O navegador não guarda uma cópia do banco.
- Online: Render Free com PostgreSQL 17 no Neon Free e chave estável fora do banco; detalhes em [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md). A proposta paga do Render foi recusada e não foi executada.

Persistência conserva os dados entre reinícios; backup oferece uma cópia para recuperar perda ou alteração indevida. Um arquivo salvo no mesmo disco do banco não cobre a perda física desse disco.

## Backup local, somente com Java
No pacote compilado, abra o terminal na pasta extraída no D:. No código-fonte, compile primeiro com iniciar.cmd e encerre o servidor.

1. Encerre a Agenda Clara com Ctrl+C. Aguarde o Java liberar o banco.
2. Execute:
   `.\backup.cmd`
3. Informe e confirme uma senha exclusiva para proteger o backup. Ela não aparece no terminal.
4. Guarde a senha com segurança e preserve o arquivo .acbackup criado em backups.

No Linux/macOS: `bash backup.sh`. A cópia local não exige Node, Docker ou cliente PostgreSQL. Não é preciso extensão do VS Code.

Para escolher origem e destino:
```powershell
.\backup.cmd --backup=create --data-dir="D:\AgendaClara\data" --output="D:\Backups\agenda-2026-11-13.acbackup"
```

O servidor em uso é recusado por bloqueio do arquivo. Não se copia o banco enquanto ele está sendo alterado. A rotina confere esquema e identidade da chave antes de publicar o backup. Arquivo de destino existente nunca é substituído.

## Verificar e restaurar
Verificação de senha, autenticação, estrutura e hashes:
```powershell
.\backup.cmd --backup=verify --input="D:\Backups\agenda-2026-11-13.acbackup"
```

Restaurar em uma pasta que **ainda não exista**:
```powershell
.\backup.cmd --backup=restore --input="D:\Backups\agenda-2026-11-13.acbackup" --target-dir="D:\AgendaRestaurada\data"
```

A senha deve ser a utilizada na criação. A autenticação é conferida antes de extrair qualquer dado. O banco H2 restaurado é aberto em modo de leitura para conferir esquema e chave antes de publicar os arquivos no novo destino.

Para testar a cópia restaurada pelo código-fonte:
```powershell
$env:AGENDA_DATA_DIR = 'D:\AgendaRestaurada\data'
.\iniciar.cmd -SkipBuild -Port 8081
```

No pacote compilado, configure a mesma variável e execute iniciar-demo.cmd --server.port=8081. Abra http://localhost:8081. A instalação original é preservada. Links de e-mail já gravados mantêm seu endereço original; ao mudar a origem da instalação, solicite links novos.

A verificação de um arquivo não substitui um ensaio de restauração: entre na conta, confira empresas, reservas e mensagens na cópia restaurada antes de decidir qual instalação utilizar.

## Proteção e limites
O formato ACBK0001 contém um ZIP autenticado por AES-256-GCM, com salt de 16 bytes e nonce de 12 bytes aleatórios. A chave do arquivo é derivada por PBKDF2-HMAC-SHA256 com 600 mil iterações. O manifesto registra versão do aplicativo, motor, esquema, criação, identidade da chave e SHA-256 dos arquivos.

O conjunto inclui a chave original da fila. Por isso, o backup inteiro é protegido por senha e as permissões dos arquivos e temporários ficam restritas ao proprietário. A senha não vai no nome do arquivo, nos argumentos do Java ou nos logs. Ela não é recuperável pelo sistema.

Esta ferramenta atende conjuntos pequenos: limite de 64 MiB para o conteúdo e o arquivo comprimido. Os iniciadores reservam até 512 MiB de memória para a operação. Para bases maiores, utilize as ferramentas nativas e um procedimento de criptografia/armazenamento apropriado, além do backup gerenciado do provedor.

Temporários ficam em tmp no pacote e .cache/tmp no projeto. Os arquivos intermediários necessários ao PostgreSQL são privados e removidos após a operação. O processo pode deixar temporários privados se o computador for desligado abruptamente; confira essa pasta na manutenção.

Nenhum backup antigo é apagado automaticamente. Não foi criada tarefa agendada no computador. Faça cópias periódicas, confira sua recuperação e mantenha uma segunda cópia protegida fora do disco do banco. Não envie backups ou senhas ao GitHub público.

## PostgreSQL
Instale ferramentas cliente PostgreSQL 17 compatíveis com o servidor, ou use as ferramentas do contêiner PostgreSQL existente. Os clientes PostgreSQL 17.12 portáteis foram extraídos em D:/Projetos/AgendaClara/.cache/postgres-tools/pgsql/bin para validar o Neon. Não foi instalado serviço do Windows nem extensão do editor; clientes e Docker são opcionais para o uso local H2.

Configure DB_URL (JDBC), DB_USER e DB_PASSWORD em um ambiente privado. Para acesso externo ao Render, use a URL externa e TLS conforme a documentação do provedor. Configure também MAIL_ENCRYPTION_KEY ou MAIL_KEY_FILE apontando para a chave original.

```powershell
.\backup.cmd --backup=create-postgres --output="D:\Backups\agenda-postgres.acbackup"
```

O pg_dump produz arquivo custom consistente mesmo com gravações concorrentes. Credenciais são transmitidas por variáveis de ambiente do processo, sem senha na linha de comando. O export é protegido antes de ser publicado como .acbackup.

Para Docker, AGENDA_PG_CONTAINER identifica o contêiner existente que contém pg_dump e pg_restore. A conexão DB_URL também deve ser válida de dentro desse contêiner; portas mapeadas do host podem diferir da porta interna. Sem essa opção, PG_DUMP_PATH e PG_RESTORE_PATH podem indicar os executáveis locais.

Para restauração, configure as variáveis para **outro banco vazio e exclusivo**, nunca o original:
```powershell
.\backup.cmd --backup=restore-postgres --input="D:\Backups\agenda-postgres.acbackup" --target-dir="D:\ChaveRestaurada"
```

A ferramenta recusa banco não vazio e pasta existente. Usa pg_restore com transação única, encerramento no primeiro erro, sem limpar dados e sem restaurar proprietário/privilégios. Na versão 0.8.1, a chave é preservada em nova pasta privada antes da restauração nativa. Essa pasta pode permanecer se houver falha; confira o resultado antes de utilizar o banco. O operador precisa impedir o uso concorrente do banco de destino durante essa manutenção.

Não se converte H2 em PostgreSQL automaticamente. São motores diferentes, e uma migração de dados exige procedimento próprio. O backup também não inclui configurações externas, credenciais de envio, domínio, regras de rede ou contas do provedor; preserve essas configurações separadamente.

## Configuração do aplicativo
| Variável | Uso |
| --- | --- |
| STORAGE_MODE=local | arquivo local ou instalação própria; padrão |
| STORAGE_MODE=ephemeral | hospedagem com banco em disco descartável; não é o modo online atual |
| STORAGE_MODE=persistent | PostgreSQL durável e chave de e-mail explicitamente estável |
| AGENDA_DATA_DIR | pasta do banco H2/chave local |
| MAIL_ENCRYPTION_KEY | chave da fila, mantida como segredo privado |
| MAIL_KEY_FILE | caminho alternativo para a chave |
| AGENDA_BACKUP_PASSWORD | opção privada para execução sem terminal interativo; não guardar em scripts versionados |

O modo persistent recusa H2 e recusa envio ativo sem MAIL_ENCRYPTION_KEY explícita. O aviso de cadastro deixa de prometer descarte em reinícios quando o armazenamento é persistente, mantendo o aceite de dados fictícios da demonstração. O modo é uma configuração operacional; não é monitor independente da saúde do disco.

Não existe endpoint para baixar ou restaurar o banco pelo painel das empresas. Uma exportação integral contém dados de todos os estabelecimentos e é exclusiva do administrador da instalação.

## Referências e validação
- [Validação da entrega](VALIDACAO-0.7.0.md).
- [H2: backup com banco fechado](https://h2database.com/html/features.html#database_file_layout).
- [PostgreSQL 17: pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html).
- [Render: recuperação e backups](https://render.com/docs/postgresql-backups).
- [Limites do Render gratuito](https://render.com/docs/free).

## Rotina sob demanda (0.8.0)
O responsável pela instalação pode criar e verificar uma cópia, preservar o último resultado válido e consultar um relatório privado de limites com operacao.cmd/operacao.sh. Site e PWA seguem usando o mesmo banco persistente; não há nova exportação global por HTTP. Conforme a escolha do usuário, nenhum agendamento foi ativado. Consulte [OPERACAO-BACKUPS-LIMITES.md](OPERACAO-BACKUPS-LIMITES.md).

## Compatibilidade na 0.9.0

A inspeção aceita os esquemas 6 e 7. A migração V7 acrescenta os serviços por profissional; a cópia e restauração incluem essa tabela. O formato ACBK0001 permanece. Use ferramenta da versão nova para restaurar banco migrado e preserve banco e chave em conjunto.

## Compatibilidade na 0.10.0

A inspeção aceita os esquemas 6, 7 e 8. A migração V8 acrescenta acessos e sessões de clientes, incluídos no backup. A chave original preserva a derivação dos links privados; restaure banco e chave juntos. As sessões continuam sujeitas à expiração de uma hora, mesmo depois de uma restauração. Formato ACBK0001 mantido.

## Compatibilidade na 0.11.0
A ferramenta aceita esquemas 6, 7, 8 e 9. Backups incluem as regras atuais da empresa e as condições preservadas de cada reserva, além da chave e dos acessos privados. ACBK0001 permanece. Use a ferramenta nova para o banco migrado e mantenha banco e chave juntos.
