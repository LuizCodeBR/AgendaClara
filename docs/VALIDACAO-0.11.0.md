# Validação 0.11.0 — Regras de agendamento

Data: 14/11/2026, horário de São Paulo. Código, temporários, provas privadas, backups e pacotes em D:/Projetos.

## Entrega e testes

Implementação: 9ba462b4904fc72b65f89bd514a1f3faad44fd8e. Tag v0.11.0: 5faa85675677dfdef55854baa4c2db63ef0cfda8. Os dois commits intermediários de verificação ajustam esperas e orçamento de requisições somente nos testes; não relaxam limites de produção.

| Verificação | Resultado |
| --- | --- |
| Windows / Java 26 / H2 | 268 casos: 265 aprovados, três condicionais desativados, zero falhas |
| Linux / Java 21 e 26 / H2 | 268 casos por execução: 266 aprovados, dois condicionais desativados |
| Linux / Java 21 e 26 / PostgreSQL 17 | 268 casos por execução: 267 aprovados, um condicional desativado |
| Interface | 93 testes aprovados |
| TypeScript / Vite | Compilação aprovada |
| Navegador local | 26 cenários aprovados: 13 desktop e 13 Pixel 7 emulado |
| HTTP local | Oito rotinas aprovadas |
| Ensaio completo de CI local | Oito rotinas HTTP seguidas dos 26 cenários de navegador, aprovados |
| HTTP / PostgreSQL Neon de ensaio | Fluxo de regras aprovado |
| Docker / PostgreSQL | Construção, execução e recriação aprovadas no GitHub |
| Pacote oficial Windows | Atualização 8 → 9, regras e restauração aprovadas |

No Windows ficam desativados os casos condicionais de PostgreSQL por Docker, recurso especial Linux e privilégio de link de diretório. No Linux/H2 são desativados o caso PostgreSQL por Docker e o link específico Windows. No Linux/PostgreSQL fica desativado somente o caso específico Windows. O backup nativo PostgreSQL foi executado e aprovado nos dois jobs PostgreSQL.

CI: [43613016432](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43613016432), seis jobs aprovados.
Distribuição: [43613462909](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43613462909), aprovada, incluindo backup e restauração com as três provas HTTP.

As primeiras execuções detectaram uma espera curta para o processamento assíncrono de e-mail e o estouro do limite global de alterações pela soma dos testes HTTP com os de navegador. O teste aguarda o estado concluído e confere explicitamente a resposta de criação de serviço. Somente os servidores de integração usam API_REQUESTS_PER_MINUTE=5000 e WRITE_REQUESTS_PER_MINUTE=1000; os padrões do aplicativo e os testes próprios de proteção permanecem intactos.

## Segurança, tempo e concorrência

Há 27 casos novos Java: 17 de regras/isolamento, cinco limites parametrizados, quatro disputas e uma migração. Eles cobrem zero, instante exato, nanossegundo seguinte, valores até sete dias, fuso e horário de verão; validação HTTP e CHECK do banco; CSRF, sessão e empresa de origem; revisão antiga; mudança de regra durante a confirmação; passagem do prazo enquanto se espera pelo profissional; repetição de confirmação e remarcação; regras independentes; preservação das condições; consulta própria do responsável; e publicação reconferida após o bloqueio.

A política é capturada na mesma transação da confirmação. O bloqueio da empresa usa UPDATE sem alterar chave antes do bloqueio de profissional, evitando incompatibilidade com bloqueios de chave estrangeira no PostgreSQL. O servidor revalida prazo e disponibilidade após esperar. Alterações concorrentes de configuração exigem a revisão vigente.

A consulta de disponibilidade do responsável usa a própria reserva, duração original e exclusão do horário que ela já ocupa. Não herda a antecedência pública atual, permitindo as exceções anunciadas pela interface sem abrir permissões para outras empresas.

A interface cobre rascunho, conflito, descarte explícito, proteção de navegação, regras antes da confirmação, atualização de catálogo sem perder contato, prazos separados e apresentação das condições originais. Navegação de computador e Pixel 7 emulado verifica a remarcação administrativa mesmo após configurar sete dias de antecedência pública. Não foi feito teste em aparelho físico nesta etapa.

## Migração

Ensaio em branch Neon Free temporária com compute fixo 0,25 CU: esquema 8 → 9 preservou 26 empresas, 26 contas, 33 reservas, 34 mensagens, 14 vínculos privados e duas sessões. As regras e condições começaram em zero. O fluxo de regras passou no banco de ensaio. A branch expirou e deixou de constar entre as branches ativas.

No banco principal, as contagens imediatamente antes/depois da migração foram iguais: 26 empresas, 26 contas, 34 reservas, 34 mensagens, 15 vínculos privados e três sessões. Nenhuma empresa ou reserva anterior recebeu regra diferente de zero. Contagens registradas antes dos testes posteriores com novos dados fictícios.

## Pacotes e restauração

Os dois ZIPs oficiais foram baixados no D: e conferidos com SHA256SUMS.txt.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.11.0-demo.zip | 29122466 | 26b8f967729afb46b422a7fa10721cf42691504c4c31bae76cf0ca6fbac0edcd |
| AgendaClara-0.11.0-fonte.zip | 530224 | cfff2f8be7ad2bb60f11bbfd5eb7c4550558d3b6dd39af6eb268a9c39c35ec4d |

