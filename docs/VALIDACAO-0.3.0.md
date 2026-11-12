# Validação — Agenda Clara 0.3.0

Funcionalidade: [folgas e horários especiais](EXCECOES.md). Trabalho realizado em 12/11/2026 (America/Sao_Paulo).

## Validação local concluída

- Compilação completa pelo scripts/build.ps1: aprovada.
- 99 casos Java, zero falhas, erros ou ignorados.
- 38 testes da interface aprovados; TypeScript e Vite aprovados.
- H2 atualizado para 2.5.252 após o teste de migração reproduzir o defeito da versão 2.4.240.
- Banco criado pela versão 0.2.2 encerrado e aberto com a 0.3.0: sete atendimentos e quatro profissionais, seus dados, serviços e expediente comparados e preservados.
- Migração V2 → V3 e fluxo HTTP de folga, abertura excepcional, reserva, proteção contra fechamento/remoção e retorno à semana aprovados.
- OSV: 66 dependências Maven de compilação/execução consultadas, sem alertas retornados para as versões usadas.
- Projeto, caches, temporários, dados fictícios de teste e artefatos mantidos no D:.

A versão será verificada com Java 21/26, H2/PostgreSQL, Docker e navegador desktop/celular. Consulte [GitHub Actions](https://github.com/LuizCodeBR/AgendaClara/actions). Esta página no ramo main receberá os resultados finais, links de publicação e somas dos downloads após a conferência.
