# Validação — Agenda Clara 0.2.2

Concluída em 11/11/2026 (America/Sao_Paulo; 12/11 em UTC). [Revisão de uso e confiabilidade](REVISAO-USO-0.2.2.md).

## Versão

- Tag v0.2.2 e commit conferido: `d70f32f25f0c8e77ea2829b23ecd935cd6460647`.
- [Site/PWA](https://agenda-clara-luizcodebr.onrender.com).
- [Código-fonte](https://github.com/LuizCodeBR/AgendaClara).
- [Downloads](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.2.2).

## Testes aprovados

[Execução final no GitHub Actions](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43133614392):

| Verificação | Resultado |
| --- | --- |
| Java 21 + H2 | 77 casos aprovados, zero falhas, erros ou ignorados |
| Java 26 + H2 | 77 casos aprovados, zero falhas, erros ou ignorados |
| Java 21 + PostgreSQL 17 | 77 casos aprovados, zero falhas, erros ou ignorados |
| Java 26 + PostgreSQL 17 | 77 casos aprovados, zero falhas, erros ou ignorados |
| Interface — Vitest | 30 testes aprovados |
| TypeScript e Vite | Compilação aprovada |
| Playwright — desktop e celular emulado | 10 cenários aprovados |
| Docker + PostgreSQL | Construção e fluxo HTTP aprovados |

Os 77 casos Java são executados nas quatro combinações; não representam 308 casos distintos. A validação mobile é feita com emulação de navegador, não em aparelho físico.

Foram acrescentados onze testes da interface e dois cenários de Playwright, cada um executado em desktop e celular. Sete testes de regressão reproduziram problemas antes das correções. Na primeira execução remota, o novo cenário mobile tentou encontrar uma opção com o menu fechado; o teste foi ajustado para abrir o menu, e a execução final passou integralmente.

Compilação completa local aprovada no Windows, com Java 26.0.2 e Node 24.18. Projeto, caches, temporários e arquivos produzidos no D:. Evidências em .cache/review-usage-build.log e .cache/review-usage-ci-evidence.json.

## Publicação

[Fluxo de downloads aprovado](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43133874568).

Render: mesmo espaço My Workspace e serviço gratuito `srv-dbqf9mon74is73c13k6g`. Deploy `dep-dbqh9flg1s2s73eb77q0` ficou **live** em 12/11/2026 às 01:24:11 UTC, com o commit acima. Atualização por mesclagem apenas do endereço de download; demais variáveis preservadas.

Após a publicação:
- Configuração pública confirma versão 0.2.2 e link de download correto.
- HTML aponta para o JavaScript index-CgrRyekv.js da versão revisada.
- Interface, ícones, manifesto, reserva fictícia, repetição segura, login, rotação da sessão, cookies HTTPS, CSRF, limite de corpo, cancelamento e logout aprovados.
- Cadastro fictício de profissional, expediente, disponibilidade, recusa de edição desatualizada, inativação e isolamento entre estabelecimentos aprovados.
- Reservas de verificação canceladas; profissional de teste deixado inativo.
- Consulta de logs de erro na janela de publicação não retornou registros.

Os comportamentos de interface novos foram verificados nos testes de navegador contra o aplicativo compilado. A conferência pública verificou os arquivos publicados e os fluxos HTTP; não substitui teste em celular físico.

## Pacotes no D:

Pasta de entrega: `D:\Projetos\Entregas\AgendaClara-0.2.2-GitHub`.

| Arquivo | Tamanho em bytes | SHA-256 |
| --- | ---: | --- |
| AgendaClara-0.2.2-demo.zip | 28889618 | 5dca3113704ab2179e1d59529d4213521bf55cd4ecbe2b62bc4492c67fd89cb9 |
| AgendaClara-0.2.2-fonte.zip | 192663 | e70f331812766a2b8d9152b5e2270aec1d661001ea8120b728a606a061ca2b4f |

Downloads obtidos da release e conferidos contra SHA256SUMS.txt. A demonstração foi extraída em uma pasta com espaços e iniciada por iniciar-demo.cmd na porta 8084. A versão, o arquivo da interface, os fluxos de segurança/reservas e a gestão de profissionais foram conferidos nesse pacote. O servidor temporário foi encerrado ao terminar.

O relatório no ZIP fonte registra a etapa anterior à publicação. Esta página no ramo main e sua cópia na pasta de entrega contêm os resultados finais.

## Necessidades e limites

Não houve nova dependência, extensão, migração do banco ou plano pago. A instalação já existente atende ao desenvolvimento; o pacote compilado exige Java, e o site/PWA publicado funciona pelo navegador.

As proteções da versão 0.2.1 foram preservadas. A hospedagem continua destinada a demonstração, com contas fictícias compartilhadas e dados descartáveis. Contas próprias, recuperação de acesso, persistência e backup/restauração continuam necessários antes de uso com clientes reais.

Para próximas alterações, revisar o fluxo afetado e seus erros; aprofundar segurança/concorrência nas mudanças de acesso, dados ou reservas; validar web/mobile e distribuição antes de publicar.
