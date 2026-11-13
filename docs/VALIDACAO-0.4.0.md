# Validação 0.4.0 — empresas e contas próprias

Entrega conferida em 12/11/2026, horário de Brasília (13/11 em UTC). Código, dependências, relatórios e pacotes mantidos no D:.

## Versão publicada
- Código/tag: **bf591d3e1ac140d4f47b21c5d0d6eff84a8ff47a**, v0.4.0.
- [Código e documentação](https://github.com/LuizCodeBR/AgendaClara).
- [Site e PWA](https://agenda-clara-luizcodebr.onrender.com).
- [Pacotes oficiais](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.4.0).
- [Verificações completas](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43338485339): concluídas com sucesso.
- [Compilação e publicação dos downloads](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43338887539): concluídas com sucesso.
- Render: dep-dbr5pltbedkc73ck53c0, live desde 2026-11-13 00:44:03 UTC, commit da entrega confirmado; serviço existente mantido no plano free.

## Testes aprovados
| Camada | Resultado |
| --- | --- |
| Java 21 + H2 | 124 testes, zero falhas |
| Java 21 + PostgreSQL 17 | 124 testes, zero falhas |
| Java 26 + H2 | 124 testes, zero falhas |
| Java 26 + PostgreSQL 17 | 124 testes, zero falhas |
| Interface Vitest | 47 testes, zero falhas |
| TypeScript e Vite | compilação aprovada |
| Playwright | 14 cenários: sete em desktop e sete em Pixel 7 emulado |
| Docker/PostgreSQL | compilação, início e três roteiros HTTP aprovados |
| Download portátil oficial | extraído no D:, iniciado com iniciar-demo.cmd; três roteiros HTTP aprovados |
| Site HTTPS | três roteiros HTTP aprovados; formulário conferido em desktop e celular emulado |

Os roteiros HTTP são scripts/smoke.mjs, scripts/date-hours-smoke.mjs e scripts/registration-smoke.mjs. Foram executados também antes da distribuição do JAR no fluxo da release.

## O que foi verificado
- Criação atômica de empresa privada e responsável, e-mail normalizado/único e senha BCrypt, sem conteúdo da demonstração copiado para a empresa própria.
- Reenvio idêntico com a mesma chave, quatro tentativas simultâneas e corrida entre chaves diferentes com o mesmo e-mail; ausência de empresas órfãs.
- Senha curta, limite em bytes/Unicode, campos ausentes/inválidos, domínio reservado, desativação do cadastro e aceite da demonstração temporária.
- Login real com renovação de sessão, CSRF, saída, cookies e limite de solicitações independente de cookies/cabeçalhos de IP.
- Empresa incompleta não pode ser publicada; expediente semanal ou exceção aberta futura pode habilitar a configuração.
- Publicação com versão, link público e reserva; ocultação bloqueia novas solicitações e preserva os atendimentos existentes.
- Duas contas próprias não conseguem ler ou modificar a equipe, agenda ou reservas uma da outra.
- Migração V3 para V4 preserva conta e publicação antigas. A matriz usa a migração completa também no PostgreSQL.
- Conta própria continua acessível depois de encerrar e reiniciar a instalação local.
- Instalação sem exemplos aceita a primeira empresa; habilitar exemplos posteriormente cria as duas empresas fictícias e preserva a conta própria privada.
- Formulário responsivo, aceite obrigatório no site público, proteção de rascunho e ausência de erro JavaScript nos navegadores observados.
- Não foram observados logs de nível ERROR da aplicação hospedada na janela da conferência após a publicação.

A primeira execução de CI identificou que o pom.xml com a versão 0.4.0 não havia sido incluído no primeiro envio. O arquivo foi publicado no commit bf591d3, alinhando o nome do JAR com Docker e iniciadores; a matriz inteira foi repetida e aprovada antes da tag e da implantação.

## Arquivos oficiais conferidos
| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.4.0-demo.zip | 28.935.400 | 0d2f333a79482e802d94b78cad31efea8d22e637c9de29b7a64037fa85a2cabb |
| AgendaClara-0.4.0-fonte.zip | 256.807 | dbf8b23164b6b7266a5ecc5e6ada9ad4f4b76ffcc597fb3d6f56b92cce3e88b5 |

Os valores foram comparados com SHA256SUMS.txt publicado na release. O ZIP de fonte registra a documentação no commit da entrega; este relatório final é atualizado no ramo principal e copiado para a pasta de entrega, sem modificar os arquivos ZIP oficiais.

Pasta de entrega: D:\Projetos\Entregas\AgendaClara-0.4.0-GitHub.
Iniciador conferido: Aplicativo local\AgendaClara-demo\iniciar-demo.cmd.

## Limites
O site gratuito continua sendo uma demonstração temporária. Contas podem desaparecer em reinícios, novas implantações ou suspensão por inatividade. Localmente, o banco fica em data; no perfil postgres, a persistência depende do banco e de seus backups. Verificação de e-mail, recuperação de senha, gestão/exclusão de contas e operação comercial não fazem parte desta entrega.

Os testes mobile usam emulação de Pixel 7 no Chromium; não houve teste em um aparelho físico nem validação de aplicativo nativo de loja. Consulte [CADASTRO-EMPRESAS.md](CADASTRO-EMPRESAS.md) para regras e configuração.

Capturas locais: .cache/registration-live-desktop.png, .cache/registration-live-mobile.png, .cache/registration-desktop-published.png e .cache/registration-mobile-published.png. Relatórios e verificações detalhadas permanecem em .cache/registration-* no projeto.
