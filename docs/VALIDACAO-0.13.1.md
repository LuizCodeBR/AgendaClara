# Validação 0.13.1 — revisão do resumo diário

15/11/2026. Projeto e evidências locais em D:/Projetos/AgendaClara. [Achados e limites](REVISAO-RESUMO-0.13.1.md).

## Testes locais concluídos

- Cinco regressões reproduzidas na 0.13.0 antes das correções: contato alterado durante a prévia, resposta de gravação perdida, consulta atrasada, saída para segundo plano e resultado tardio da área de transferência.
- Windows/Java 26/H2: Maven verify aprovado, 339 casos, 336 aprovados e três condicionais desativados, zero falhas/erros. Acréscimo de três testes de backend, incluindo disputa real entre remoção do contato e edição do cadastro.
- Interface: 137 testes aprovados, 14 novos; TypeScript/Vite compilados.
- Quatro fluxos reais específicos no navegador: desktop e Pixel 7 emulado. Incluem mudança externa de telefone/cancelamento e resposta perdida após a gravação efetiva no servidor. Nenhum link do WhatsApp foi seguido e nenhuma mensagem foi enviada.
- render.yaml validado com o esquema oficial atual: mesmo serviço Free, sem novo banco/disco pago.
- Esquema V11, formato ACBK0001, dependências e versões das ferramentas preservados.

Logs privados: .cache/daily-review-red.log, daily-review-green.log, daily-review-ui.log, daily-review-build.log, daily-review-backend-targeted.log, daily-review-backend.log, daily-review-e2e.log. Capturas do navegador ficam em frontend/test-results, fora do Git.

## Publicação e distribuição

Verificações no GitHub, pacote oficial e site serão registradas aqui após sua conclusão. Não houve contratação de recursos, criação de rotinas recorrentes ou instalação de ferramentas.