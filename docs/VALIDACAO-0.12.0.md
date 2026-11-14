# Validação 0.12.0 — Resultados e histórico

14/11/2026. Arquivos, temporários, provas privadas e pacotes no D:.

Windows / Java 26 / H2: 299 casos, 296 aprovados e três condicionais desativados, zero falhas. Interface: 105 testes aprovados; TypeScript/Vite compilados. Navegador: 28 cenários aprovados, 14 desktop e 14 Pixel 7 emulado.

Há 19 casos novos Java: classificação no limite, recusa antecipada, cancelados, justificativa, versão antiga, repetição/chave reutilizada, falhas com rollback, histórico das alterações, paginação, registros legados, permissões/CSRF/empresa, entradas inválidas, área do cliente, três disputas concorrentes e migração 9 → 10. Backups H2/PostgreSQL incluem resultado e evento histórico na mesma fixture já usada para chave, sessão e regras.

Doze testes novos de interface cobrem confirmação, correção, justificativa, repetição após falha, conflito, rascunho, falta de conexão, paginação, histórico vazio, filtros, contadores e resultado no portal.

Os dois novos testes de navegador usam respostas controladas para representar atendimentos encerrados, sem precisar adulterar o relógio do servidor ou aguardar um atendimento real. Conferem conclusão, correção justificada, histórico e ausência de transbordamento em desktop/mobile. A autorização e a classificação reais são testadas no backend com relógio controlado; a rotina HTTP real confere criação/remarcação/cancelamento com histórico e recusa de resultado antecipado. Nenhum aparelho físico foi utilizado.

Ensaio Neon Free em cópia temporária: esquema 9 → 10 preservou 37 empresas, 37 contas, 47 reservas, 47 mensagens e 28 acessos privados. Histórico iniciou vazio, sem classificação retroativa. O fluxo HTTP real também passou.

Os detalhes da distribuição e publicação estão registrados abaixo.

O fluxo HTTP no PostgreSQL de ensaio passou. O servidor foi encerrado e a branch temporária br-soft-wind-bcwiej1b expirou; a consulta posterior não encontrou branches com esse nome. O compute ficou fixo em 0,25 CU no plano gratuito. A primeira tentativa de definir suspensão personalizada foi recusada pelo plano; foi mantida a configuração padrão.

CI da implementação: 43646797894. API Java 21/26, H2/PostgreSQL, Docker e os 28 testes de navegador passaram. A sequência de login da conta de demonstração excedeu o limite durante a preparação das provas de restauração. O orçamento da conta foi ajustado somente no ambiente de integração; os limites do aplicativo permaneceram iguais. [CI final 43647153645](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43647153645): seis grupos aprovados. Implementação ad8f2d306e7979cf338ec6c2657198ad5266f93c; tag v0.12.0 no commit 6c1a88b04535867a244da04b35ba3d2a6db937f7, com correção de links e configuração do teste.

Backup anterior à migração online: AgendaClara-20261114T221451Z-7105d385-4bbb-4882-a7b0-d9cfa3affbe7.acbackup, PostgreSQL, 55788 bytes, SHA-256 ecb0e6022c60d9466896585df2459609b187a30d78aff688e4299d175fbaa0f1, verificado em 2026-11-14T22:15:07.56357Z. Nenhuma rotina recorrente criada.
## Distribuição e pacote Windows

[Publicação 43647463599](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43647463599): aprovada. Inclui nove rotinas HTTP e restauração das provas de armazenamento, regras, portal e histórico.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.12.0-demo.zip | 29139621 | 81c3677e67b94234e956ad0a03b695d6d876eb28f25020e2ce363c656dca9422 |
| AgendaClara-0.12.0-fonte.zip | 570665 | 29b3750c43859f216c124c1a121d4c2b353675d4463ddf67eba93f9ed5b2c31c |

