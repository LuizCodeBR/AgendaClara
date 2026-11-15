# Validação — Agenda Clara 0.14.1

15/11/2026. Código, temporários, logs, bancos de ensaio e entregas no D:. Código/tag v0.14.1: a016596b5fda34f2c186412dcffd3dc62b134777.

## Resultado da revisão
Revisão de autorização/contexto da empresa, operações simultâneas e exportação CSV. Corrigidos contexto divergente entre abas, aceitação de resposta HTML como arquivo CSV, encerramento incompleto de operações após perda de autorização, ausência de resposta controlada para tempo limite e normalização de controles Unicode. Detalhes e limites em [REVISAO-RELATORIOS-0.14.1.md](REVISAO-RELATORIOS-0.14.1.md).

Mesmo código no site, PWA e pacote local. Nenhuma nova dependência, extensão obrigatória, migração de esquema ou contratação. Mantidos Render Free, Neon Free, esquema V12 e formato de backup ACBK0001. Backups e monitoramento permanecem sob demanda.

## Reprodução antes da correção
- Teste HTTP e navegador na versão 0.14.0 reproduziram a exportação sob a empresa de outra aba: a sessão era autorizada, mas a tela mostrava a empresa anterior. O teste esperava 412 e recebia 200.
- Testes da interface reproduziram a aceitação de resposta HTML com status 200 e o tratamento incompleto de contexto/autorização.
- Falha de tempo limite reproduzida por injeção de QueryTimeoutException no teste, sem sobrecarregar o site público.
- Um dos 13 casos de conteúdo CSV revelou controle U+0085 ainda presente.
- As inserções concorrentes com período vazio e não vazio passaram também antes da correção: não foi reproduzida inconsistência de leitura nesses cenários. O isolamento transacional foi preservado.

## Testes locais
- Backend Windows/Java 26/H2: 377 casos, 374 aprovados e três condicionais não aplicáveis; zero falhas/erros.
- Foram acrescentados 18 casos de backend: cinco de contexto/autorização/tempo limite/concorrência e 13 de conteúdo CSV.
- Interface: 164 testes aprovados, oito novos. TypeScript e Vite aprovados.
- Seis cenários de navegador de relatórios/revisão: desktop e Pixel 7 emulado, todos aprovados em 5,7 segundos.
- Testes verificam operações pendentes, respostas tardias, cliques repetidos, perda de conexão, resposta de tipo incorreto e novo acesso após 401/403/412.
- Testes de concorrência confirmam consistência entre totais, grupos e dias durante novas reservas; mantido o teste de cancelamento simultâneo. A consulta seguinte reflete a alteração confirmada.
- CSV analisado por parser independente: BOM, 20 colunas, aspas/separadores, valores, prefixos textuais e controles Unicode. Não foi utilizado Excel/LibreOffice automatizado.
- render.yaml validado pelo esquema oficial atual, mantendo somente o serviço Free.

## GitHub e distribuição
[CI 43722030336](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43722030336): seis grupos aprovados.
- Java 21 e 26 com H2 e PostgreSQL 17. PostgreSQL: 377 casos, 376 aprovados e um condicional, em cada versão de Java; zero falhas/erros.
- Interface: 164 testes. Navegador: 38 cenários aprovados em 59,3 segundos, quatro novos nesta revisão.
- Onze rotinas HTTP e seis provas de preservação após backup/restauração: armazenamento, acesso privado do cliente, regras, resultados/histórico, contato/resumo e indicadores.
- Docker/PostgreSQL: compilação, fluxos e persistência aprovados.

[Publicação 43722862924](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43722862924): sucesso, incluindo onze rotinas HTTP e seis comparações após restauração antes de empacotar.

[Entrega 0.14.1](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.14.1). Downloads oficiais conferidos com metadados GitHub e SHA256SUMS.txt e extraídos em D:/Projetos/Entregas/AgendaClara-0.14.1-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.14.1-demo.zip | 29177707 | 249383350421fd045657d39d8cdf74ef38d6b595cde9b05efe9e89a5c6a1637a |
| AgendaClara-0.14.1-fonte.zip | 675332 | a66acf6eda6a103da026ad6b5225cab8ea82d9dcb8a7374819b12fa9ea9fd0eb |