Pasta: D:/Projetos/Entregas/AgendaClara-0.11.0-GitHub.
[Entrega oficial](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.11.0).

O iniciar-demo.cmd oficial foi executado no Windows/Java 26 sobre uma cópia isolada dos dados 0.10.0. Aplicou V9 e preservou conta, senha, empresa e mesmo registro histórico de e-mail de prova anterior. O mesmo link de reserva 0.10 abriu após a atualização e manteve condições zero; sua sessão de uma hora já havia expirado e foi renovada explicitamente com o link, sem alegar persistência de sessão expirada.

Foi criada uma empresa fictícia com configuração atual em revisão 2 e reserva com condições da revisão 1. Depois do encerramento normal com Ctrl+C, operacao.cmd criou e verificou backup, e backup.cmd restaurou em uma pasta nova. O pacote restaurado manteve regras atuais, condições individuais, sessão privada ainda válida, mesmo link e conta/histórico antigos.

Backup Windows: AgendaClara-20261114T160646Z-962b4afa-1f64-47f2-8246-b125121d086d.acbackup. SHA-256: 16bce5b4d9229b259e612eac4c21b60427315ddf7b888258b16a99bf80ca4d59. Verificado em 2026-11-14T16:06:47.167833Z.

Backup online anterior à migração: AgendaClara-20261114T155118Z-3a7f454f-e2ba-493c-9058-49aa6188836c.acbackup, 43851 bytes. SHA-256: 86d93c0780cb3bd253482c7831eb246bfc0f2ee9e16a178d79bc9da8d3bdd14a. Verificado em 2026-11-14T15:51:34.8315Z.

O formato ACBK0001 permanece. A ferramenta 0.11 aceita esquemas 6, 7, 8 e 9. Os testes de backup H2 e PostgreSQL verificam valores diferentes para a empresa e para a reserva, evitando que a restauração substitua as condições individuais pelas regras atuais.

## Operação e privacidade

O esquema oficial do Render foi consultado e render.yaml passou na validação. Define somente um serviço web Free, sem banco ou disco pago, com credenciais privadas fornecidas fora do arquivo. Nenhuma dependência, programa, extensão, recurso pago ou rotina recorrente foi adicionado. Backups e limites permanecem sob demanda. O relatório sinaliza consumo não informado para conferência nos painéis; não se presume saldo gratuito suficiente quando esse dado não está disponível.

Provas, cookies, senhas, chaves, tokens, bancos, cópias e capturas ficam em diretórios privados ignorados no D:. O repositório e os ZIPs não incluem esses arquivos. PWA e site compartilham o servidor online; o banco local é independente. Recarregue a página/PWA após atualizar.
## Publicação online

A primeira implantação dep-dbs8ddrm8hqs73c217ng foi marcada live pelo provedor, porém a conferência direta de /api/public/config ainda retornava 0.10.0 e o download anterior. Os logs mostraram o encerramento da instância 0.11 e a inicialização da 0.10 durante a troca. Não foi considerada publicação válida com base apenas no estado da plataforma. Uma nova implantação do mesmo commit, com o serviço ativo, corrigiu a divergência sem alterar recursos ou limites.

Implantação validada: dep-dbs8garm8hqs73c27q70, live em 2026-11-14T16:13:43.867527Z, commit 5faa85675677dfdef55854baa4c2db63ef0cfda8. HTTPS respondeu 0.11.0, storageMode persistent, mailMode demo e download 0.11.0.

A reserva fictícia preparada na 0.10 manteve a sessão privada ainda válida e o mesmo link. A conta, senha, empresa e registro histórico de e-mail anteriores permaneceram acessíveis após autenticação. A prova foi feita antes dos testes posteriores e não expôs credenciais.

A sequência de testes online atingiu HTTP 429 na criação de novas contas após os fluxos anteriores. A proteção de cadastro foi mantida; cenários novos foram separados e repetidos após a janela de limite, sem ampliar limites de produção.
Os oito fluxos HTTP foram aprovados por HTTPS. O fluxo de regras foi repetido isoladamente após a limitação de cadastro, com sucesso. Os dois cenários de regras em navegador passaram no site: desktop em 31,0 s e Pixel 7 emulado em 27,4 s. Registros privados: .cache/policy-live-smoke.log, .cache/policy-live-policy-recheck.log e .cache/policy-live-browser.log.

Logs do Render consultados entre 2026-11-14T16:11:00Z e 2026-11-14T16:18:11Z: nenhum registro de nível error retornado. Essa é uma janela observada, sem garantia permanente.

Backup online posterior: AgendaClara-20261114T161842Z-72550a9a-c4f1-4393-a1e7-fecb5c422118.acbackup, PostgreSQL, 55465 bytes. SHA-256: ff4ae9ae6c359bc7cd2b80ded24e3ca5755afb46c665c808c2ef3335310eb9a0. Verificado em 2026-11-14T16:18:58.733156200Z. Não substitui o backup anterior e não foi programada nova execução.

Os servidores locais de teste foram encerrados normalmente. A cópia original dos dados usados no ensaio de atualização foi preservada; restaurações ocorreram somente em pastas separadas.