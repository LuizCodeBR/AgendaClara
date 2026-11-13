# Validação — Agenda Clara 0.7.0

## Escopo e ambiente
Backups criptografados do banco e da chave, restauração em destino separado, exportação PostgreSQL nativa e configuração explícita de armazenamento. Código, dependências, temporários, pacotes e evidências no D:/Projetos.

Java local 26.0.2 e Node 24.18.0. Dependências do aplicativo e extensões do editor preservadas. Clientes oficiais PostgreSQL 17.12 foram extraídos de forma portátil no D: para exportar o Neon e restaurar em um servidor isolado; verificadores de esquema Render também ficam em .cache. Não foi instalado serviço Windows nem alterado o PATH. Datas de infraestrutura abaixo em UTC, em 13/11/2026.

## Resultados finais
| Verificação | Resultado |
| --- | --- |
| Build local | 171 testes Java aprovados; um teste nativo PostgreSQL condicional não executado localmente; 63 testes da interface, TypeScript e Vite aprovados |
| CI Java 21 + H2 | 171 aprovados e um teste PostgreSQL condicional não executado |
| CI Java 26 + H2 | 171 aprovados e um teste PostgreSQL condicional não executado |
| CI Java 21 + PostgreSQL 17 | 172 aprovados, incluindo pg_dump/pg_restore reais |
| CI Java 26 + PostgreSQL 17 | 172 aprovados, incluindo pg_dump/pg_restore reais |
| Interface e navegador no CI | 63 testes e 18 cenários Playwright aprovados |
| Navegador local em banco restaurado | 18 cenários aprovados em desktop e Pixel 7 emulado |
| Docker + PostgreSQL | cinco roteiros HTTP e recriação da aplicação com chave preservada aprovados |
| Restauração pelo JAR no CI | backup criptografado, verificação, pasta nova e mesma conta/mensagem recuperadas por HTTP |
| Pacote oficial baixado | hashes SHA-256 conferidos; cinco roteiros HTTP aprovados |
| backup.cmd do pacote oficial | criação, verificação, restauração e inicialização com a mesma empresa/conta/senha/mensagem aprovadas |
| Permissões no Windows | backup criptografado e chave restaurada com acesso permitido somente ao proprietário |
| Site HTTPS | versão 0.7.0, mailMode=demo, storageMode=persistent e download 0.7.0 confirmados; cinco roteiros aprovados no PostgreSQL Neon |
| Interface HTTPS | painel, filtro, aviso simulado e logout em desktop/Pixel 7 emulado; sem transbordamento ou erros JavaScript |
| Persistência HTTPS entre implantações | mesma empresa/conta/senha/mensagem recuperadas após substituição do servidor web |
| Backup real Neon → PostgreSQL local | arquivo criptografado exportado com o site ativo, verificado, restaurado em banco vazio e mesma conta/mensagem recuperadas por HTTP |
| Navegador sobre PostgreSQL restaurado | 18 cenários aprovados em 28,1 segundos, desktop e Pixel 7 emulado |
| Blueprint | render.yaml validado com esquema oficial JSON Schema 2020-12; web free, sem banco/disco pago e quatro entradas privadas |
| Logs do site | zero registros de nível ERROR entre 19:52:36 e 19:57:00 UTC |

Roteiros HTTP: smoke, date-hours-smoke, registration-smoke, account-access-smoke e mail-queue-smoke. O roteiro storage-smoke compara os mesmos identificadores antes/depois da restauração, sem imprimir a prova privada ou senha.

## Cobertura acrescentada
Nove testes do arquivo e backup local verificam recuperação de empresa, conta, hash e conteúdo do mesmo e-mail, banco em uso, destino existente, chave ausente/incorreta, senha incorreta, adulteração, truncamento, caminho inesperado, limites de leitura e ausência de senha na saída.

O teste PostgreSQL cria duas bases isoladas no serviço temporário do CI, exporta uma com pg_dump, restaura na outra com pg_restore, confere dados e chave, descriptografa a mensagem e confirma a continuidade da sequência queue_seq. A segunda tentativa de restaurar no destino já preenchido é recusada.

Dois testes da configuração de armazenamento verificam os modos permitidos, exigência de PostgreSQL e chave estável para persistent. A interface verifica a distinção entre demonstração temporária/persistente, mantendo o aceite de dados fictícios.

