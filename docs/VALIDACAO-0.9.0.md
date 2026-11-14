# Validação 0.9.0 — serviços por profissional

Concluída em 14/11/2026. Código testado e publicado: 0c2ee4ce3091d873e5846861b5aef6d4b3697aad, tag v0.9.0. Alterações posteriores atualizam somente documentação e a referência do download; os pacotes mantêm o código validado.

## Resultado
Serviços atendidos são configurados com perfil e expediente, validados na disponibilidade, reserva e remarcação e apresentados no filtro público. A migração V7 preserva as combinações anteriores. Não houve mudança de preço/duração por profissional, de autenticação ou dos serviços de hospedagem.

## Testes executados
| Verificação | Resultado |
| --- | --- |
| Java 26, Windows | 215 casos: 212 aprovados, 3 condicionais não executados, nenhuma falha |
| CI Java 21 e 26, PostgreSQL | 215 casos por matriz: 214 aprovados, 1 exclusivo do Windows não executado |
| CI Java 21 e 26, H2 | 215 casos por matriz: 213 aprovados, 2 condicionais não executados |
| Interface | 69 testes aprovados, TypeScript e compilação aprovados |
| Navegador local, desktop e Pixel 7 emulado | 20 cenários aprovados no JAR final |
| CI no navegador | 20 cenários aprovados |
| Docker e PostgreSQL | Demonstração e verificações HTTP aprovadas |
| Publicação | Compilação, seis verificações HTTP, backup, relatório e restauração aprovados |

Foram acrescentados 17 casos Java, seis testes da interface e um fluxo de navegador executado nos dois tamanhos. Os testes existentes de backup H2 e PostgreSQL passaram a conferir também a preservação dos vínculos.

As condições locais não executadas são backup PostgreSQL via Docker, link simbólico que exige privilégio no Windows e FIFO exclusivo do Linux. As matrizes Linux executaram os casos aplicáveis, e a junção Windows continuou coberta localmente. Não somar matrizes e repetições como se fossem casos diferentes.

CI: [seis verificações aprovadas](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43549925093).
Publicação: [workflow aprovado](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43550211429).

## Regras, autorização e concorrência
Os testes cobrem IDs ausentes, desconhecidos, duplicados, nulos e de outro estabelecimento; limite do corpo; constraints compostas; lista vazia; profissional novo sem vínculo; mesmo profissional com serviço e expediente para publicação; histórico cancelado/encerrado; proteção de atendimento futuro/em andamento; versão antiga e repetição segura da reserva.

As disputas entre retirada de serviço e reserva são exercitadas nas duas ordens, com transações e bloqueio real: retirada primeiro impede a reserva; reserva primeiro impede a retirada. Perfil, vínculos e semana não são parcialmente salvos.

Na interface foram conferidos filtro da equipe, limpeza do horário incompatível, preservação de nome/telefone após conflito, estado sem profissional, rascunho após recusa e carregamento do catálogo. Uma inconsistência visual temporária após salvar foi identificada na inspeção e corrigida antes da entrega final.

## Ensaio da migração online
Uma cópia do banco foi criada no mesmo projeto Neon Free, com compute fixo de 0,25 CU, e migrou de 6 para 7. As contagens antes/depois foram idênticas: 17 empresas, 17 contas, 10 profissionais, 9 serviços, 15 reservas e 21 mensagens. Foram criados os 24 vínculos esperados.

A conta antiga e seu histórico foram conferidos por acesso autenticado. O teste HTTP dos vínculos passou, assim como os dois cenários de navegador com o banco Neon (desktop e Pixel 7 emulado). O servidor de teste foi encerrado. A branch temporária br-bitter-boat-bcmqn76g expirou e deixou de constar na lista de branches ativas.

## Migração do site
Antes da implantação foi criada uma conta fictícia de prova e um backup manual autenticado. Isso explica a passagem de 17 para 18 empresas e de 21 para 22 mensagens entre o ensaio e a implantação.

