# Fila persistente e monitoramento — 0.6.1

## Como experimentar
1. Cadastre uma empresa fictícia no site, PWA ou pacote local.
2. Entre no painel e abra **E-mails e envios**.
3. Confira a confirmação de e-mail no histórico e na caixa de teste privada.
4. Filtre a situação ou use Atualizar envios. A tela também consulta a cada dez segundos enquanto estiver visível e conectada.
5. No pacote local, encerre e reabra o servidor: os registros e mensagens ainda válidas continuam disponíveis, desde que banco e chave sejam preservados.

Na demonstração, as mensagens ficam diretamente disponíveis na caixa simulada; não passam pela API do Resend nem representam envio real. O painel usa o fuso do estabelecimento. Web e celular compartilham a mesma interface; não há um aplicativo nativo separado.

## Persistência e transações
A migração V6 acrescenta mail_outbox, mail_worker_heartbeat e mail_key_identity. A emissão do link e a gravação da mensagem protegida acontecem na mesma transação, antes do commit. Uma falha ao gravar a fila desfaz a alteração de acesso. O processamento externo começa em segundo plano, após a confirmação da transação.

O conteúdo necessário para enviar a mensagem — remetente, destinatário, assunto, texto e link — usa AES-256-GCM com nonce aleatório e autenticação dos identificadores da mensagem, empresa, usuário, finalidade e modo. O banco de tokens de acesso continua guardando somente o hash do segredo; a cópia necessária ao envio existe criptografada na outbox.

As relações compostas impedem associar uma mensagem a um usuário de outra empresa. Consultas administrativas sempre usam a empresa da sessão. O histórico não expõe endereço, assunto, corpo, link, chave ou conteúdo criptografado.

## Processamento e recuperação de falhas
Um processador interno ao servidor consulta a fila a cada segundo. Não é necessário Redis, nova biblioteca ou serviço separado.

- PENDING: aguarda a próxima tentativa.
- PROCESSING: uma instância possui uma concessão de 60 segundos.
- ACCEPTED: o provedor aceitou a solicitação; no modo demo, a mensagem está na caixa simulada.
- FAILED: esgotou tentativas ou ocorreu uma recusa definitiva.
- EXPIRED: o prazo de envio/validade terminou.
- CANCELLED: o link já foi usado ou substituído antes do envio.

A aquisição usa bloqueio de linha e SKIP LOCKED. Um identificador de concessão impede que um processo antigo sobrescreva o resultado de quem recuperou uma tarefa interrompida. A chamada HTTP ocorre fora da transação e tem limite de tempo.

São até três tentativas automáticas por ciclo. Falhas transitórias aguardam 30 e 60 segundos; um Retry-After válido do provedor pode ampliar a espera. Falhas de autorização, rejeição ou conflito não são repetidas automaticamente. Depois da terceira tentativa interrompida, a falha fica visível em vez de permanecer presa em processamento.

O responsável pode agendar **um novo ciclo manual** de até três tentativas para uma falha elegível da própria empresa. A ação exige sessão, CSRF, validade e conteúdo preservados, modo resend, link ainda utilizável e término da espera solicitada pelo provedor. Essa espera permanece registrada mesmo quando o ciclo automático termina em falha; a interface identifica Espera do serviço até. Não reenvia mensagens já aceitas. Corrigir uma chave de acesso/um remetente inválido é responsabilidade do operador.

A mesma mensagem preserva identificador e conteúdo em todas as tentativas. O Resend mantém deduplicação por 24 horas; o sistema limita a janela de tentativa a 23 horas desde a criação e à validade original do link, o que ocorrer primeiro. Não existe promessa de entrega exatamente uma vez: há tentativas recuperáveis e deduplicação dentro desse limite.

## Monitoramento
O painel mostra contagens dos últimos sete dias e até 50 registros recentes, com filtro, datas, tentativas e motivos resumidos. O processador confere a necessidade de registrar sua atividade antes e durante o lote, aproximadamente a cada 15 segundos. Uma chamada HTTP em andamento pode ampliar esse intervalo até retornar ou atingir seu limite de tempo.

O estado pede atenção quando falta uma verificação recente por mais de 90 segundos, há erro do processador, falhas da empresa ou mensagem pendente há mais de cinco minutos. A caixa simulada e o envio desativado são identificados separadamente. O sinal do processador é compartilhado pela instalação; os históricos e totais são separados por empresa.

