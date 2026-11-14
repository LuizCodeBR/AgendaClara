# Operação sob demanda: backups e limites — 0.11.0

## Decisão de execução
Nesta etapa o usuário escolheu não agendar backup diário e consultar os limites somente sob demanda. Não há tarefa ativa no Codex, Agendador do Windows, GitHub Actions com horário, cron, Render ou Neon. Os comandos abaixo automatizam as etapas de uma execução solicitada, sem repetição posterior.

## O que a rotina faz
1. Abre uma pasta operacional exclusiva e obtém um bloqueio de execução.
2. No modo backup, confere espaço livre, cria um arquivo criptografado novo e verifica senha, autenticação, manifesto, hashes e identidade da chave usando a ferramenta já existente.
3. Somente após a verificação registra a última cópia válida e seu SHA-256.
4. Se houver falha, mantém a cópia anterior e registra a tentativa sem divulgar erros do banco ou credenciais.
5. Mede o arquivo H2 ou executa apenas pg_database_size(current_database()) no PostgreSQL.
6. Interpreta observações de consumo, distinguindo ausência, dado antigo e período encerrado.
7. Gera latest.json e latest.html privados. A página pode ser aberta no navegador sem instalar um servidor adicional.

O relatório atual confere o SHA-256 do último arquivo. A verificação criptográfica completa ocorreu na criação; é necessário também ensaiar restauração em destino separado. Não há exclusão automática de cópias. Pouco espaço livre é uma falha, não autorização para apagar arquivos.

## Local: pacote compilado, apenas Java
Abra o terminal na pasta extraída no D:. Para consultar o estado sem criar outra cópia:

~~~powershell
.\operacao.cmd
~~~

Para criar, verificar e registrar um backup do H2, primeiro encerre o servidor local com Ctrl+C:

~~~powershell
.\operacao.cmd --operations=backup
~~~

A senha exclusiva é solicitada e confirmada sem aparecer no terminal. Não informe senha como argumento. Para automação não interativa, AGENDA_BACKUP_PASSWORD pode ser fornecida por um ambiente privado. Nenhum agendamento é criado.

No Linux/macOS, use bash operacao.sh com as mesmas opções. No código-fonte, compile antes com iniciar.cmd e encerre o servidor. A rotina usa o JAR 0.11.0; o pacote distribuído usa agenda-clara.jar.

O resultado fica em operations/latest.html e operations/latest.json, com cópias em operations/backups. A pasta operations é privada e ignorada pelo Git. Para escolher outra pasta, use --work-dir apontando a uma pasta nova ou vazia. A ferramenta recusa pastas existentes com outros arquivos, links simbólicos e troca de motor ou de origem no mesmo histórico.

## Neon desta instalação no D:
O adaptador Windows reutiliza a configuração privada criada na etapa anterior, sem senhas no script. Para apenas consultar:

~~~powershell
.\operacao-neon.cmd
~~~

Para executar o backup e sua verificação:

~~~powershell
.\operacao-neon.cmd -Action backup
~~~

É equivalente a executar scripts/operacao-neon.ps1 com -Action report ou backup. O iniciador libera a execução do PowerShell apenas nesse processo; não altera a política permanente.

A configuração fica em D:/Projetos/AgendaClara/.cache/neon-private:
- connection.json: url JDBC direta, user e password, mantidos privados.
- mail-outbox.key: chave original e estável da fila.
- backup-password.txt: senha exclusiva da cópia, sem quebra de linha interna.
- resources.json: identificadores esperados do projeto Neon e workspace Render.
- provider-usage.json: observações sem credenciais; campos não disponíveis são null.

O adaptador lê esses arquivos, passa credenciais apenas no ambiente do Java e restaura o ambiente anterior quando termina. Não imprime valores. Para outros usuários, essa pasta não é distribuída: cada operador precisa de sua própria configuração privada ou usar as variáveis DB_URL, DB_USER, DB_PASSWORD e MAIL_KEY_FILE/MAIL_ENCRYPTION_KEY com operacao.cmd --engine=postgres.

O relatório desta instalação fica em D:/Projetos/AgendaClara/.cache/neon-operations/latest.html. Os novos backups ficam na subpasta backups. O backup anterior da 0.7.0 permanece preservado; a nova pasta mantém seu próprio histórico a partir da primeira execução 0.8.0.

Clientes PostgreSQL 17.12 já estão em .cache/postgres-tools/pgsql/bin. São portáteis e não exigem serviço Windows, extensão VS Code ou instalação global. TLS e channelBinding=require da conexão JDBC também são propagados ao cliente pg_dump. A exportação PostgreSQL pode ocorrer com o site em execução.

## O que é medido e o que não é
O tamanho retornado por pg_database_size é uma medida do banco atual. A referência de 1 GiB usada na API do projeto é exibida como estimativa: não representa o total de armazenamento de todos os bancos/branches, histórico e outros componentes do provedor.

No Neon Free, a API detalhada de consumo exige planos superiores. Campos zerados no retorno genérico do projeto não foram aceitos como prova de consumo zero. O relatório permite informar totais conferidos no painel, mas não inventa números ou faz upgrade.

