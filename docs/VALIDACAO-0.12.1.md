# Validação 0.12.1 — revisão dos resultados e histórico

14/11/2026, Windows, código em D:/Projetos/AgendaClara. [Escopo e achados](REVISAO-RESULTADOS-0.12.1.md).

## Resultados locais confirmados

- Backend Java 26/H2: 317 casos, 314 aprovados, três condicionais desativados, zero falhas/erros. Maven verify concluiu e gerou o JAR 0.12.1.
- Interface: 108 testes aprovados; TypeScript e Vite compilados.
- Navegador: 28 cenários aprovados (14 desktop e 14 Pixel 7 emulado).
- HTTP real: resultado antes do término recusado; entradas numéricas e caractere nulo recusados com 400; histórico, autor, remarcação, cancelamento e acesso privado aprovados.
- render.yaml validado contra o esquema oficial atualizado: um serviço web Free, sem banco/disco pago; alterado somente o link do download.

## Novos testes

18 casos de backend: 12 em AppointmentOutcomeReviewTest e seis em AppointmentOutcomeConcurrencyReviewTest. Cobrem entrada malformada, retrocesso do relógio, exigência de transação, rollback de correção com reutilização da chave, paginação durante novas alterações, colisão simultânea de chave em profissionais diferentes, disputa com rotação/revogação, espera por remarcação com versão antiga ou adivinhada e leitura durante transação não confirmada.

Três casos novos de interface verificam resposta antiga após troca de atendimento, cancelamento de paginação ao fechar e texto HTML tratado como texto. Antes das correções, os testes reproduziram aceitação de quatro resultados numéricos, caractere nulo, dois cancelamentos após retrocesso do relógio e ausência dos guardas de transação. Dois testes de concorrência de respostas da interface também falharam na implementação anterior.

As duas jornadas de resultados da suíte de navegador usam respostas controladas; as regras e disputas reais são exercitadas nos testes Java/banco e HTTP. Nenhum aparelho físico foi usado.

## Compatibilidade e publicação

Sem migração nova: esquema V10 e formato ACBK0001. Nenhuma instalação, dependência, extensão, serviço pago ou tarefa recorrente. Backups e limites continuam sob demanda. Código, caches, provas privadas e entregas ficam no D:.

Integração, distribuição e atualização concluídas; evidências abaixo.

## Integração Linux e publicação

Commit de código e tag v0.12.1: 6f212f1ef896cb77ee1b7f965f20f86de1b4a8c0.

[CI 43664012760](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43664012760): os seis grupos passaram. Matriz Java 21/26 com H2/PostgreSQL 17, Docker e navegador. Nos logs conferidos de Java 21, H2 teve 317 casos/315 aprovados/dois condicionais desativados; PostgreSQL teve 317/316/um condicional. Os 18 casos novos passaram também no PostgreSQL. Os outros dois grupos da matriz também terminaram com sucesso. Interface: 108 testes; navegador: 28. O CI executou as nove rotinas HTTP e o backup/restauração com provas de armazenamento, regras, sessão privada e histórico.

[Publicação 43664280825](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43664280825): sucesso, incluindo nove rotinas HTTP e provas de restauração antes de distribuir.

| Pacote oficial | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.12.1-demo.zip | 29139201 | e428a13d11ca417ec7e012ad539dfa40ee42d687b3452ca5ee1e6a64221b215b |
| AgendaClara-0.12.1-fonte.zip | 585033 | d2530dffea4b387701ef376df96db2bf4ef8d8a7bb40bbda0454fc324dbd4b3b |

Baixados em D:/Projetos/Entregas/AgendaClara-0.12.1-GitHub, com conferência dos hashes da publicação e de SHA256SUMS.txt. A demonstração está extraída em AgendaClara-demo.

## Atualização local e restauração

Foi usada uma cópia separada do banco e da chave da demonstração 0.12.0, preservando os originais. Com o pacote antigo, foram capturados dois exemplos fictícios já classificados e seu histórico integral, além de uma nova reserva com sessão privada. O JAR 0.12.1 e depois o iniciar-demo.cmd oficial mantiveram os exemplos e eventos exatamente iguais. A sessão e a reserva também sobreviveram à atualização. Esquema permaneceu V10.

Dois fluxos adicionais no pacote oficial, sem interceptar a API: desktop e Pixel 7 emulado. Cada um corrigiu o resultado de um dos exemplos fictícios, corrigiu-o novamente, consultou o histórico e verificou o estado no portal privado. Os exemplos terminaram NO_SHOW, com quatro eventos cada (dois anteriores e duas correções novas). A captura mobile foi inspecionada, sem transbordamento horizontal.

O operacao.cmd oficial criou e verificou o backup abaixo; backup.cmd restaurou em uma pasta nova no D:. Reiniciado com iniciar-demo.cmd, o banco restaurado preservou os dois históricos integrais, resultados, versões e a prova de sessão privada.

- Backup Windows/H2: AgendaClara-20261115T014505Z-dec7824a-effe-4da5-8076-5c8a3d719e32.acbackup.
- 56364 bytes; SHA-256 c0c387a56b9ddde38b7e1790971ccf4411e8bb44cb9d1f5fc1cc0e2948bc38c6.
- Verificado em 2026-11-15T01:45:06.561036200Z.
- Provas privadas em .cache/neon-private; relatórios em .cache/outcome-review-package-operations.
- Os servidores locais usados nos testes foram encerrados. Nenhuma tarefa recorrente foi criada.

## Banco online e site

Backup manual anterior à atualização, preservado no D:, usando conexão direta existente:

- AgendaClara-20261115T014032Z-4266fa80-66df-4133-add7-0f659b3f2848.acbackup, PostgreSQL.
- 58416 bytes; SHA-256 410d8afa9c5e88e96ad0891554196914848e5b91a3fb927b8d4c2b34296051d3.
- Verificado em 2026-11-15T01:40:49.314151Z.
- Relatórios em .cache/neon-operations. A rotina retorna sinal de atenção para cotas não informadas; não se presumiu consumo zero.

Render dep-dbsgsaon74is739rghd0: live em 2026-11-15T01:45:21.511468Z (22:45 de 14/11 em São Paulo), commit 6f212f1. Serviço Free existente, um único deploy. Atualizado somente DEMO_DOWNLOAD_URL por mesclagem; demais variáveis preservadas. Nenhum recurso criado ou plano alterado.

A consulta HTTPS confirmou versão 0.12.1, storageMode persistent e download 0.12.1. Os dois exemplos fictícios e seus eventos foram comparados antes/depois e preservados integralmente. A rotina HTTP passou no site, incluindo números e U+0000 recusados.

Depois da conferência de preservação, foram repetidos dois fluxos reais no site, desktop e Pixel 7 emulado, sem interceptação da API. Somente os dois exemplos originais fictícios João Exemplo/Maria Exemplo, de 14/11, receberam as duas correções de teste; o estado final continua NO_SHOW e cada histórico passou de dois para quatro eventos. A rotina HTTP também criou e cancelou uma reserva fictícia isolada. Isso são dados de teste explícitos, não classificação automática de registros anteriores.

Consulta de logs ERROR do Render entre 01:43:07Z e 01:46:46Z de 15/11: nenhum registro retornado. Essa observação se limita à janela consultada.

Sem aparelho físico, auditoria externa, novo provedor de e-mail, custo contratado ou automação periódica. A revisão é limitada aos fluxos e cenários descritos.
