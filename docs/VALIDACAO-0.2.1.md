# Validação — Agenda Clara 0.2.1

Revisão concluída em 11/11/2026 (America/Sao_Paulo; 12/11 em UTC). [Relatório da revisão](REVISAO-SEGURANCA-0.2.1.md).

## Versão e acessos

- Commit do aplicativo e tag v0.2.1: `3247163aa12e8e217d59f4137e275b6d29b866ac`.
- [Código-fonte no GitHub](https://github.com/LuizCodeBR/AgendaClara).
- [Site e PWA](https://agenda-clara-luizcodebr.onrender.com).
- [Downloads 0.2.1](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.2.1).

## Testes

[Execução completa aprovada](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43127329083):

| Ambiente | Resultado |
| --- | --- |
| Java 21 + H2 | 77 casos, zero falhas, erros ou ignorados |
| Java 26 + H2 | 77 casos, zero falhas, erros ou ignorados |
| Java 21 + PostgreSQL 17 | 77 casos, zero falhas, erros ou ignorados |
| Java 26 + PostgreSQL 17 | 77 casos, zero falhas, erros ou ignorados |
| Interface — Vitest | 19 testes aprovados |
| Navegador — Playwright | 6 cenários aprovados: desktop e celular emulado |
| Docker + PostgreSQL | Construção e fluxo HTTP aprovados |

São 77 casos Java executados em quatro combinações, não 308 casos diferentes. A validação mobile automatizada usa emulação de navegador; não representa teste em um aparelho físico.

Localmente: Windows, Java 26.0.2, Node 24.18; compilação completa com o iniciador PowerShell, 77 casos Java, 19 da interface e TypeScript/Vite aprovados. Todos os caches, temporários e artefatos desta revisão foram direcionados ao D:.

Os testes HTTP reais verificam JSON e formulário excessivos sem tamanho declarado, cookies e cabeçalhos. O fluxo de entrega também verifica rotação do identificador de sessão, recusa por falta de CSRF e limite de corpo.

## Publicação e verificação pública

[Fluxo de downloads aprovado](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43127588199), segunda tentativa. A primeira parou ao baixar o Maven do repositório de dependências; a repetição passou sem alteração de código.

Render: mesmo serviço gratuito `srv-dbqf9mon74is73c13k6g`, espaço My Workspace. Deploy `dep-dbqghol6e07s73f51d8g` ficou **live** em 12/11/2026 às 00:33:36 UTC, com o commit acima. Apenas o endereço do download foi atualizado por mesclagem, preservando as demais variáveis.

Conferência após publicação:
- /api/public/config retorna 0.2.1 e o endereço do ZIP 0.2.1.
- HTML aponta para o arquivo JavaScript da revisão.
- Interface, ícones e manifesto acessíveis.
- Cookie HTTPS com Secure, HttpOnly e SameSite=Lax.
- Reserva fictícia, repetição segura, login, rotação de sessão, cancelamento e logout aprovados.
- Cadastro fictício de profissional, configuração de expediente, disponibilidade, recusa de edição antiga, inativação e isolamento entre empresas aprovados.
- Reservas criadas pela conferência foram canceladas; o profissional de teste terminou inativo.
- Consulta de logs de erro da nova instância, durante a janela verificada, não retornou erros.

## Downloads verificados no D:

Pasta: `D:\Projetos\Entregas\AgendaClara-0.2.1-GitHub`.

| Arquivo | Tamanho em bytes | SHA-256 |
| --- | ---: | --- |
| AgendaClara-0.2.1-demo.zip | 28889774 | 03c5b232a3a921b80bd593a9ea741e5d059a39812d6f6bcd55972f506d542d58 |
| AgendaClara-0.2.1-fonte.zip | 180904 | a38d3fdaa8fd0760fdebfb131d4f98579f7ff4742c047840a3e1e303ad61f755 |

Ambos foram baixados da release e conferidos contra SHA256SUMS.txt. O pacote compilado foi extraído em uma pasta com espaços e iniciado com iniciar-demo.cmd na porta 8084. O fluxo de segurança/reservas e a gestão de profissionais passaram também nesse pacote. Os servidores locais temporários foram encerrados após a verificação.

O relatório dentro do ZIP fonte registra a etapa local anterior à publicação; esta página no ramo main contém as evidências finais.

## Dependências e limites

npm audit não retornou alertas. A consulta OSV das 66 coordenadas Maven de compilação/execução também não retornou alertas. Os registros locais estão em .cache/review-npm-audit.json, .cache/review-maven-audit.json e .cache/review-ci-evidence.json. Não houve atualização de bibliotecas.

Não foi necessária instalação de programa ou extensão adicional. O ZIP compilado exige Java; o código-fonte exige JDK e Node, conforme o README. Site e PWA publicados podem ser usados pelo navegador.

A demonstração ainda usa contas fictícias compartilhadas e armazenamento efêmero na hospedagem. Cotas são por instância e não substituem controle de abuso e operação próprios de produção. O relatório de revisão detalha o que falta antes de receber clientes reais.
