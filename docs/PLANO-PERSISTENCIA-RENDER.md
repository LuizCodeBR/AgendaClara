# Proposta não adotada — PostgreSQL pago no Render

**Decisão em 13/11/2026:** o usuário recusou recursos pagos. Nenhuma ação de criação deste plano foi executada. Foi adotado PostgreSQL 17 no Neon Free, mantendo o site no Render Free; consulte [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md). O restante deste documento é o registro histórico da proposta, não uma pendência de autorização nem a configuração atual.

## Estado atual
Inspeção do espaço My Workspace, tea-dbqf02c9v7es73cd4uf0: nenhum PostgreSQL existente. Serviço agenda-clara-luizcodebr, srv-dbqf9mon74is73c13k6g, free, Virginia.

A demonstração atual usa H2 no armazenamento efêmero. Atualizar o código não transforma esse armazenamento em durável. Nenhum banco pago foi criado nesta etapa sem aprovação.

## Configuração concreta proposta
| Item | Valor |
| --- | --- |
| Nome | agenda-clara-data |
| Espaço/região | My Workspace / Virginia |
| PostgreSQL | 17, mesma versão da matriz de testes |
| Plano de processamento | 0.1c-256mb |
| Armazenamento inicial | 1 GB |
| Expansão automática | desativada; qualquer expansão será uma decisão posterior |
| Serviço web | manter o plano gratuito existente |
| Rede | conexão interna do Render, mesma região e espaço |
| Chave da fila | MAIL_ENCRYPTION_KEY estável, privada e preservada fora do banco |
| Envio | manter demo, sem e-mail real |
| Cadastro | manter demonstração com dados fictícios |

Valores oficiais consultados em 13/11/2026: US$ 6/mês de processamento e US$ 0,30/GB/mês de armazenamento. Base estimada: **US$ 6,30/mês**, antes de tributos, câmbio ou consumo adicional dos limites do Render. Não é necessária mudança do plano do workspace para essa configuração.

No workspace Hobby, a documentação informa recuperação gerenciada dos últimos três dias. O prazo depende do plano real do workspace. Isso não substitui uma cópia exportada e protegida fora da conta do provedor.

## Ações após autorização de custo
1. Criar somente o banco proposto e aguardar disponibilidade.
2. Confirmar plano, capacidade, região e ausência de expansão automática.
3. Gerar uma chave estável por mecanismo privado e preservar cópia protegida no D:; nunca registrar seu valor em documentação ou no Git.
4. Configurar o serviço com SPRING_PROFILES_ACTIVE=postgres, STORAGE_MODE=persistent, DB_URL JDBC interna, DB_USER, DB_PASSWORD e MAIL_ENCRYPTION_KEY. Preservar demais variáveis e MAIL_MODE=demo.
5. Implantar e verificar migrações, cadastro, agenda, confirmação simulada e monitor.
6. Reiniciar/reimplantar a aplicação e conferir que os mesmos dados e a mesma mensagem continuam disponíveis.
7. Conferir recuperação gerenciada e realizar exportação/restauração em destino separado quando houver acesso/ferramentas autorizados.

O PostgreSQL começará com os exemplos fictícios da demonstração. A troca de perfil não converte nem transfere o H2 anterior automaticamente. Os cadastros temporários da demonstração atual não foram exportados do servidor gratuito, que não oferece acesso de shell. Não use dados reais antes de uma migração e operação preparadas.

A aplicação web gratuita ainda pode suspender por inatividade e demorar a iniciar. O banco e a fila permanecem no PostgreSQL; o processamento de e-mails só ocorre enquanto a aplicação estiver ativa. Disponibilidade contínua seria outra contratação, não incluída nesta proposta.

## Alternativas
Se já existir outro PostgreSQL durável do usuário, é possível utilizá-lo após validar acesso, TLS, backups e chave estável, sem criar o banco pago proposto. O PostgreSQL gratuito do Render expira após 30 dias e não oferece backups gerenciados, por isso não foi escolhido para persistência duradoura.

## Fontes
- [Preços oficiais](https://render.com/pricing.md#postgres).
- [Criação e capacidade mínima](https://render.com/docs/postgresql-creating-connecting).
- [Backups e janela de recuperação](https://render.com/docs/postgresql-backups).
- [Limites do plano gratuito](https://render.com/docs/free).
