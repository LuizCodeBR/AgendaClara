# Validação — Agenda Clara 0.2.1

Revisão de segurança e arquitetura realizada em 11/11/2026. Relatório completo: [REVISAO-SEGURANCA-0.2.1.md](REVISAO-SEGURANCA-0.2.1.md).

## Validação local concluída

- Windows, Java 26.0.2, Node 24.18; projeto, caches, temporários e artefatos no D:.
- Compilação completa pelo iniciador PowerShell do projeto: aprovada.
- 77 casos Java, sem falhas, erros ou ignorados.
- 19 testes da interface aprovados; TypeScript e Vite aprovados.
- Testes HTTP com servidor real: JSON e formulário excessivos sem tamanho declarado, cabeçalhos e cookie.
- JAR 0.2.1 em porta local separada: interface, ícones, manifesto, reserva, repetição segura, login, rotação da sessão, CSRF, limite de corpo, cancelamento e logout aprovados.
- Auditorias de dependências: nenhum alerta retornado por npm audit e OSV para as 66 coordenadas Maven consultadas; escopo e limitações no relatório.

## Validação remota e distribuição

Aguardar a execução referente ao commit desta versão em [GitHub Actions](https://github.com/LuizCodeBR/AgendaClara/actions). Os links definitivos, resultados da hospedagem e somas dos downloads serão acrescentados após a execução. A aprovação local não substitui o resultado remoto.
