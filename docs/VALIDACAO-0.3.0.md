# Validação — Agenda Clara 0.3.0

Concluída em 12/11/2026 (America/Sao_Paulo). [Guia de folgas e horários especiais](EXCECOES.md).

## Versão

- Commit do aplicativo e tag v0.3.0: `f4c74ccbdea8c0c75f71691d80cc508675363dca`.
- [Site e PWA](https://agenda-clara-luizcodebr.onrender.com).
- [Código-fonte](https://github.com/LuizCodeBR/AgendaClara).
- [Downloads 0.3.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.3.0).

## Testes aprovados

[Execução final no GitHub Actions](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43331319846):

| Verificação | Resultado |
| --- | --- |
| Java 21 + H2 2.5.252 | 99 casos aprovados, zero falhas, erros ou ignorados |
| Java 26 + H2 2.5.252 | 99 casos aprovados, zero falhas, erros ou ignorados |
| Java 21 + PostgreSQL 17 | 99 casos aprovados, zero falhas, erros ou ignorados |
| Java 26 + PostgreSQL 17 | 99 casos aprovados, zero falhas, erros ou ignorados |
| Interface — Vitest | 38 testes aprovados |
| TypeScript e Vite | Compilação aprovada |
| Navegador — Playwright | 12 cenários aprovados, desktop e celular emulado |
| Docker + PostgreSQL | Construção, fluxo comum e fluxo de exceções aprovados |

Os 99 casos Java são executados nas quatro combinações; não representam 396 testes diferentes. Foram acrescentados 22 casos Java e oito da interface. Um novo fluxo completo de navegador, executado em desktop e celular, acrescenta dois cenários aos dez anteriores. Não houve teste em aparelho físico.

O fluxo novo fecha uma data, confirma a ausência de horários públicos, abre atendimento fora da semana, reserva esse horário, recusa fechamento/remoção incompatíveis, cancela a reserva e confirma o retorno à semana. A primeira execução encontrou um rótulo de seleção ambíguo no navegador; o campo ganhou associação explícita entre rótulo e seletor. A execução final passou integralmente.

Compilação local pelo iniciador do projeto aprovada, com Java 26.0.2 e Node 24.18. O ajuste final da interface também foi recompilado e testado. Evidências no D: em .cache/date-hours-final-build.log, .cache/date-hours-ui-final-tests.log e .cache/date-hours-ci-evidence.json.

## Migração e H2

Um teste de migração reproduziu o defeito de validação de CHECK do H2 2.4.240 ao trocar conexões. O teste foi mantido e passou após atualizar o componente para 2.5.252. Consulte o [registro do H2](https://github.com/h2database/h2database/issues/4302) e o [histórico de versões](https://h2database.com/html/changelog.html).

Além do teste automatizado V2 → V3, foi criado um banco novo com o JAR oficial 0.2.2/H2 2.4.240. Após encerrar o servidor e guardar uma cópia do arquivo, esse banco foi aberto com a 0.3.0/H2 2.5.252:
- Sete atendimentos e quatro profissionais, seus dados, serviços, bloqueios e expediente foram comparados por resumo SHA-256 antes/depois, nas duas contas fictícias.
- A comparação passou e a migração V3 foi aplicada.
- O fluxo de folga, abertura especial, reserva, proteção e retorno à semana também passou nesse banco atualizado.
- Evidências: .cache/upgrade-030-snapshot.json, .cache/upgrade-030-result.json e .cache/date-hours-upgrade-server.log.

Esse cenário verificado não representa garantia de compatibilidade de qualquer banco histórico ou alterado manualmente. A orientação de cópia/exportação antes de atualizar uma instalação está no guia.

O Flyway ainda informa que o H2 é mais recente que sua última versão declarada como verificada. As três migrações e os testes H2/PostgreSQL passaram nos ambientes acima.

OSV: 66 coordenadas Maven de compilação/execução consultadas após atualizar o H2, sem alertas retornados para as versões usadas. Registro em .cache/date-hours-audit.json. Isso não cobre vulnerabilidades desconhecidas ou a imagem inteira do sistema operacional. As dependências da interface não mudaram.

## Publicação e conferência pública

[Fluxo de downloads aprovado](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43331596459).

Mesmo espaço My Workspace e serviço gratuito Render `srv-dbqf9mon74is73c13k6g`. Deploy `dep-dbr50hlbedkc73cho6gg` ficou **live** em 12/11/2026 às 23:50:37 UTC, com o commit acima. O endereço de download foi atualizado por mesclagem, preservando as outras variáveis.

Após a publicação:
- Configuração pública retorna versão 0.3.0 e o link do pacote 0.3.0.
- HTML aponta para o JavaScript index--EYiaUl4.js da interface conferida.
- Interface, ícones, manifesto, reserva, repetição segura, login, rotação de sessão, cookies HTTPS, CSRF, limite de corpo, cancelamento e logout aprovados.
- Folga, abertura especial, reserva, recusa de fechamento/remoção incompatíveis, cancelamento, retorno à semana e inativação do profissional fictício aprovados.
- As reservas criadas para conferir a entrega foram canceladas; o profissional de verificação terminou inativo.
- Consulta dos logs de erro na janela de publicação/conferência não retornou registros.

## Downloads no D:

Pasta: `D:\Projetos\Entregas\AgendaClara-0.3.0-GitHub`.

| Arquivo | Tamanho em bytes | SHA-256 |
| --- | ---: | --- |
| AgendaClara-0.3.0-demo.zip | 28918615 | 68e95d350b59f646578ffdfd27b2c8380353ec1f2d08742a0ee54e2809c0fee4 |
| AgendaClara-0.3.0-fonte.zip | 221627 | 6fb7e8934b645050b784bbb3c4ae8b155c6391ee2f8d23d825d1d8876bfdc73a |

Os dois pacotes foram baixados da release e conferidos contra SHA256SUMS.txt. O compilado foi extraído em uma pasta com espaços e iniciado por iniciar-demo.cmd na porta 8084. A versão, os arquivos da interface, o fluxo comum e o novo fluxo de exceções foram conferidos nesse pacote. Os processos locais temporários foram encerrados após os testes.

O ZIP fonte registra a documentação da etapa anterior à publicação. Esta página no ramo main e sua cópia na pasta de entrega contêm os resultados finais.

Projeto, caches, temporários, arquivos de testes e entregas ficaram no D:. Não foi necessário programa ou extensão adicional. O H2 atualizado está incluído no JAR.

## Limites atuais

Feriados e folgas são configurados manualmente por profissional e data, de hoje a 365 dias adiante. A página pública oferece reservas dentro do horizonte configurado, de 60 dias na demonstração. Intervalos especiais substituem a semana e continuam respeitando os bloqueios.

O site gratuito continua sendo uma demonstração com contas fictícias compartilhadas e dados descartáveis. Antes de clientes reais, permanecem necessárias contas próprias, recuperação de acesso, persistência e backup/restauração.
