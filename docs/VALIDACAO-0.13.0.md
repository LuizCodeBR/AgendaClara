# Validação 0.13.0 — resumo diário

14–15/11/2026. Projeto em D:/Projetos/AgendaClara. [Guia](RESUMO-DIARIO.md).

## Resultado final

Commit de código/tag v0.13.0: f0cc4b9fe3d66bef735320bbfd02c926afbffda1. Implementação inicial f1712c9951e12aec99330b66d384436df443071a.

- Windows/Java 26/H2: 336 casos, 333 aprovados, três condicionais desativados, zero falhas/erros. Maven verify e pacote aprovados.
- Interface: 123 testes aprovados, incluindo 15 novos. TypeScript/Vite compilados.
- [CI 43669967266](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43669967266): seis grupos aprovados. Java 21/26 com H2/PostgreSQL 17, Docker e navegador.
- Logs conferidos: Java 21/H2 com 336 casos, 334 aprovados e dois condicionais; Java 21/26/PostgreSQL com 336 casos, 335 aprovados e um condicional. Os 19 novos casos passaram no PostgreSQL.
- Navegador no CI: 30 cenários aprovados, desktop e Pixel 7 emulado, incluindo dois fluxos novos reais sem interceptar a API.
- Dez rotinas HTTP aprovadas, seguidas de backup/restauração com provas de armazenamento, sessão privada, regras, histórico e contato/resumo.
- [Publicação 43670150747](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43670150747): sucesso. Pacote verificado antes de distribuir.

## Escopo dos novos testes

DailySummaryTest (18 casos) e DailySummaryMigrationTest (1): somente BOOKED pelo início na data/fuso da empresa, ordenação, exclusão de cancelados/concluídos/faltas, dia vazio, remarcação, profissional inativo, limites de data/200 itens, autorização/CSRF/empresa, telefone malformado, versões antigas/fracionárias, duas edições simultâneas e snapshot consistente durante cancelamento/alteração de contato.

Quinze casos de interface: formatação, tamanho/divisão sem perdas, link oficial codificado, entrada Unicode malformada, prévia, conferência do destinatário, contato/versão, rascunho/conflito, respostas antigas, expiração, cópia, falha da área de transferência, falta de conexão e sessão expirada.

Os novos fluxos de navegador verificam o endereço/texto do link sem segui-lo. Não há teste de envio, entrega ou leitura no WhatsApp; nenhuma mensagem foi enviada e nenhum aparelho físico foi utilizado.

## Ajuste encontrado no CI

A execução inicial 43669621549 encontrou acúmulo de pools dos contextos Spring em cache, atingindo o limite de clientes do PostgreSQL de integração ao iniciar SecurityReviewTest. O cache da suíte foi limitado a oito pelo Maven Surefire; a expulsão fecha contextos/pools antigos. O limite de conexões do aplicativo e os testes de concorrência foram preservados. Outro grupo teve falha transitória ao baixar Maven antes dos testes. Nova execução completa passou, conforme resultado acima.

Referência: [cache de contextos Spring](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html).

## Migração ensaiada

Branch temporária br-square-pine-bc4gto32 do projeto Neon Free existente, compute fixo 0,25 CU. V10 → V11 preservou 38 empresas, 34 profissionais, 50 reservas e 15 eventos de histórico. Os 34 contatos começaram vazios. Depois da comparação, a rotina HTTP criou apenas dados fictícios isolados. A branch recebeu expiração e sua remoção automática foi confirmada pela listagem vazia.

No local, uma cópia separada de banco/chave da 0.12.1 foi atualizada pelo JAR 0.13.0: preservação integral dos dois exemplos fictícios e seus históricos, além de reserva/sessão privada capturados antes da migração. Originais preservados.

O iniciar-demo.cmd oficial foi executado em outra cópia V10 da 0.12.1 e aplicou V11. A comparação dos exemplos/históricos passou novamente. Dois fluxos reais adicionais no pacote oficial (desktop/mobile) passaram, incluindo contato e prévia. Captura mobile inspecionada, sem transbordamento horizontal.

## Downloads oficiais

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.13.0-demo.zip | 29152096 | 51617f30e1897aebc6134cee6fb3b74273493a905fc8cb154af7d4fdb7b6a549 |
| AgendaClara-0.13.0-fonte.zip | 615904 | 777e8e3c804c66fd6526546e023f227fe819aeeb516ba5bc621b77914cffd3bd |

