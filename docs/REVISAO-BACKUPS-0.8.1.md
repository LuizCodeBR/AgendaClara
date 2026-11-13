# Revisão de segurança e concorrência — backups 0.8.1

## Escopo
Rotinas de backup, restauração, arquivos operacionais, histórico, credenciais do adaptador Windows e interpretação de limites gratuitos. Ferramentas do responsável pela instalação, sem novos endpoints HTTP ou alterações de esquema. O usuário mantém execução somente sob demanda, arquivos no D: e nenhum recurso pago.

## Problemas reproduzidos na 0.8.0
Cinco testes iniciais foram executados antes das correções; quatro falharam e um passou. A junção do Windows já era recusada e foi mantida como teste de proteção, sem ser apresentada como falha nova.

| Caso reproduzido | Antes | Correção |
| --- | --- | --- |
| JSON de consumo inválido | Interrompia a operação antes do backup | A observação é descartada com estado INVALID; o backup pode ser criado e verificado |
| Outra origem com o mesmo motor | O histórico anterior podia parecer válido para outro banco H2 | Estado vinculado por hash ao caminho H2 ou host/porta/banco/usuário PostgreSQL; senha e opções TLS ficam fora da identidade |
| Primeiras execuções simultâneas | Erros de inicialização em vez de relatório ou estado ocupado | Um bloqueio do sistema operacional cobre também identificação, criação e preparação da pasta |
| Consumo esgotado com observação antiga | O alerta de limite era substituído apenas por STALE | Mantém o alerta da última observação e informa separadamente que ela está antiga |

A vinculação identifica o destino lógico da configuração, não é uma identidade física imutável da instância de banco. Troca de senha ou opções TLS não muda a vinculação; trocar caminho, servidor, banco ou usuário exige pasta operacional própria.

## Outros reforços e testes
- A intenção RUNNING é salva antes de senha, cópia e exportação. Na próxima execução, uma intenção não finalizada vira INTERRUPTED, mantendo a última cópia válida.
- Metadados antigos formato 1 são lidos e preservados como UNBOUND. Não são atribuídos automaticamente à origem atual. Um novo backup válido grava estado formato 2 e estabelece o vínculo.
- Arquivos e temporários nascem com permissões exclusivas do proprietário, em vez de depender apenas de alteração posterior. Discos sem POSIX/ACL são recusados.
- Leituras são limitadas pelo fluxo aberto, recusam arquivos não regulares e redirecionamentos; bloqueios H2 usam NOFOLLOW_LINKS.
- O leitor PowerShell mantém limite efetivo de 4 KiB, rejeita redirecionamento de pasta e usa leitura UTF-8 estrita.
- O processo nativo recebe um ambiente reduzido. PGHOSTADDR, PGSERVICE, PGOPTIONS e segredos da aplicação não são herdados.
- JDBC e cliente nativo usam a mesma configuração validada. Parâmetros não suportados ou repetidos são recusados; TLS é exigido fora do loopback.
- Limites JDBC são reconstruídos na URL, pois a URL tem prioridade sobre Properties. Conexão: 10 s; login: 15 s; leitura/consulta: 20 s; cancelamento: 10 s. Isso não representa um prazo único para toda a operação.
- O nome do banco é passado como valor libpq corretamente escapado, inclusive se contiver espaços, aspas ou sinal de igual; nenhuma senha vai nos argumentos.
- Restaurações PostgreSQL usam bloqueio consultivo não bloqueante no destino antes de conferir se está vazio. O bloqueio coordena estas ferramentas; não impede clientes externos que não participam do protocolo.
- A chave de recuperação é preservada em nova pasta privada antes de iniciar a restauração PostgreSQL. Se a etapa nativa falhar, essa pasta permanece disponível; não significa que o banco foi restaurado.
- O hash aceita o envelope criptográfico de até 52 bytes além do conteúdo limitado. O formato ACBK0001 continua compatível com cópias anteriores.

## Testes acrescentados
Dezesseis casos de revisão cobrem as quatro reproduções, junção Windows, interrupção simulada após intenção persistida, migração de metadados, ambiente nativo, timeouts, nomes especiais de banco, normalização da origem, preservação de pasta inválida, permissões desde a criação, concorrência entre duas JVMs, recusa de FIFO no Linux e limite do envelope.

O teste PostgreSQL nativo também tenta restaurar com o bloqueio já ocupado, confirma a recusa sem criar a pasta da chave e depois realiza a restauração normal com os mesmos dados e mensagem. É executado nas matrizes PostgreSQL do CI.

## Limites e operação
Não houve mudança de contas, autenticação, agendamento, esquema ou limites dos provedores. Não há contratação, tarefa recorrente ou exclusão automática de backups.

O operador, a conta do sistema operacional, os binários PostgreSQL instalados e a origem do backup precisam ser confiáveis. Uma restauração exige banco vazio e isolado de outros clientes; o bloqueio não torna segura uma restauração sobre um banco em uso. Estados e arquivos de um operador comprometido não são atestados por um serviço externo.

JSON e HTML continuam com substituição atômica individual, identificador de execução e horário. Não constituem uma transação única entre arquivos, nem garantia contra perda física do disco. Mantenha cópia externa protegida e a senha separada.

Resultados finais e publicação: [VALIDACAO-0.8.1.md](VALIDACAO-0.8.1.md).