## Atualização real no Windows
Preservados os originais. Banco e chave de uma instalação 0.14.0 copiados para .cache/neon-private/reports-review-upgrade/data. Após iniciar a versão antiga, registrados dois exemplos fictícios com seus históricos completos e uma reserva isolada de prova dos indicadores. A versão 0.14.1 preservou ambos os conjuntos; seis cenários de navegador aprovados.

O ensaio foi repetido com cópia independente em .cache/neon-private/reports-review-official-upgrade/data, primeiro com iniciar-demo.cmd oficial da 0.14.0 e depois com o pacote 0.14.1 baixado do GitHub. Dois exemplos/históricos e totais/grupos/dias idênticos após atualização. Os seis cenários de navegador passaram no pacote oficial em 5,9 segundos.

As reservas de prova foram canceladas e os profissionais de teste desativados ao terminar as verificações. Servidores locais de ensaio encerrados; instalações originais não substituídas. Para atualizar a instalação utilizada, encerre a versão anterior e preserve a pasta data e mail-outbox.key. O novo iniciador está em D:/Projetos/Entregas/AgendaClara-0.14.1-GitHub/AgendaClara-demo/iniciar-demo.cmd.

## Backup e publicação online
Backup principal criado e verificado sob demanda em D:/Projetos/AgendaClara/.cache/neon-operations/backups:
- AgendaClara-20261115T141026Z-104fcad6-e076-406e-8e36-5e651c869e28.acbackup.
- 62341 bytes; SHA-256 f6fee2c2d89ee167b91df614bcd3276c03f0011161106226440bdf9b4af36c6b.
- VERIFIED, fileMatches=true, verificado em 15/11/2026 às 14:10:43 UTC.
- A rotina retornou 2 por cotas não informadas, não por falha do backup. Nenhuma tarefa recorrente foi criada.

Render: deploy dep-dbsrrmtckfvc73c22arg, commit a016596, live em 15/11/2026 às 14:15:06 UTC (11:15:06 em São Paulo). Mesmo serviço Free; alterado somente o link público de download na configuração, preservando os segredos. Sem mudança de esquema, nova branch de banco ou recurso pago nesta revisão.

Após publicação:
- /api/public/config confirmou 0.14.1, storageMode=persistent e o download oficial 0.14.1.
- Dois exemplos fictícios e seus históricos completos preservados.
- Indicadores, grupos e dias da reserva de prova preservados; limpeza da prova concluída.
- Rotina HTTP verificou filtros combinados, isolamento por empresa, datas inválidas, CSV privado, atualização após cancelamento e 412 para empresa esperada divergente nos três endpoints.
- Seis cenários de navegador aprovados no site publicado em 37,4 segundos: desktop e Pixel 7 emulado, duas abas com troca real de sessão, respostas inválidas/negadas e download CSV real.
- Nenhum erro retornado na consulta aos logs entre o início da publicação, 14:12:43 UTC, e a verificação final, 14:18:32 UTC.

## Limites e evidências
A revisão não equivale a certificação ou auditoria externa. Mobile foi testado em navegador emulado, sem aparelho físico ou pacote Android/iOS nativo. A PWA compartilha o site; o banco local é independente. O CSV faz nova leitura dos mesmos filtros e pode refletir alterações posteriores à consulta da tela. Clientes antigos sem X-Agenda-Business mantêm isolamento pela sessão, mas não a checagem adicional da empresa esperada; recarregue site/PWA para usar a interface nova.

As garantias descritas correspondem aos cenários testados. Não há promessa de sanitização universal em todo programa de planilha nem de ausência de qualquer condição de concorrência no motor H2.

Logs locais, fora do Git: .cache/report-review-backend-full.log, report-review-ui-full.log, report-review-build.log, reports-review-e2e-green.log, reports-review-e2e-official.log, reports-review-e2e-live.log e reports-review-predeploy-backup.log. Reproduções anteriores: report-review-backend-red.log, report-review-backend-red-extra.log, report-review-ui-red.log, report-review-csv-red.log e reports-review-e2e-red.log. Provas com dados ficam em .cache/neon-private; resultados de navegador em frontend/test-results.
