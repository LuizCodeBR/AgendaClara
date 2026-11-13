# Validação — Agenda Clara 0.6.1

Revisão de segurança e concorrência com três achados reproduzidos antes das correções. Veja [o relatório](REVISAO-SEGURANCA-0.6.1.md).

## Validação local
- 160 testes Java aprovados, zero falhas, erros ou ignorados.
- 62 testes de interface aprovados, TypeScript e Vite aprovados.
- 18 cenários Playwright aprovados em desktop e Pixel 7 emulado.
- Cinco roteiros HTTP aprovados: smoke, date-hours-smoke, registration-smoke, account-access-smoke e mail-queue-smoke.
- Banco e chave da versão 0.6.0 abertos na 0.6.1, com recuperação da mesma mensagem privada anterior.
- Três rodadas de 12 inicializações concorrentes e quatro processos Java separados usando a mesma chave.
- npm audit: zero vulnerabilidades reportadas na árvore JavaScript em 13/11/2026.

## Reprodução anterior
Os testes de prazo manual, monitor durante lote lento e chave concorrente falharam no código anterior. Após as correções, passaram. O teste adicional de duas tentativas manuais confirmou que só uma agenda novo ciclo.

## Publicação
Os resultados de CI, pacotes oficiais e implantação serão acrescentados ao ramo principal após a publicação. O ZIP fonte conserva a documentação existente no commit da entrega.

## Ambiente e limites
Arquivos de trabalho e evidências no D:. Nenhuma nova biblioteca ou extensão instalada. Sem envio externo real, teste em aparelho físico ou alerta externo. O armazenamento gratuito permanece efêmero. A revisão não equivale a certificação ou auditoria externa.
