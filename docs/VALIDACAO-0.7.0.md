# Validação — Agenda Clara 0.7.0

## Escopo e ambiente
Backups criptografados do banco e da chave, restauração em destino separado, exportação PostgreSQL nativa e configuração explícita de armazenamento. Código, dependências, temporários, pacotes e evidências no D:/Projetos.

Java local 26.0.2 e Node 24.18.0. Nenhuma nova biblioteca ou extensão instalada. O teste PostgreSQL nativo foi executado no GitHub, pois Docker e clientes PostgreSQL não estão instalados neste computador. Datas de infraestrutura abaixo em UTC, em 13/11/2026.

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
| Site HTTPS | versão 0.7.0, mailMode=demo, storageMode=ephemeral e download 0.7.0 confirmados; cinco roteiros aprovados |
| Interface HTTPS | painel, filtro, aviso simulado e logout em desktop/Pixel 7 emulado; sem transbordamento ou erros JavaScript |
| Logs do site | zero registros de nível ERROR entre 19:28:30 e 19:31:45 UTC |

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
- Render: dep-dbrm95p5efls73dbvo40, live em 2026-11-13T19:29:57.847467Z, serviço srv-dbqf9mon74is73c13k6g no plano free preservado.
- Somente DEMO_DOWNLOAD_URL e STORAGE_MODE foram atualizados por mesclagem. Nenhum banco PostgreSQL estava criado no espaço conectado após a publicação.
- Os servidores Java locais de validação foram encerrados.

## Downloads e evidências
Pasta: D:/Projetos/Entregas/AgendaClara-0.7.0-GitHub.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.7.0-demo.zip | 29029173 | 53bd17626835c52257a032f99024ed2f030bcfd4242e883fc74801b5d6c77dd8 |
| AgendaClara-0.7.0-fonte.zip | 376223 | 456f0c6df54b41c31850a0a34e27c2d297760724fa2cc5c35a9def255797c0c0 |

O pacote foi extraído em teste-pacote/AgendaClara-demo, iniciado na porta 8086 e restaurado em teste-restaurado/data. Os arquivos oficiais continuam intactos. Evidências sanitizadas, imagens e relatórios ficam em evidencias; não incluem chaves, senhas, cookies, fixtures de acesso ou bancos.

O ZIP fonte conserva o relatório disponível no commit da entrega. Este relatório final foi acrescentado ao ramo principal depois da publicação, sem alterar tag ou arquivos distribuídos.

## Pendência para armazenamento online durável
O serviço gratuito atual continua efêmero. A versão 0.7.0 prepara a operação e os backups, mas não ativa um banco pago automaticamente.

O [plano concreto](PLANO-PERSISTENCIA-RENDER.md) propõe PostgreSQL 17, 0.1c-256mb, 1 GB, Virginia, no mesmo espaço, mantendo o web service free. Base estimada consultada em 13/11/2026: US$ 6,30/mês antes de tributos/câmbio/consumo adicional. Requer autorização explícita de cobrança.

## Limites
Backups são protegidos por senha exclusiva, com limite de 64 MiB; os iniciadores usam até 512 MiB de memória. Sem a senha, não é possível abrir a cópia. Restaure apenas arquivos de origem confiável, em destino separado.

Nenhuma tarefa agendada local ou exclusão automática de backups foi criada. A cópia fora do disco do banco e a guarda da senha continuam sendo responsabilidade do operador. O PostgreSQL gerenciado pago pode oferecer recuperação automática conforme seu plano; essa contratação ainda não foi feita.

Não há migração automática H2→PostgreSQL, envio real de e-mail, teste em celular físico ou restauração da infraestrutura inteira. Configurações externas e credenciais do provedor precisam de preservação separada. Consulte [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md).
