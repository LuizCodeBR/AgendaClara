# Validação — Agenda Clara 0.14.0

15/11/2026. Código, temporários, logs, bancos de ensaio e entregas no D:. Código/tag v0.14.0: ccb20ba3ab27140e7ae8ce9f8784bb20a562d151.

## Resultado funcional
Painel com períodos de até 90 dias, filtros combinados, indicadores, gráfico diário, tabelas e exportação CSV autenticada. Mesmo aplicativo no site, PWA e pacote local. Guia: [RELATORIOS.md](RELATORIOS.md).

Sem dependências novas, extensão obrigatória ou recurso pago. Mantidos Render Free e Neon Free. Backups e monitoramento continuam sob demanda.

## Testes locais
- Backend Windows/Java 26/H2: 359 casos, 356 aprovados e três condicionais não aplicáveis, zero falhas/erros. Foram acrescentados 19 casos de relatório e um de migração.
- Interface: 156 testes aprovados, 19 novos, incluindo filtros, resultados antigos, exportação cancelada, sessão expirada, paginação e arquivo baixado. TypeScript/Vite aprovados.
- Dois fluxos novos reais desktop/Pixel 7 emulado: filtros, CSV baixado e analisado, privacidade, cancelamento refletido no relatório e largura sem overflow da página.
- Durante o desenvolvimento, o teste de um dia de 23 horas revelou uma diferença de conversão de data entre bancos. Limites de dia calculados pela aplicação corrigiram o problema; a mesma regressão passou em H2 e PostgreSQL.
- Teste concorrente cancela uma reserva enquanto a leitura está em andamento: totais, dias e detalhes mantêm o mesmo snapshot, e a consulta seguinte mostra o cancelamento.
- Teste de CSV inclui nome semelhante a fórmula, aspas, ponto e vírgula e quebra de linha. BOM, colunas, escaping e prefixos verificados. Não foi executada automação dentro de Excel/LibreOffice.
- render.yaml validado pelo esquema oficial disponível, mantendo somente o serviço Free e referências privadas existentes.

## GitHub e distribuição
[CI 43718731356](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43718731356): seis grupos aprovados.
- Java 21 e 26 com H2/PostgreSQL 17, incluindo testes de segurança, isolamento e concorrência. PostgreSQL: 359 casos, 358 aprovados e um condicional, em ambas as versões de Java.
- Interface: 156 testes. Navegador: 34 cenários aprovados em 56,3 segundos.
- Onze rotinas HTTP e backup/restauração com seis provas: armazenamento, acesso privado do cliente, regras, resultados/histórico, contato/resumo e indicadores.
- Docker/PostgreSQL: compilação, fluxos existentes e persistência após recriação do contêiner aprovados.

[Publicação 43718996483](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43718996483): sucesso, com onze rotinas HTTP e seis comparações após restauração antes de publicar.

[Entrega 0.14.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.14.0). Downloads oficiais conferidos com metadados GitHub e SHA256SUMS.txt, extraídos em D:/Projetos/Entregas/AgendaClara-0.14.0-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.14.0-demo.zip | 29178006 | 3dd0ec80c7652e5a5427b0bf290b6b392472ce39690255470a5ccaab4484ca00 |
| AgendaClara-0.14.0-fonte.zip | 660891 | 67800a60e8959c7a8cdc63ee8ffdb8059eac0fce363f8a292e1b376fa7ebda92 |

## Atualização real no Windows
Preservados os originais; copiados banco e chave V11 para .cache/neon-private/reports-upgrade/data. O aplicativo 0.13.1 iniciou essa cópia; registrados dois exemplos fictícios e seus históricos completos, além de uma reserva de prova com contato/versão/resumo diário. A aplicação 0.14.0 migrou para V12 e preservou as provas.

O ensaio foi repetido com outra cópia independente em .cache/neon-private/reports-official-upgrade/data, usando iniciar-demo.cmd oficial da 0.13.1 e depois da 0.14.0 baixada. V11 → V12 aplicado, dois exemplos/históricos e contato/resumo idênticos. Dois testes de navegador do painel passaram no pacote oficial em 2,9 segundos.

Servidores locais de ensaio encerrados. As instalações originais não foram substituídas. O novo pacote permanece disponível no D:; preserve a pasta data e a chave ao atualizar a instalação que efetivamente utiliza.

## PostgreSQL online e publicação
Antes da atualização principal, criada branch temporária do banco Neon Free, com compute fixo 0,25 CU e expiração. V11 → V12 preservou 38 empresas, 57 reservas e 31 eventos e acrescentou o índice. A rotina HTTP de relatórios/CSV passou nessa cópia. Servidor de ensaio encerrado e expiração confirmada: a branch deixou de constar entre as ativas.

Backup principal criado e verificado sob demanda em D:/Projetos/AgendaClara/.cache/neon-operations/backups:
- AgendaClara-20261115T132514Z-2a024ebc-96d3-49bf-b02f-76e95fbdcf12.acbackup.
- 60512 bytes; SHA-256 2e2ac5c4eb4fda7540b4e5cb79bf3bc4e345ff8d0eeeb355c4acbbd12125b170.
- VERIFIED, fileMatches=true, verificado às 13:25:32 UTC.
- A rotina retornou 2 por cotas não informadas, não por falha do backup. Não foi agendada rotina recorrente.

Render: deploy dep-dbsr6om1egvs73el55qg, commit ccb20ba, live em 15/11/2026 13:30:17 UTC (10:30:17 em São Paulo). Mesmo serviço Free; alterado somente o link público de download na configuração, preservando os segredos. Banco principal com esquema 12 e índice confirmados.

Após publicação:
- /api/public/config confirmou 0.14.0, storageMode=persistent e o download 0.14.0.
- Dois exemplos fictícios com histórico completo e prova de contato/versão/resumo preservados.
- Rotina HTTP verificou filtros, isolamento de empresa, datas inválidas, CSV privado e atualização após cancelamento.
- Dois fluxos de navegador no site publicado passaram em 18,5 segundos, desktop e Pixel 7 emulado, com download CSV real.
- Nenhum erro nos logs consultados após a publicação e testes.

## Limites da validação
Mobile foi testado por emulação de navegador; não foi utilizado aparelho físico nem empacotamento Android/iOS nativo. A PWA compartilha o site; o banco local continua separado. O relatório representa o estado consultado, e o CSV executa uma nova leitura dos mesmos filtros. Não é fechamento financeiro nem comprovação de pagamento.

Logs/evidências locais, fora do Git: .cache/report-backend-full.log, report-ui-full.log, report-build.log, reports-e2e-local.log, reports-e2e-official.log, reports-e2e-live.log, reports-neon-server.log e reports-predeploy-backup.log. Provas com dados ficam em .cache/neon-private; capturas/CSV dos testes em frontend/test-results.
