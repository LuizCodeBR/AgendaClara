# Validação — Agenda Clara 0.15.0

15/11/2026. Implementação, publicação e verificações concluídas. Código/tag v0.15.0: 5fdda90cc5fdc82540e2e339d9b26ca5f72f31ac. Arquivos, provas e temporários no D:.

- Backend local: 401 casos, 398 aprovados, três condicionais não aplicáveis e zero falhas/erros.
- 24 casos novos: 23 de acesso/convites/permissões/concorrência e um de migração V12 → V13.
- Interface: 176 testes aprovados, 12 novos; TypeScript/Vite aprovados.
- Navegador: 40 cenários aprovados em 54,4 segundos, desktop e Pixel 7 emulado; dois novos fluxos completos de convite, ativação, login, consulta e revogação.
- Primeira execução completa de navegador atingiu o limite normal de cadastros por minuto. Repetida com os limites existentes da CI, sem mudar os limites publicados.
- Atualização de cópia local 0.14.1 → 0.15.0 preservou dois exemplos fictícios/históricos completos e indicadores/grupos/dias da reserva de prova.
- PostgreSQL Neon Free: migração testada em branch temporária com compute 0,25 CU e expiração em 15/11/2026 18:26:49 UTC. As 38 contas anteriores permaneceram responsáveis, habilitadas e sem vínculo profissional; fluxo HTTP de convite, ativação, isolamento, dados mínimos, prazo e revogação aprovado.
- O ensaio PostgreSQL revelou que information_schema inclui a restrição NOT NULL como CHECK; corrigida a seleção das duas restrições com valores de ator, preservando a regra obrigatória.
- Sem nova dependência, extensão obrigatória, tarefa recorrente ou recurso pago. V13/ACBK0001.

Limites: mobile emulado, sem aparelho físico. Os fluxos novos de navegador validam atendimento futuro e a recusa de resultado antecipado; conclusão e correção após o término são verificadas com relógio controlado no backend e com testes da interface. Revisão não equivale a auditoria externa.

Evidências locais ignoradas pelo Git: .cache/staff-backend-full.log, staff-targeted-package.log, staff-ui-full.log, staff-ui-build.log, staff-e2e-local.log, staff-e2e-all-local-green.log, staff-upgrade-new.log e staff-neon-server-green.log. Provas privadas em .cache/neon-private.

## GitHub e recuperação
[CI 43746727453](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43746727453): seis grupos aprovados, código 5fdda90cc5fdc82540e2e339d9b26ca5f72f31ac.
- Java 21 e 26 com H2/PostgreSQL 17. Em PostgreSQL, 401 casos, 400 aprovados e um condicional, em cada versão do Java; zero falhas/erros.
- Interface: 176 testes. Navegador: 40 cenários aprovados em 57,8 segundos.
- Doze rotinas HTTP e sete provas após restauração: armazenamento, acesso privado do cliente, regras, resultados/histórico, contato/resumo, relatórios e acesso profissional.
- Conta profissional manteve identidade, senha e vínculo após restauração; convite pendente continuou válido com o prazo original, sem renovação.
- Docker/PostgreSQL: compilação, novo fluxo e persistência aprovados.

A branch Neon temporária br-lively-lab-bczj6zjo expirou e deixou de aparecer entre as ativas na verificação posterior. Servidor PostgreSQL de ensaio encerrado. O limite de suspensão padrão do plano Free foi preservado; não foi contratado recurso pago.

## Backup anterior à publicação
Criado e verificado sob demanda em D:/Projetos/AgendaClara/.cache/neon-operations/backups:
- AgendaClara-20261115T182653Z-978f5261-3626-41e2-aabb-c554930cceb2.acbackup.
- 63520 bytes; SHA-256 7c619bf8d1588d02e10fc92e0f2c8afa42e8c205aec2135a5b9efb1cccf31d87.
- VERIFIED, fileMatches=true, verificado em 15/11/2026 às 18:27:09 UTC.
- Código de saída 2 por cotas não informadas; backup concluído. Nenhuma rotina recorrente foi criada.
## Distribuição e pacote oficial Windows
[Publicação 43747021203](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43747021203): sucesso, com doze rotinas HTTP e sete provas após restauração antes de empacotar. [Entrega oficial](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.15.0).

Downloads conferidos pelos metadados GitHub e SHA256SUMS.txt, extraídos em D:/Projetos/Entregas/AgendaClara-0.15.0-GitHub:

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.15.0-demo.zip | 29211639 | fa4b442c9e554cbc30e017b8f7e907ade26a77a717c73a0e84b8ac6a6312b1a8 |
| AgendaClara-0.15.0-fonte.zip | 716820 | fb6cf67f3458b60bf608ddf69d8e88916ae9d196231a7257d1fe8fb65622a2ad |

O iniciar-demo.cmd oficial iniciou outra cópia do banco 0.14.1 em .cache/neon-private/staff-official-upgrade/data e aplicou V12 → V13. Dois exemplos fictícios e seus históricos completos preservados. Quatro cenários de navegador aprovados em 6,0 segundos: acesso profissional e relatórios/CSV, desktop e Pixel 7 emulado. Após reiniciar o pacote, a prova da conta profissional, reserva própria e convite pendente passou novamente; limpeza dos dados de prova concluída. Servidores locais de ensaio encerrados, instalações originais preservadas.

O pacote usa Java 21–27, sem Node/Maven/Docker para quem deseja apenas experimentar. Nenhuma instalação adicional foi necessária neste computador. Registros adicionais: .cache/staff-e2e-official.log, staff-official-server.log e staff-official-restarted.log.
## Site e banco principal
Render: deploy dep-dbsvl06i0phs73e9d030, código 5fdda90, live em 15/11/2026 às 18:33:51 UTC (15:33:51 em São Paulo). Mesmo serviço Free; somente o link de download foi atualizado na configuração, com mesclagem que preserva os segredos existentes. Migração V12 → V13 aplicada no banco principal pelo aplicativo, após validação isolada e backup.

Após publicação:
- /api/public/config confirmou 0.15.0, storageMode=persistent e download oficial 0.15.0.
- Dois exemplos fictícios e seus históricos completos preservados; indicadores, grupos e dias da reserva de prova idênticos.
- Rotina HTTP real verificou ativação, repetição recusada do convite, vínculo à própria agenda, dados mínimos, acesso administrativo negado, divergência de conta recusada, histórico reduzido, resultado antecipado recusado e revogação.
- Quatro cenários reais de navegador passaram em 45,9 segundos no site: convite/ativação/login/revogação e relatório/CSV, desktop e Pixel 7 emulado.
- Consulta final confirmou V13, 38 contas OWNER habilitadas e três contas PROFESSIONAL fictícias dos ensaios, todas revogadas. Convites de prova revogados; reservas canceladas e profissionais de prova desativados, preservando o histórico.
- Consulta aos logs entre 18:31:28 UTC e 18:36:11 UTC não retornou erros. A conclusão se limita à janela consultada.
- Nenhum servidor de ensaio escutando em 8090/8091 ao final. Cópias de banco e instalações originais preservadas no D:.

A orientação de atualização alerta que o binário antigo não reconhece as permissões profissionais. Não executar 0.14.1 ou anterior sobre o banco V13; recuperação para versão anterior exige backup compatível em destino separado. A instalação local mantém banco independente do site.
