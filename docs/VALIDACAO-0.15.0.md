# Validação — Agenda Clara 0.15.0

15/11/2026. Registro em andamento; publicação e resultados remotos serão acrescentados após verificação. Arquivos, provas e temporários no D:.

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
