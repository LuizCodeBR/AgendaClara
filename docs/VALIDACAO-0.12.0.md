# Validação 0.12.0 — Resultados e histórico

14/11/2026. Arquivos, temporários, provas privadas e pacotes no D:.

Windows / Java 26 / H2: 299 casos, 296 aprovados e três condicionais desativados, zero falhas. Interface: 105 testes aprovados; TypeScript/Vite compilados. Navegador: 28 cenários aprovados, 14 desktop e 14 Pixel 7 emulado.

Há 19 casos novos Java: classificação no limite, recusa antecipada, cancelados, justificativa, versão antiga, repetição/chave reutilizada, falhas com rollback, histórico das alterações, paginação, registros legados, permissões/CSRF/empresa, entradas inválidas, área do cliente, três disputas concorrentes e migração 9 → 10. Backups H2/PostgreSQL incluem resultado e evento histórico na mesma fixture já usada para chave, sessão e regras.

Doze testes novos de interface cobrem confirmação, correção, justificativa, repetição após falha, conflito, rascunho, falta de conexão, paginação, histórico vazio, filtros, contadores e resultado no portal.

Os dois novos testes de navegador usam respostas controladas para representar atendimentos encerrados, sem precisar adulterar o relógio do servidor ou aguardar um atendimento real. Conferem conclusão, correção justificada, histórico e ausência de transbordamento em desktop/mobile. A autorização e a classificação reais são testadas no backend com relógio controlado; a rotina HTTP real confere criação/remarcação/cancelamento com histórico e recusa de resultado antecipado. Nenhum aparelho físico foi utilizado.

Ensaio Neon Free em cópia temporária: esquema 9 → 10 preservou 37 empresas, 37 contas, 47 reservas, 47 mensagens e 28 acessos privados. Histórico iniciou vazio, sem classificação retroativa. O fluxo HTTP real será registrado ao finalizar.

Distribuição e publicação serão registradas ao concluir.