No Render, métricas de memória, tráfego ou disponibilidade de um serviço não equivalem aos totais mensais compartilhados do workspace. Horas gratuitas, transferência e minutos de compilação devem ser conferidos na seção Monthly Included Usage do painel. O limite de minutos deve ser copiado do plano real: não é presumido no código.

As referências atuais são Neon Free: 100 CU-h/mês e 5 GB de transferência pública; Render: 750 horas gratuitas/mês por workspace e referência Hobby de 5 GB de transferência. Transferência usa GB decimais no relatório; o tamanho PostgreSQL é apresentado em MiB. Os números são referências de 13/11/2026, não um bloqueio de cobrança.

O Render informa que excedentes de transferência/compilação podem ser cobrados se houver forma de pagamento. A rotina não acessa nem altera cartão, plano ou limites de gastos. Confira esses controles no painel. Não foram criados recursos pagos nesta etapa.

## Como informar consumo conferido
O formato está em examples/provider-usage.example.json. Copie-o para uma pasta privada, preencha os identificadores e o instante observedAt, e substitua somente os campos efetivamente conferidos. Campos indisponíveis continuam null.

Para consumos mensais, periodStart e periodEnd devem delimitar o período apresentado pelo provedor, com início incluído e fim excluído. O instante de observação deve pertencer ao período. Datas usam ISO 8601 UTC, por exemplo 2026-11-13T20:00:00Z. Uma observação com mais de 24 horas ou um período já encerrado fica como antiga; o relatório nunca a considera saudável por omissão.

~~~powershell
.\operacao.cmd --operations=report --engine=postgres --provider-input="D:\PastaPrivada\provider-usage.json"
~~~

A conexão do PostgreSQL deve estar no ambiente privado. Se NEON_PROJECT_ID ou RENDER_WORKSPACE_ID estiverem configurados, observações de outro recurso são recusadas. O adaptador Neon configura esses identificadores por resources.json.

O arquivo aceita somente os campos previstos, números finitos não negativos, origem console ou connector e até 16 KiB. Não coloque chaves de API ou senhas nele. Os scripts não extraem tokens das integrações do Codex.

## Interpretação do resultado
- 70%: atenção; 90%: crítica; 100%: limite atingido.
- Não informado: não foi possível estabelecer consumo ou limite.
- Antigo/período encerrado: atualizar a observação antes de confiar no percentual.
- Backup com mais de sete dias: atenção no relatório. Isso não cria rotina diária nem faz verificação fora de uma execução solicitada.
- Arquivo ausente/alterado: a cópia registrada não corresponde ao hash; preserve-a e investigue antes de usar.
- Falha na última tentativa: o backup anterior continua no histórico, sem marcar a tentativa como sucesso.

Códigos de saída: 0 significa verificações conferidas; 2 significa rotina concluída com pontos de atenção (inclusive cotas desconhecidas); 1 significa falha da operação; 3 significa outra execução em andamento. Código 2 com backup VERIFIED não significa falha de criação. Os relatórios mostram o horário UTC e não se atualizam sozinhos.

A rotina exige pelo menos 256 MiB livres no disco de trabalho e mantém o limite existente de 64 MiB por backup. JSON e HTML formato 2 são gravados por substituição atômica individual; ambos incluem o identificador da execução para reconhecer uma interrupção entre arquivos. O arquivo state.json mantém a última cópia verificada. Na 0.8.1, o formato 2 vincula esse histórico à origem e registra a intenção antes da exportação. Metadados formato 1 permanecem sem vínculo até um novo backup válido; arquivos anteriores não são apagados.

## Web, mobile e segurança
Site e PWA continuam usando o mesmo servidor e PostgreSQL persistente. O pacote local tem banco próprio. A versão 0.8.0 distribui as ferramentas operacionais; não há exportação global nem consulta de credenciais no painel de uma empresa.

O relatório HTML não carrega JavaScript, imagens ou fontes externas; a política de conteúdo bloqueia esses recursos. São usados dados operacionais, sem nomes, e-mails, tokens ou conteúdos da fila. O relatório e os backups ficam restritos ao proprietário. Não publique a pasta operacional como site estático nem envie os arquivos privados para o GitHub.

Para restaurar, use backup.cmd/backup.sh conforme [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md). Nunca use o banco online como destino de teste. Mantenha uma segunda cópia protegida fora do D: e guarde a senha separadamente; nenhum armazenamento externo foi contratado.

## Referências
- [Neon: planos e cotas](https://neon.com/docs/introduction/plans).
- [Neon: métricas de consumo e planos aceitos](https://neon.com/docs/reference/api/consumption/get-consumption-history-per-project-v2).
- [Render: horas, limites e faturamento no plano gratuito](https://render.com/docs/free).
- [Render: referências do plano Hobby](https://render.com/pricing).
- [PostgreSQL: tamanho de banco](https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-ADMIN-DBSIZE).

## Revisão 0.8.1
Consulte [REVISAO-BACKUPS-0.8.1.md](REVISAO-BACKUPS-0.8.1.md). Entrada de consumo inválida não impede backup; o relatório mostra INVALID. Tentativa sem finalização vira INTERRUPTED. Trocar a origem do banco exige pasta operacional distinta. Um alerta antigo de limite esgotado permanece visível com a indicação de observação antiga. TLS externo é obrigatório e os parâmetros JDBC/nativos aceitos são validados.
