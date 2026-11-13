# Validação final 0.8.0 — operação sob demanda

## Escopo e decisão
O usuário escolheu não agendar backups diários e consultar os limites somente sob demanda. A versão automatiza as etapas de uma execução solicitada, sem tarefa recorrente no computador, Codex ou nuvem. Nenhum recurso pago foi provisionado ou plano alterado.

Projeto, ferramentas, temporários, backups, downloads e evidências locais ficam no D:/Projetos. Java local 26.0.2 e Node 24.18.0. Nenhuma nova dependência do aplicativo, extensão do editor, serviço Windows ou instalação global foi necessária. Clientes PostgreSQL 17.12 portáteis da etapa anterior foram reutilizados.

## Resultados
| Verificação | Resultado |
| --- | --- |
| Build local | 182 testes Java encontrados, 180 aprovados, zero falhas/erros e dois condicionais não executados |
| Condicionais locais | PostgreSQL Docker do CI; criação de link simbólico não permitida no Windows |
| Interface | 63 testes aprovados; TypeScript e Vite aprovados |
| CI Java 21 + H2 | 181 aprovados; um teste PostgreSQL condicional não executado |
| CI Java 26 + H2 | 181 aprovados; um teste PostgreSQL condicional não executado |
| CI Java 21 + PostgreSQL 17 | 182 aprovados, sem exclusões, incluindo rotina operacional nativa e restauração |
| CI Java 26 + PostgreSQL 17 | 182 aprovados, sem exclusões |
| CI navegador | 63 testes de interface e 18 cenários de desktop/celular aprovados |
| CI Docker | aplicação e PostgreSQL com cinco roteiros HTTP aprovados |
| Backup real Neon | 19.024 bytes, exportado, autenticado e verificado pela nova rotina |
| Restauração Neon → PostgreSQL local | novo banco exclusivo, mesma conta/senha/empresa/mensagem recuperadas por HTTP |
| Navegador na cópia PostgreSQL restaurada | 18 cenários aprovados em 28,0 segundos, desktop e Pixel 7 emulado |
| Consulta sob demanda | não cria outra cópia nem altera state.json da última cópia válida |
| Relatório HTML | verificado visualmente em desktop e Pixel 7, sem transbordamento do documento, scripts, erros JavaScript ou requisições externas |
| Pacote oficial baixado | hashes SHA-256 conferidos; iniciar-demo.cmd e roteiro HTTP aprovados |
| Operação no pacote oficial | operacao.cmd cria/verifica cópia e backup.cmd restaura em outra pasta; mesma conta e mensagem recuperadas em HTTP |
| Site após implantação | 0.8.0, storageMode=persistent, mailMode=demo e download 0.8.0 confirmados |
| Persistência online | conta e mesma mensagem criadas antes da versão 0.8.0 continuam acessíveis |
| HTTPS | cinco roteiros aprovados, painel e cadastro conferidos em desktop/Pixel 7 emulado |
| Render YAML | validado com esquema oficial; serviço Free e entradas privadas preservados |

Roteiros HTTPS: smoke, date-hours-smoke, registration-smoke, account-access-smoke e mail-queue-smoke. Os testes usam somente dados fictícios. Pixel 7 é emulação; não houve ensaio em aparelho físico.

## Cobertura acrescentada
Dez testes operacionais verificam arquivo realmente criptografado, restauração, metadados sem credenciais, preservação da última cópia após falha, bloqueio concorrente, consulta sem nova cópia, adulteração do arquivo, pasta indevida, caminho salvo malicioso, limites de atenção/crítico/esgotado, consumo desconhecido, observações antigas, período encerrado, origem/recurso inválido, campos inesperados, valores inválidos e proteção da página HTML.

O teste PostgreSQL do CI passou a usar a rotina completa de operação antes de restaurar a exportação nativa, conferir a mesma mensagem criptografada e continuidade da sequência. O JAR e o iniciador Bash também executam a rotina no CI de navegador e na publicação da entrega.