Logs registram códigos de falha sem credenciais, destinatários ou links. Não há alertas enviados para serviços externos nem monitoramento independente capaz de avisar quando o servidor inteiro estiver desligado.

**Aceitação não comprova entrega:** spam, rejeição posterior e recebimento exigem confirmação no provedor e, em uma evolução, webhooks de entrega. Nenhum envio externo real foi validado nesta versão.

## Chave e backup
| Variável | Uso |
| --- | --- |
| MAIL_ENCRYPTION_KEY | 32 bytes aleatórios em Base64, configurados como segredo privado; preferencial para múltiplas instâncias |
| MAIL_KEY_FILE | caminho alternativo do arquivo da chave |
| AGENDA_DATA_DIR | pasta local; o padrão é ./data |
| MAIL_WORKER_ENABLED | true por padrão; false desliga processamento, manutenção e verificação periódica |

Na demonstração, a primeira inicialização gera data/mail-outbox.key. O arquivo tem acesso restrito ao proprietário e não é versionado. Sua criação/leitura no modo demo é coordenada por mail-outbox.key.lock, inclusive entre processos. As permissões são aplicadas antes de escrever o segredo. O arquivo de coordenação é automático; não o remova com instâncias em funcionamento. No Windows, mantenha projeto e extração em D: para que dados, chave e temporários do projeto também fiquem nesse disco. Uma chave configurada por variável tem prioridade sobre o arquivo.

**Preserve o banco e a chave original juntos no plano de backup, com acesso restrito e cópia protegida da chave.** Para copiar o H2, pare o servidor e copie a pasta data completa. Não copie o arquivo do banco em uso como se fosse um backup consistente. Para PostgreSQL, use backup consistente do banco e preserve a chave separadamente em armazenamento protegido.

A identidade da chave fica vinculada à instalação no banco. Iniciar com chave ausente ou diferente é recusado para evitar perda silenciosa de conteúdo e mistura entre instâncias. Restaure a chave original; não apague o vínculo nem gere outra chave para contornar a proteção. Todas as instâncias devem usar a mesma chave. Rotação automática de chaves ainda não foi implementada.

O conteúdo de mensagens reais aceitas é apagado da outbox imediatamente. Mensagens simuladas e conteúdos de falhas permanecem até sua validade; registros administrativos são removidos após sete dias. A manutenção roda aproximadamente a cada 30 segundos enquanto o processador está ativo.

## Limites de armazenamento e operação
A persistência resiste ao reinício da aplicação quando o armazenamento é preservado. Ela não recupera dados que a hospedagem apagou.

- Pacote local: banco H2 e chave na pasta data, com gravação síncrona do H2.
- Docker Compose: PostgreSQL no volume postgres-data e chave no volume mail-data; ambos precisam de backup. O local físico dos volumes depende da configuração do Docker Desktop; configure seu armazenamento no D: se usar essa opção. Não execute down -v para preservar esses dados.
- Online: Render Free com banco PostgreSQL 17 no Neon Free; contas, mensagens e fila permanecem entre reinícios. MAIL_ENCRYPTION_KEY estável é preservada no serviço e em pasta privada no D:. Não foi contratado disco nem banco pago. Consulte [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md).
- Operação real: PostgreSQL durável, chave estável, backups, remetente autorizado, credenciais privadas, HTTPS e validação externa de entrega ainda precisam ser configurados.

## Validação e referências
Veja [REVISAO-SEGURANCA-0.6.1.md](REVISAO-SEGURANCA-0.6.1.md), [VALIDACAO-0.6.1.md](VALIDACAO-0.6.1.md) e [EMAIL-ACESSO.md](EMAIL-ACESSO.md).

- [Resend: deduplicação por 24 horas](https://resend.com/docs/dashboard/emails/idempotency-keys).
- [Resend: códigos de erro](https://resend.com/docs/api-reference/errors).
- [PostgreSQL: SELECT e SKIP LOCKED](https://www.postgresql.org/docs/17/sql-select.html).
- [Render: limites do plano gratuito](https://render.com/docs/free).

## Backup protegido (0.7.0)
O pacote inclui rotinas para reunir banco e chave em um backup criptografado, verificar sua integridade e restaurar em outro destino. Não basta restaurar somente o banco. Consulte [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md).
