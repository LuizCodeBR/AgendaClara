# Validação 0.4.0 — cadastro de empresas

Código e evidências locais em D:\Projetos\AgendaClara. Escopo: [CADASTRO-EMPRESAS.md](CADASTRO-EMPRESAS.md).

## Validação local antes da publicação
- Java 26.0.2, alvo Java 21: **124 testes Java**, sem falhas.
- Node 24.18.0: **47 testes da interface**, sem falhas; TypeScript e Vite compilados.
- **14 cenários Playwright**, sem falhas: sete fluxos em desktop e sete em Pixel 7 emulado.
- Três roteiros HTTP no JAR real: smoke.mjs, date-hours-smoke.mjs e registration-smoke.mjs.
- Novo fluxo de navegador cria conta, entra com o e-mail preenchido, configura serviço/profissional/semana, publica, reserva, oculta, preserva/cancela atendimento e confirma indisponibilidade pública.
- Capturas do painel publicadas em .cache/registration-desktop-published.png e .cache/registration-mobile-published.png; layout conferido sem transbordamento horizontal.
- Verificados cadastro concorrente com mesma chave e com e-mail repetido, rollback sem empresa órfã, senha Unicode/limite UTF-8, CSRF, renovação da sessão, cota independente de cookies/IP, isolamento entre contas e atualização V3 para V4 preservando contas antigas.

O teste de navegador aguarda a conclusão da saída antes de abrir outro endereço; a navegação fica bloqueada durante a operação pendente.

## Publicação
A confirmação da matriz GitHub (Java 21/26, H2/PostgreSQL 17, Docker e navegadores), dos downloads oficiais e do site será acrescentada após a publicação. O ZIP de fonte registra esta validação local; os resultados finais serão mantidos no ramo principal.
