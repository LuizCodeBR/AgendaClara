# Validação — Agenda Clara 0.6.0

## Escopo e ambiente
Fila transacional persistente, criptografia do conteúdo, recuperação de tarefas interrompidas e acompanhamento privado por empresa. Mesma compilação para web, PWA e execução local. Dados exclusivamente fictícios, modo demo e nenhum envio externo.

Código, dependências, temporários, pacotes e evidências desta validação foram mantidos em D:/Projetos. Não foi necessário instalar biblioteca ou extensão adicional. A validação local usou Java 26.0.2 e Node 24.18.0. A publicação ocorreu em 13/11/2026; os horários abaixo são UTC.

## Resultado dos testes
| Verificação | Resultado |
| --- | --- |
| Build local completo | 154 testes Java aprovados, zero falhas/erros/ignorados; 61 testes de interface aprovados; TypeScript e Vite aprovados |
| GitHub: Java 21 + H2 | 154 aprovados |
| GitHub: Java 26 + H2 | 154 aprovados |
| GitHub: Java 21 + PostgreSQL 17 | 154 aprovados |
| GitHub: Java 26 + PostgreSQL 17 | 154 aprovados |
| Interface e navegador no GitHub | testes da interface, compilação e 18 cenários Playwright aprovados em desktop e Pixel 7 emulado |
| Docker + PostgreSQL | cinco roteiros HTTP aprovados; aplicação recriada com banco e chave preservados e nova inicialização aprovada |
| JAR local | cinco roteiros HTTP aprovados |
| ZIP oficial baixado do GitHub | SHA-256 conferido, extraído no D:, iniciado pelo iniciar-demo.cmd, cinco roteiros HTTP aprovados |
| Site publicado por HTTPS | versão 0.6.0, modo demo e download 0.6.0 confirmados; cinco roteiros HTTP aprovados |
| Interface publicada por HTTPS | painel, filtro, logout e layout aprovados em desktop/Pixel 7 emulado; zero erros JavaScript |
| Logs do servidor publicado | zero registros de nível ERROR entre o início da nova aplicação e 03:05:55 UTC |

Roteiros: smoke, date-hours-smoke, registration-smoke, account-access-smoke e mail-queue-smoke. Cobrem reserva, repetição segura, CSRF, equipe, folgas, cadastro, publicação, confirmação, recuperação, revogação de sessões e monitoramento privado.

## Persistência e segurança verificadas
- Migração real de arquivo H2 V5 para V6 preservando contas, credenciais e links.
- Encerramento e reinício real do processo Java: mesma mensagem recuperada na caixa privada e no histórico com banco e chave original preservados.
- Rollback conjunto da outbox e alteração de acesso.
- Criptografia aleatória, autenticação dos identificadores e recusa de conteúdo adulterado.
- Reuso da chave persistida e recusa de chave ausente/incorreta.
- Quatro execuções concorrentes disputando uma tarefa, recuperação de concessão expirada e recusa de conclusão atrasada.
- Limite de tentativas, repetição manual, Retry-After, recusa definitiva e interrupção da terceira tentativa.
- Link utilizado/substituído, prazo de envio, validade e limpeza.
- Histórico privado, filtro válido, login obrigatório, CSRF, repetição de outra empresa recusada e ausência de destinatário/link no monitor.
- Interface com perda de conexão, sessão expirada, atualização, filtro e reagendamento.

Os testes de transporte usam respostas simuladas; nenhum recebimento externo foi validado sem remetente/serviço configurado.

## Publicação conferida
- Commit da aplicação e da tag v0.6.0: 4a205c9350cd62bb12a11fa5f7db3cb907e08ac1.
- [CI: seis trabalhos aprovados](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43355645902).
- [Publicação dos pacotes aprovada](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43355900855).
- [Entrega v0.6.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.6.0), publicada em 2026-11-13T03:00:18Z, marcada como pré-lançamento de demonstração.
- [Site](https://agenda-clara-luizcodebr.onrender.com).
- Render: serviço srv-dbqf9mon74is73c13k6g, plano free preservado; implantação dep-dbr7r25ckfvc73dfpkmg confirmada live em 2026-11-13T03:03:56.261584Z.
- Somente DEMO_DOWNLOAD_URL foi atualizado por mesclagem; configuração restante preservada. A alteração acionou uma implantação, sem criar serviço pago.
- Servidores Java locais usados nos testes foram encerrados.

## Downloads oficiais no D:
Pasta: D:/Projetos/Entregas/AgendaClara-0.6.0-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.6.0-demo.zip | 28994839 | a097d99a6dbc4559e8126d9bed33093e501f1918d093c3c131b8a10edef3cbab |
| AgendaClara-0.6.0-fonte.zip | 331713 | f8c27388b15687df0576b3aaafffcb112f20b9bf9bf799c9e31bc22cd6de262e |

O ZIP demo foi extraído em teste-pacote/AgendaClara-demo, iniciado na porta 8086 e encerrado após a validação. Os ZIPs oficiais permanecem intactos. Evidências sanitizadas, imagens e cópia deste relatório ficam em evidencias; não incluem chaves, cookies nem a prova privada de acesso aos testes.

O ZIP fonte contém o relatório preliminar disponível no commit da entrega. Este relatório final foi acrescentado ao ramo principal após a publicação, sem alterar a tag ou os arquivos já conferidos.

## Limites
Não há envio real configurado, confirmação de recebimento por webhook ou alerta externo. Aceitação pelo serviço não comprova entrega à caixa de entrada. Pixel 7 é emulação de navegador, não teste em aparelho físico.

A persistência resiste ao reinício da aplicação quando o armazenamento é preservado. Ela não sobrevive à remoção do armazenamento efêmero do Render gratuito. Banco e chave original são necessários para recuperar conteúdos protegidos; rotação automática da chave ainda não existe. Consulte [FILA-EMAILS.md](FILA-EMAILS.md) e [EMAIL-ACESSO.md](EMAIL-ACESSO.md).