Ambos baixados em D:/Projetos/Entregas/AgendaClara-0.13.0-GitHub, conferidos com os hashes da publicação e SHA256SUMS.txt. Demonstração extraída em AgendaClara-demo. Ferramentas Java/Node existentes reutilizadas, sem nova instalação ou extensão.

## Backups e restauração

Todos manuais, em pastas privadas do D:. Formato ACBK0001; ferramenta aceita esquemas 6 a 11.

1. **PostgreSQL antes da publicação:** AgendaClara-20261115T025216Z-a8621e92-643a-480b-a63d-4e14e7595924.acbackup, 59863 bytes, SHA-256 5055fd98aa0897181c2e1e10aa544b30d15cd88c908255ee2edd415606493bb3; verificado em 2026-11-15T02:52:34.932715100Z.
2. **H2/JAR local:** AgendaClara-20261115T025506Z-8afabd42-2d59-4ee4-b92a-f1cfb1f11783.acbackup, 58305 bytes, SHA-256 24199e7af7474e30867d01539fd40afcff27ddf0c0689edbc4b56405d5dbe014; verificado em 2026-11-15T02:55:06.844077400Z. Restaurado em destino novo; contato, versão, resumo e exemplos anteriores conferidos por HTTP.
3. **H2/pacote oficial:** AgendaClara-20261115T030039Z-e657f2a4-aee9-4e86-9990-d46e5541a45c.acbackup, 58817 bytes, SHA-256 429ee56c197b1c3acf2ede6d50abb5382b3c90404e1705c2860d19c6ecd94a44; verificado em 2026-11-15T03:00:40.529823400Z. Criado/verificado por operacao.cmd oficial, restaurado por backup.cmd em daily-official-restored/data e iniciado por iniciar-demo.cmd. Contato, versão, lista do resumo e históricos anteriores preservados.
4. **PostgreSQL atualizado:** AgendaClara-20261115T030350Z-1a600b4e-891b-46f5-bf6c-6991f7a28f43.acbackup, 60846 bytes, SHA-256 b2dfbef78c113ab16537044f84e460f6d22abaf98a60008344ecf945b90b98d2; verificado em 2026-11-15T03:04:05.946752600Z, já com V11 e contatos fictícios dos testes.

Provas privadas: .cache/neon-private. Relatórios locais: .cache/daily-local-operations e .cache/daily-official-operations. Relatórios PostgreSQL: .cache/neon-operations. Cotas não informadas continuam sinalizadas; não se presume consumo zero. Nenhum agendamento de backup/monitoramento foi criado. Servidores locais de teste encerrados normalmente.

## Site publicado e preservação online

Render dep-dbsi016i0phs73d6q940: live em 2026-11-15T03:01:28.168067Z (00:01 de 15/11 em São Paulo), commit f0cc4b9. Serviço web Free existente, uma implantação. Atualizado somente DEMO_DOWNLOAD_URL por mesclagem, preservando credenciais/configuração existentes.

Antes e depois da migração, antes dos novos testes: 38 empresas, 35 profissionais, 51 reservas, 17 eventos de histórico; V10 → V11 e zero contatos preenchidos. Comparação HTTPS preservou os dois exemplos fictícios/históricos e a sessão privada preparada na versão anterior.

Consulta pública confirmou versão 0.13.0, storageMode persistent e download 0.13.0. Rotina HTTP do resumo aprovada por HTTPS. Dois fluxos adicionais reais no site, desktop e Pixel 7 emulado, passaram em 20,7 segundos; nenhum link externo foi seguido.

Após os testes: 38 empresas, 38 profissionais, 54 reservas, 25 eventos e três contatos preenchidos. O acréscimo corresponde aos três profissionais/reservas fictícios isolados da rotina e dos dois fluxos, todos cancelados/inativados na limpeza, mais o cancelamento da prova antiga. Nenhum contato foi inventado para profissionais anteriores.

Consulta ERROR do Render entre 02:59:17Z e 03:03:47Z: nenhum registro retornado; observação limitada à janela consultada.

## Limites da entrega

Compartilhamento manual, sem API paga, chave de WhatsApp, tarefa diária ou alegação de entrega. O resumo é uma cópia consultada, não acompanha alterações posteriores e exige nova conferência/atualização na tela. A abertura transfere número/texto ao WhatsApp conforme descrito no guia. A cópia local usa banco independente do site.

Sem novo recurso pago, plano alterado, dependência ou extensão. Projeto, caches, provas, backups e entregas permanecem no D:.
