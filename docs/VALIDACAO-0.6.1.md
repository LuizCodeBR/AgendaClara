# Validação — Agenda Clara 0.6.1

## Escopo e ambiente
Revisão de segurança e concorrência do estado publicado 0.6.0, com três achados reproduzidos antes das correções. Leia [o relatório da revisão](REVISAO-SEGURANCA-0.6.1.md) para condições, impacto e implementação.

Código, dependências, temporários, pacotes e evidências permanecem em D:/Projetos. A validação local usou Java 26.0.2 e Node 24.18.0. Nenhuma nova biblioteca ou extensão foi instalada. Publicação em 13/11/2026; horários de infraestrutura abaixo em UTC.

## Testes e verificações
| Verificação | Resultado |
| --- | --- |
| Build local | 160 testes Java, 62 da interface, TypeScript e Vite aprovados |
| CI Java 21 + H2 | 160 aprovados |
| CI Java 26 + H2 | 160 aprovados |
| CI Java 21 + PostgreSQL 17 | 160 aprovados |
| CI Java 26 + PostgreSQL 17 | 160 aprovados |
| Interface e navegador no CI | 62 testes e 18 cenários Playwright aprovados |
| Docker + PostgreSQL | cinco roteiros HTTP e recriação da aplicação com chave preservada aprovados |
| Navegador local | 18 cenários aprovados, desktop e Pixel 7 emulado |
| JAR local | cinco roteiros HTTP aprovados |
| ZIP oficial baixado | SHA-256 conferido; extração no D:, início pelo iniciar-demo.cmd e cinco roteiros aprovados |
| Site publicado por HTTPS | versão 0.6.1, mailMode=demo e download 0.6.1 confirmados; cinco roteiros aprovados |
| Interface publicada | painel, filtro, modo simulado e logout aprovados em desktop e Pixel 7 emulado; sem transbordamento ou erros JavaScript |
| Logs publicados | zero registros de nível ERROR entre 03:33:50 e 03:36:33 UTC |
| npm audit | zero vulnerabilidades reportadas na árvore JavaScript no catálogo consultado em 13/11/2026 |
| Permissões da chave local | somente o proprietário com acesso permitido no Windows; conteúdo não exibido |

Roteiros: smoke, date-hours-smoke, registration-smoke, account-access-smoke e mail-queue-smoke. Usam dados fictícios e verificam reserva, repetição segura, CSRF, equipe, folgas, cadastro, publicação, confirmação, recuperação, revogação de sessões e monitor privado.

## Provas específicas da revisão
Antes das correções, a execução de 17 testes específicos apresentou três falhas e um erro: dois casos de espera do provedor, o monitor em lote lento e a leitura concorrente da chave. O teste da interface também falhou ao exigir uma indicação de espera diferente de envio automático.

Após as correções, passaram os testes específicos e a suíte completa. Foram acrescentados seis testes Java e um de interface em relação à 0.6.0.

- Espera de uma resposta 429 na última tentativa automática também impede tentativa manual antecipada.
- Espera maior que a validade não permite contornar o prazo pelo ciclo manual.
- Duas chamadas manuais simultâneas permitem somente um novo ciclo.
- Dez chamadas simuladas de dez segundos mantêm o sinal de atividade recente.
- Três rodadas de 12 inicializações concorrentes do componente da chave convergem para uma chave íntegra e compatível.
- Quatro processos Java separados executam o componente da chave usando o mesmo arquivo e convergem para a mesma identidade.
- Uma cópia do banco e da chave da versão 0.6.0 foi aberta pela 0.6.1, recuperando a mesma mensagem privada anterior.

Os testes Java de transporte usam respostas simuladas. Nenhum e-mail externo foi enviado.

## Publicação conferida
- Commit da aplicação e da tag v0.6.1: 9706d2b7b57700b988741764b47175ad6b318f03.
- [CI com seis trabalhos aprovados](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43359157633).
- [Publicação dos downloads aprovada](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43359495612).
- [Entrega v0.6.1](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.6.1), publicada em 2026-11-13T03:31:44Z como pré-lançamento de demonstração.
- [Site](https://agenda-clara-luizcodebr.onrender.com).
- Render: serviço srv-dbqf9mon74is73c13k6g, plano free preservado; implantação dep-dbr89qmtlv9c73foriig confirmada live em 2026-11-13T03:35:10.377148Z.
- Somente DEMO_DOWNLOAD_URL foi atualizado por mesclagem; variáveis restantes preservadas. Não foi criado recurso pago.
- Servidores Java locais utilizados nesta validação foram encerrados.

## Arquivos oficiais
Pasta: D:/Projetos/Entregas/AgendaClara-0.6.1-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.6.1-demo.zip | 28998166 | f93b3d413c427ee64bebf9d7885612c2b1a5c1f0b082b6255605d6ddbb278e5e |
| AgendaClara-0.6.1-fonte.zip | 343431 | 81f5caab5be02bf63eba2007e0d6afc6a023f859f7fe8403a2166135fe794873 |

O ZIP demo foi extraído em teste-pacote/AgendaClara-demo e testado na porta 8086. Os arquivos oficiais permanecem intactos. A pasta evidencias reúne relatórios, registros sanitizados e imagens, sem chaves nem cookies.

O ZIP fonte conserva a documentação existente no commit da entrega. Este relatório final foi acrescentado ao ramo principal após a publicação, sem alterar a tag nem os arquivos distribuídos.

## Limites
A revisão não equivale a certificação ou auditoria externa, nem garante ausência de vulnerabilidades. A consulta npm não representa análise completa das dependências Java.

Não há envio real configurado, confirmação de entrega por webhook ou alerta externo. Aceitação pelo provedor não comprova recebimento. O celular foi emulado no navegador, sem teste em aparelho físico.

A hospedagem gratuita continua com armazenamento efêmero. Banco e chave original precisam ser preservados para recuperar conteúdos; o bloqueio de arquivo não substitui backup. A revisão manteve os limites de frequência por processo e a revogação das sessões na próxima chamada à API. Consulte [REVISAO-SEGURANCA-0.6.1.md](REVISAO-SEGURANCA-0.6.1.md) e [FILA-EMAILS.md](FILA-EMAILS.md).