A implantação Render dep-dbrt3vbrjlhs73cgm3e0 ficou live em 2026-11-14T03:16:16.602119Z. A conferência imediatamente antes/depois da migração preservou 18 empresas, 18 contas, 10 profissionais, 9 serviços, 15 reservas e 22 mensagens, criando os 24 vínculos esperados. Essas contagens antecedem os testes HTTP posteriores, que criam outros dados fictícios.

A API por HTTPS confirmou 0.9.0, armazenamento persistent, e-mails demo e download v0.9.0. Seis verificações HTTP passaram. Empresa, conta, senha e mesma mensagem privada criadas antes da implantação permaneceram acessíveis e descriptografáveis depois dela.

A tela Serviços atendidos foi conferida por HTTPS em desktop e Pixel 7 emulado, com catálogo, saída de sessão, ausência de erros JavaScript e de transbordamento. Não houve teste em aparelho físico nem instalação em loja.

Consulta aos logs de erro do Render entre a implantação e 2026-11-14T03:21:15Z: nenhum registro retornado. É uma janela observada, não garantia permanente de ausência de erros.

## Pacote oficial no Windows
O pacote oficial foi baixado no D: e os dois ZIPs conferidos com SHA256SUMS.txt. Uma cópia privada dos dados de uma instalação 0.8.1 foi aberta pela 0.9.0. Empresa, senha e histórico de e-mails foram preservados; a origem antiga permaneceu separada.

O teste HTTP dos vínculos passou no pacote. A rotina operacao.cmd criou e verificou um backup do banco já migrado. A restauração em nova pasta recuperou conta, senha e mesma mensagem privada, além de exatamente os mesmos profissionais e serviceIds do catálogo. Os servidores de teste foram encerrados; o servidor de linguagem do editor foi preservado.

| Arquivo | SHA-256 |
| --- | --- |
| AgendaClara-0.9.0-demo.zip | 8364e9fbba1b4805a3f2f1f96f56aea37b402c173090c003274d7a8a4413e982 |
| AgendaClara-0.9.0-fonte.zip | cbf24d7c14de38bfaa0a7ad09de530f31122e3b7ae2326952fc2e5c77bd2396f |

Pacotes e evidências: D:/Projetos/Entregas/AgendaClara-0.9.0-GitHub.
Site: https://agenda-clara-luizcodebr.onrender.com
Entrega: https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.9.0

## Backups e limites
Backup manual anterior à migração: 28.822 bytes, SHA-256 ba77f7dc9793f67814a9f1f5c1adb5847f9f9ba3a1d1191a05b9a733949d3218.
Backup manual posterior à migração: 31.098 bytes, SHA-256 4d9b13a5ac40c1607976a1bbf81ef4c326c0146a4dc3f0e1c4562b1797a0f552. Quatro cópias preservadas na pasta operacional.

Um backup real antigo da 0.7.0 também foi autenticado pela 0.9.0. As inspeções aceitam esquemas 6 e 7 e recusam 8 no teste; ACBK0001 continua inalterado. O relatório real está VERIFIED para backup e ATTENTION no conjunto por consumo mensal desconhecido.

## Ambiente e custos
Mantidos Render Free e Neon Free. Nenhum upgrade, recurso pago ou tarefa recorrente foi ativado. O ambiente de ensaio consumiu cotas do plano Free e expirou após uso. render.yaml continua com um único serviço web Free, sem banco ou disco pago, validado no esquema oficial.

Projeto, pacotes, bancos de teste, cópias e evidências locais no D:. Credenciais, chaves e provas privadas não entraram no Git nem nas evidências compartilháveis. Não foi necessário instalar programa ou extensão.

A PWA usa o banco online; a instalação local mantém banco independente. Recarregue a página/PWA após atualizar. Preserve a pasta data completa ao substituir uma instalação local. O limite do conjunto de backup permanece 64 MiB; cópias no mesmo disco não substituem uma cópia externa protegida.