Os nomes de clientes dos testes de navegador passaram a ser únicos por execução, permitindo rodar sobre uma base restaurada com histórico. O iniciador Linux/macOS foi corrigido de uma referência antiga 0.3.0 para a versão atual.

## Publicação
- Commit principal da funcionalidade: 56f8a74278f2e454002af7850dcca6bce25ba2d5.
- Commit da aplicação distribuída/tag v0.7.0: b238aabbde1ced98c7038dee3169abacd704167d, incluindo exclusão de bancos restaurados e dumps dos pacotes de fonte.
- [CI final: seis trabalhos aprovados](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43491461456).
- [Publicação dos pacotes aprovada](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43491996030).
- [Entrega v0.7.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.7.0), publicada em 2026-11-13T19:25:56Z como pré-lançamento de demonstração.
- [Site](https://agenda-clara-luizcodebr.onrender.com).
- Render inicial H2: dep-dbrm95p5efls73dbvo40, live em 2026-11-13T19:29:57.847467Z.
- Ativação Neon: dep-dbrmhrkipgvc739i1l8g, live em 2026-11-13T19:47:35.384814Z; seis migrações aplicadas em PostgreSQL 17.12.
- Nova implantação para provar persistência: dep-dbrmk695efls73dd0uig, live em 2026-11-13T19:52:36.572194Z; mesma conta e mensagem verificadas depois.
- Serviço srv-dbqf9mon74is73c13k6g no plano free preservado. Configurações PostgreSQL, chave estável e pool pequeno atualizadas por mesclagem, com MAIL_MODE=demo.
- Projeto Neon sweet-dream-41359619, branch br-dry-band-bcbho3aa, região aws-us-east-1; organização confirmada no plano free, compute fixo 0,25 CU e histórico configurado de seis horas.
- Snapshot manual snap-lively-credit-bcn6ett1 criada em 2026-11-13T19:54:23Z e conferida pela listagem. A restauração testada foi a do arquivo criptografado; não foi ensaiada a restauração da snapshot do provedor.
- Os servidores Java locais de validação foram encerrados.

## Downloads e evidências
Pasta: D:/Projetos/Entregas/AgendaClara-0.7.0-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.7.0-demo.zip | 29029173 | 53bd17626835c52257a032f99024ed2f030bcfd4242e883fc74801b5d6c77dd8 |
| AgendaClara-0.7.0-fonte.zip | 376223 | 456f0c6df54b41c31850a0a34e27c2d297760724fa2cc5c35a9def255797c0c0 |

O pacote foi extraído em teste-pacote/AgendaClara-demo, iniciado na porta 8086 e restaurado em teste-restaurado/data. Os arquivos oficiais continuam intactos. Evidências sanitizadas, imagens e relatórios ficam em evidencias; não incluem chaves, senhas, cookies, fixtures de acesso ou bancos.

O ZIP fonte conserva o relatório disponível no commit da entrega. Este relatório final foi acrescentado ao ramo principal depois da publicação, sem alterar tag ou arquivos distribuídos.

## Persistência online concluída sem recurso pago
O usuário recusou a proposta paga e conectou a integração Neon no plano Free. Banco PostgreSQL 17 criado e vinculado ao site com TLS e chave estável. O teste comparou identificadores reais de dados fictícios antes/depois da nova implantação e, depois, na cópia restaurada localmente.

Backup criptografado de 13.549 bytes e arquivos privados ficam em D:/Projetos/AgendaClara/.cache/neon-private, com ACL somente do usuário Windows atual e exclusão pelo Git. O servidor de teste PostgreSQL e o Java local foram encerrados após a validação. Configuração, cotas e instruções em [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md). Nenhum recurso pago foi criado.

## Limites
Backups são protegidos por senha exclusiva, com limite de 64 MiB; os iniciadores usam até 512 MiB de memória. Sem a senha, não é possível abrir a cópia. Restaure apenas arquivos de origem confiável, em destino separado.

Nenhuma tarefa agendada local ou exclusão automática de backups foi criada. A cópia fora do disco do banco e a guarda da senha continuam sendo responsabilidade do operador. O Neon Free oferece histórico curto de recuperação e uma snapshot manual; não foram configuradas snapshots agendadas nem tarefa de exportação recorrente. Não houve contratação paga.

Não houve migração dos dados H2 temporários do Render. Não há envio real de e-mail, teste em celular físico ou restauração da infraestrutura inteira. Configurações externas e credenciais do provedor precisam de preservação separada. Consulte [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md).
