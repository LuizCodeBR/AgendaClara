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

Código e tag v0.13.1: c53bc220685418816d06ddd28b7ba17ffc5e5263.

- [CI 43671938125](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43671938125): seis grupos concluídos com sucesso, Java 21/26 em H2/PostgreSQL 17, Docker e navegador. Logs PostgreSQL de ambas as versões: 339 casos, 338 aprovados e um condicional; H2/Java 21: 337 aprovados e dois condicionais.
- Interface no CI: 137 testes. Navegador: 32 cenários aprovados em 53,4 s, desktop e Pixel 7 emulado; dez rotinas HTTP e backup/restauração com provas privadas preservadas.
- [Publicação 43672158775](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43672158775): sucesso. Dez rotinas HTTP e restauração verificaram armazenamento, sessão privada, regras, histórico e contato/resumo antes de publicar os arquivos.
- [Entrega 0.13.1](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.13.1), baixada e extraída em D:/Projetos/Entregas/AgendaClara-0.13.1-GitHub. Hashes conferidos com os metadados da publicação e SHA256SUMS.txt.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.13.1-demo.zip | 29152981 | 04eb3fc43f7e5a39de4de615080ffabc5dcf0201f099194cc6085f408ada797f |
| AgendaClara-0.13.1-fonte.zip | 626635 | 86506238a3604580b6d127a39f7199dc6f373a0194dc028fab51de7fb5760c18 |

## Atualização real no Windows

Copiados apenas banco/chave de um conjunto privado da 0.13.0 para .cache/neon-private/daily-review-upgrade/data, preservando os originais. O iniciar-demo.cmd oficial antigo iniciou a cópia; foram capturados os dois exemplos fictícios/históricos completos e preparada uma reserva com contato/versão/resumo.

Após encerramento normal, o iniciar-demo.cmd oficial 0.13.1 iniciou a mesma cópia: V11 validado sem migração; exemplos/históricos e contato/versão/lista preservados. Quatro fluxos reais específicos do resumo passaram no pacote oficial, em 4,3 s. Servidores locais de teste encerrados normalmente. Captura mobile inspecionada; verificação de largura sem transbordamento horizontal. Não foi usado aparelho físico.

Provas privadas: daily-review-local-originals.json e daily-review-upgrade-proof.json, em .cache/neon-private. Log: .cache/daily-review-official-e2e.log.

## Banco online e publicação

Backup nativo PostgreSQL antes de publicar, criado/verificado em .cache/neon-operations/backups: AgendaClara-20261115T032225Z-34e5fc9f-6a42-4ef3-affd-0511d7bb9b95.acbackup; 60843 bytes; SHA-256 f13866f3b58846c679a95d7ef5067134c3144b2d0122d7ead023d2041afa764b. Verificado em 2026-11-15T03:22:41.403732700Z. Cotas não informadas continuaram sinalizadas; o código 2 da rotina não indica falha do backup.

Render dep-dbsic056e07s739uuci0 ficou live em 2026-11-15T03:26:59.821625Z, 00:26 de 15/11 em São Paulo, no commit c53bc22. Mesclada apenas DEMO_DOWNLOAD_URL, preservando os segredos e demais configurações. Mesmo serviço Free, sem novo recurso ou alteração de plano. Não houve migração de banco.

Configuração HTTPS confirmou versão 0.13.1, storageMode persistent e download 0.13.1. Dois exemplos fictícios e seus históricos completos comparados antes/depois, sem alteração. Contato, versão e resumo preparados na 0.13.0 também permaneceram iguais. Rotinas preparatórias só usaram dados fictícios isolados; reservas canceladas e profissionais inativados na limpeza dos testes.

Quatro fluxos reais no site atualizado passaram em 36,3 s, desktop/mobile emulado: consulta, alteração externa de contato, cancelamento externo e resposta de gravação perdida. Log .cache/daily-review-live-e2e.log. Nenhum link externo foi seguido e nenhum WhatsApp enviado. Consulta de erros Render entre 03:24:48Z e 03:28:42Z não retornou registros; observação limitada a essa janela.

A primeira tentativa de capturar a prova online excedeu 30 segundos antes do login; repetida com limite maior, concluiu. Nenhum teste funcional falhou no site atualizado.

## Estado final

Sem dependência, extensão, ferramenta instalada, serviço pago ou rotina recorrente. Arquivos, provas, caches, backups e entregas permanecem no D:. O compartilhamento é manual e continua sendo uma fotografia da consulta; mudanças posteriores podem ocorrer e cópias já exportadas não são revogáveis. A revisão do código não substitui uma auditoria externa.