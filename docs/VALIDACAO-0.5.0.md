# Validação 0.5.0 — confirmação e recuperação de acesso

Conferência concluída em 12/11/2026, horário de Brasília (13/11 em UTC). Projeto e entregas mantidos no D:.

## Publicação
- Código/tag v0.5.0: **eeacac797b538772efaa458a9d99b4a11f96f108**.
- [Testes completos](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43347565292): sucesso.
- [Compilação e publicação dos downloads](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43347841526): sucesso.
- [Release oficial](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.5.0).
- [Site e PWA](https://agenda-clara-luizcodebr.onrender.com).
- Render: dep-dbr6qjdckfvc73dctna0, live em 2026-11-13 01:54:25 UTC, commit da entrega confirmado.
- Serviço existente mantido no plano free, com MAIL_MODE=demo e APP_BASE_URL da instalação HTTPS. Nenhuma credencial de provedor ou recurso pago foi configurado.

## Testes aprovados
| Ambiente | Resultado |
| --- | --- |
| Java 21 / H2 | 142 testes, zero falhas |
| Java 21 / PostgreSQL 17 | 142 testes, zero falhas |
| Java 26 / H2 | 142 testes, zero falhas |
| Java 26 / PostgreSQL 17 | 142 testes, zero falhas |
| Vitest / interface | 56 testes, zero falhas |
| TypeScript / Vite | compilação aprovada |
| Playwright | 16 cenários: oito em desktop e oito em Pixel 7 emulado |
| Docker / PostgreSQL | início e quatro roteiros HTTP aprovados |
| ZIP portátil oficial | baixado, extraído no D:, iniciado por iniciar-demo.cmd e verificado com quatro roteiros HTTP |
| Site HTTPS | quatro roteiros HTTP e telas de confirmação/recuperação aprovados |

Roteiros: scripts/smoke.mjs, scripts/date-hours-smoke.mjs, scripts/registration-smoke.mjs e scripts/account-access-smoke.mjs. Executados também antes da distribuição do JAR na release.

## Casos verificados
- Confirmação obrigatória antes da publicação de novas empresas; configuração do atendimento e login continuam possíveis enquanto o e-mail está pendente.
- Link de confirmação explícito e de uso único; recuperação com nova senha, confirmação do endereço no modo correspondente e notificação sem incluir a senha.
- Finalidade incorreta, segredo malformado, validade expirada, reutilização e senhas fora da política são recusados.
- Duas tentativas simultâneas com o mesmo link de recuperação têm apenas um sucesso.
- Duas sessões anteriores perdem acesso na próxima chamada; senha antiga deixa de funcionar e senha nova permite entrar.
- Caixa simulada é privada por cookie, recusa acesso de outro navegador/conta e permite recuperação após logout sem depender da sessão de login.
- Prova de caixa expirada deixa de autorizar novas mensagens; o responsável autenticado pode reabri-la.
- Reenvio respeita intervalo por conta/finalidade e substitui o link anterior.
- Respostas de solicitação são genéricas para e-mail conhecido, inexistente, compartilhado e sem prova da caixa.
- CSRF, limite de corpo e cota de ações de acesso são preservados; rotação de cookie/cabeçalho de IP não supera a cota global.
- O caminho canônico também é usado para recusar sessões revogadas quando a URI contém codificação.
- Transação desfeita não expõe mensagem; pré-visualização tem limite e expiração.
- Confirmação simulada usa um campo separado e não comprova posse de endereço real. Tokens simulados e páginas dependentes dessa confirmação não são aceitos no modo de envio real.
- Configuração de origem rejeita credenciais, caminho, parâmetros, fragmento e HTTP externo. Host de visitante não define o destino público dos links.
- Adaptador Resend tem destino HTTPS fixo, timeout e chave de idempotência estável; a fila foi testada com transporte simulado e três tentativas.
- Interface exige ação explícita, remove o segredo da URL, protege o formulário pendente e limpa a senha depois da troca.
- Telas HTTPS conferidas em desktop/celular emulado, sem transbordamento horizontal nem erro JavaScript observado.
- Não foram encontrados logs de nível ERROR da aplicação hospedada na janela da conferência após 01:54:25 UTC.

## Atualização de banco local real
Uma cópia de um arquivo H2 fictício da versão 0.4.0 foi atualizada de V4 para V5 com o novo JAR:
- três empresas, três contas e seis agendamentos antes e depois;
- login da conta própria preservado;
- zero contas marcadas indevidamente com confirmação real;
- três contas legadas mantidas com sua regra de acesso anterior.

Somente a cópia em .cache/access-upgrade-data foi usada. Evidências em .cache/access-upgrade-before.log, access-upgrade-after.log, access-upgrade-http.log e access-upgrade-server.log.

## Downloads oficiais
| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.5.0-demo.zip | 28.973.498 | 0d2b2cbe12684bed7eaf64bb6f2c7625fcb912c7885a3e9e1181c86a7b693d3b |
| AgendaClara-0.5.0-fonte.zip | 297.654 | 62633c7809f58dc78883980bceb43cec4a3ed5bae5f440ee7945826187a85aaa |

Checksums comparados com SHA256SUMS.txt da release. A versão do pom.xml e a presença da migração V5 foram conferidas no ZIP de fonte.

Entrega: D:\Projetos\Entregas\AgendaClara-0.5.0-GitHub.
Iniciador: Aplicativo local\AgendaClara-demo\iniciar-demo.cmd.

O ZIP de fonte mantém a documentação no commit da entrega. Este relatório final é publicado no ramo principal e copiado para a pasta da entrega, sem alterar os ZIPs oficiais.

## Limites e configuração pendente
O usuário informou que ainda não possui serviço de envio e solicitou preparar a configuração. Por isso, **nenhum e-mail real foi enviado nem teve entrega externa validada**. A demonstração usa uma caixa simulada, explicitamente identificada na interface.

O envio real requer provedor/remetente autorizado, RESEND_API_KEY, MAIL_FROM e APP_BASE_URL. Depois da configuração, deve ser validado com endereços autorizados, incluindo spam e notificação de troca. A fila atual tem memória limitada e não é durável; reinício pode perder mensagens pendentes. Para entrega garantida, é necessária evolução para persistência protegida e monitoramento.

A hospedagem gratuita continua com banco temporário. No pacote local, o banco permanece em data, mas a caixa simulada em memória some no reinício. A versão mobile é PWA e os testes usaram emulação, sem aparelho físico.

Detalhes de operação, credenciais, migração e preparação do envio: [EMAIL-ACESSO.md](EMAIL-ACESSO.md).