A revisão final acrescentou exclusão de relatórios, dumps, bancos e backups do contexto Docker. O adaptador Windows foi testado com saída UTF-8 e restauração do ambiente de credenciais ao terminar. Não houve mudança de esquema do banco ou das regras de agendamento.

## Publicação e rastreabilidade
- Funcionalidade: 2237ec09e344a0503ac7e3fe81474d5a766d1694.
- [CI final da aplicação e Docker](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43502626528): seis trabalhos aprovados no commit e72b7c9c7a6b39144b0cdeeb35bb72ba5d64a3ee.
- Tag v0.8.0: 38ebaa00666e5ce2cd983034ee3274808b45c792. A diferença posterior ao CI é exclusiva da codificação da saída do iniciador Windows, validada neste computador.
- [Publicação dos pacotes](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43503374969): aprovada; executou o código da tag, a rotina do JAR, verificação de SHA-256 e recuperação da mesma conta/mensagem.
- A publicação automática foi impedida pela marcação skip ci do ajuste Windows. O fluxo foi corrigido para permitir execução manual de uma tag existente, validando seu formato e a versão do pacote. A tag não foi alterada.
- [Entrega v0.8.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.8.0), publicada em 2026-11-13T20:39:27Z como demonstração.
- Render: dep-dbrnbg0ae00c7384am1g, live em 2026-11-13T20:43:07.978828Z, commit 53865d4fec5ae5d4d5ceda895c789113f0170b28. Datas de infraestrutura em UTC.
- Somente DEMO_DOWNLOAD_URL foi mesclada no serviço. Banco, chave estável, perfil PostgreSQL e plano Free foram preservados; atualizações automáticas continuam desligadas.
- O relatório final e o link do render.yaml são atualizados no ramo principal depois da entrega, sem alterar tag, binários ou hashes dos downloads.

## Downloads e evidências no D:
Pasta: D:/Projetos/Entregas/AgendaClara-0.8.0-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.8.0-demo.zip | 29061044 | 7fb9ebe7c0acc8a3e9d257f3345dc961d3ebfe809d857f08b0e0bb8951505839 |
| AgendaClara-0.8.0-fonte.zip | 410567 | f1f1df582efc8856ac6151879eb8ee35c6e84da8febcae55b0a4a4ea613b49f7 |

O pacote foi extraído em teste-pacote/AgendaClara-demo. A cópia local foi restaurada em teste-restaurado/data. O JAR oficial baixado tem 32.227.883 bytes. Os arquivos ZIP oficiais foram preservados intactos.

Os novos backups do Neon e seu relatório ficam em D:/Projetos/AgendaClara/.cache/neon-operations. Configuração, chave e senha continuam em .cache/neon-private, protegidas e fora do Git. Evidências sanitizadas não incluem essas credenciais, provas de acesso, cookies ou bancos.

## Limites observados e pendências explícitas
O banco atual medido na primeira execução tinha 8.527.872 bytes. pg_database_size é medida do banco atual, não uso total do projeto Neon nem faturamento. Não foi apresentado como cota oficial consumida.

Os totais mensais de processamento/transferência do Neon Free e os totais compartilhados do Render não estão disponíveis integralmente nas integrações usadas. Ficaram UNKNOWN no relatório. A API detalhada Neon exige planos superiores; não foi feito upgrade. O relatório aceita observações conferidas no painel, com fonte, período e validade.

Saída 2 com backup VERIFIED significa cópia válida e pontos de atenção, inclusive consumos desconhecidos. Não significa falha do backup. A página não atualiza sozinha e não existe aviso externo ou monitor contínuo.

Não há cópia externa automática, exclusão de backups, agendamento ativo, restauração da snapshot do provedor, envio real de e-mails ou restauração completa da infraestrutura. O backup permanece limitado a 64 MiB; criptografia e cópia no D: não substituem outra cópia protegida fora do disco e a guarda separada da senha.

Instruções: [OPERACAO-BACKUPS-LIMITES.md](OPERACAO-BACKUPS-LIMITES.md), [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md) e [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md).