Ambos baixados e conferidos contra SHA256SUMS.txt no D:/Projetos/Entregas/AgendaClara-0.12.0-GitHub. iniciar-demo.cmd oficial executado sobre dados preparados com a 0.11.1; migrou 9 → 10 e preservou regras atuais, condições da reserva, sessão e mesmo link.

Outra cópia isolada de dados históricos foi atualizada com o mesmo pacote. Dois exemplos fictícios encerrados foram concluídos e corrigidos para falta pela interface real, em desktop e Pixel 7 emulado. O portal privado mostrou o resultado e não ofereceu alteração. As duas provas registraram NO_SHOW, dois eventos e versões 3/2 (a emissão do acesso privado ocupou a versão 1). Nenhuma interceptação de API nesse fluxo adicional.

Após encerramento normal, operacao.cmd criou/verificou o backup e backup.cmd restaurou em pasta nova. O pacote restaurado preservou ambos os resultados, todos os eventos/versões e a prova adicional de reserva, histórico e sessão privada. Cópias originais preservadas; servidores locais de teste encerrados normalmente.

Backup Windows: AgendaClara-20261114T222416Z-c569df54-33d6-402a-8f31-ef8b2d54cf53.acbackup, 70051 bytes, SHA-256 8380af3a595aba400ce822be6d1fd369b1d0a14b6d58e94dcd496a9a7fea2c1c, verificado em 2026-11-14T22:24:16.828521400Z.

## Site e banco principal

Render dep-dbsdtl3bc2fs73ba5s3g, live em 2026-11-14T22:23:23.383897Z, commit 6c1a88b04535867a244da04b35ba3d2a6db937f7. Resposta HTTPS confirmou 0.12.0, persistent e download novo.

Antes da atualização: esquema 9, 38 empresas, 38 contas, 48 reservas, 48 mensagens, 29 acessos privados e 13 sessões. Depois da migração e dos testes: esquema 10, 38 empresas, 38 contas, 49 reservas e 48 mensagens; oito eventos históricos. A reserva adicional veio do novo teste HTTP. As duas faltas foram classificadas explicitamente nos exemplos fictícios originais, após o término; não houve classificação automática dos demais registros.

A prova criada na 0.11.1 confirmou regras, condições originais, sessão e link preservados. O teste HTTP real de histórico, remarcação, cancelamento, permissões e recusa de resultado antecipado passou por HTTPS.

Também foram executados dois fluxos reais de navegador online, desktop e Pixel 7 emulado, usando somente os exemplos fictícios originais João Exemplo e Maria Exemplo da Barbearia Horizonte em 14/11/2026. Foram concluídos e depois corrigidos para Não compareceu com justificativa. A API real, o histórico e o portal privado confirmaram os resultados. As provas privadas e imagens ficam em .cache/outcome-live-real-*; tokens não foram publicados. Esses testes adicionais não usaram respostas interceptadas.

Consulta de logs de erro do Render entre 2026-11-14T22:20:00Z e 2026-11-14T22:27:50Z: nenhum registro retornado. A primeira consulta sofreu indisponibilidade do serviço de logs (503); a consulta posterior funcionou. A conclusão se limita à janela observada.

Backup online posterior: AgendaClara-20261114T222640Z-860b226f-0edb-4d44-9e9c-dfbe475ab48b.acbackup, 58446 bytes, SHA-256 6470a1b88841bd7a1e4335d6c00023082b43be47537072d5608311f1370eee70, verificado em 2026-11-14T22:26:56.153947300Z. Preserva esquema 10, resultados, histórico, banco e chave.

Render continua Free; o banco Neon existente foi reutilizado. render.yaml validado com somente um serviço web Free e entradas privadas, sem banco/disco pago. Nenhuma dependência, extensão, programa global ou rotina recorrente adicionada. Backups e consumo continuam sob demanda; ausência de cotas informadas continua sinalizada no relatório operacional. Site/PWA compartilham dados online; banco local é independente. Recarregue a página após atualizar.
