# Revisão independente das regras — 0.11.1

14/11/2026. Revisão realizada nesta conversa por Astra, com nova leitura do código e testes de reprodução. Não é uma auditoria externa nem certificação. Código, provas e pacotes permanecem no D:.

## Achados e correções

1. **Prazos podiam vencer durante a validação da disponibilidade.** A checagem inicial ocorria depois do bloqueio do profissional, mas antes de outras consultas. Um pedido no limite podia ser gravado após o prazo de antecedência. No portal do cliente, a consulta da empresa e a chamada ao serviço de agenda também ocorriam depois da conferência do prazo de cancelar/remarcar. Foram reproduzidas quatro situações com avanço controlado do relógio dentro dessas consultas: reserva nova, prazo original de remarcação, antecedência do novo horário e cancelamento. Todos os quatro testes falharam na 0.11.0.

   A decisão final agora acontece depois dessas consultas, imediatamente antes de chamar a gravação. O instante aceito é usado também em updated_at/created_at. A remarcação confirma que o início original e o novo início continuam futuros. O fluxo do cliente recebe a conferência final das suas condições; o responsável conserva a exceção explícita. Uma requisição que aguardou o profissional volta a conferir o prazo. Repetições de operações já concluídas continuam idempotentes.

   A regra usa o instante de decisão do servidor antes de enviar a escrita ao banco; não promete que a resposta ou o commit terminem antes do prazo. Transação e bloqueios mantêm as condições e a disponibilidade consistentes durante a operação.

2. **Frações negativas eram convertidas em zero.** O leitor JSON aceitava -0.5 em campos Integer, truncava para zero e só depois aplicava a validação. Os três prazos e a revisão aceitavam esse valor. Quatro testes HTTP reproduziram resposta 200 quando o correto era 400.

   Desativado ACCEPT_FLOAT_AS_INT na configuração Jackson do aplicativo. Números JSON de ponto flutuante destinados a campos inteiros são recusados antes da validação. Isso inclui minutos, versões, duração e centavos: clientes próprios devem enviar inteiros como 15 e 1, não 15.5 ou 1.0. A interface já enviava inteiros e mantém seu fluxo. O teste HTTP público confere também versão fracionária na reserva e nas alterações do cliente.

3. **Proteção preventiva da transação.** O método que bloqueia as regras dependia de o chamador já estar em transação. Acrescentada uma verificação explícita de transação de escrita ativa, com testes para chamada sem transação e em transação somente de leitura. Não foi encontrado endpoint que explorasse essa ausência na versão anterior; trata-se de proteção contra uso incorreto futuro.

## Escopo conferido

Isolamento por empresa derivada da sessão, CSRF, autorização do cliente por reserva, condições originais imutáveis, revisões concorrentes, repetição segura, publicação reconferida, bloqueios de profissional e empresa, expiração durante a espera, limites temporais e permissão administrativa. A ordem existente de bloqueios foi preservada. Não se acrescentou bloqueio da empresa às alterações do cliente, que trabalham com as condições da própria reserva.

Não se encontraram novos caminhos de acesso a outra empresa nos fluxos examinados. Essa conclusão está limitada ao código e cenários revisados. Os testes usam relógio controlado para tornar os casos de vencimento reproduzíveis, sem depender de temporizações aleatórias.

## Testes

BookingPolicyReviewTest acrescenta 12 casos: quatro vencimentos durante consultas, quatro campos fracionários, duas condições de transação e dois casos concorrentes de cancelamento/remarcação aguardando o profissional. O teste HTTP booking-policy-smoke inclui quatro requisições malformadas adicionais. Os oito testes de reprodução falharam antes das correções e passaram depois.

Os resultados de integração e distribuição estão registrados abaixo.

## Compatibilidade e operação

Não há migração nova: esquema V9 e ACBK0001 permanecem. Regras, reservas, contas, chaves e links existentes são preservados. Código no D:/Projetos/AgendaClara. Nenhuma dependência ou extensão nova. Render e Neon mantêm os planos gratuitos; backups e limites continuam sob demanda. Web e PWA compartilham o servidor online; a cópia local tem banco próprio.

Referência da configuração: [Spring Boot — propriedades JSON](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.json).

Validação local concluída: Windows/Java 26/H2, 280 casos, 277 aprovados, três condicionais desativados, zero falhas. Interface: 93 testes aprovados; TypeScript/Vite compilados. Testes HTTP de regras e portal do cliente aprovados no JAR 0.11.1.

## Resultados da integração

Commit 7ef85d19f67269ff38c3fc095a9c5b32345d03b3, tag v0.11.1. [CI 43629549138](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43629549138): seis grupos aprovados, com Java 21/26, H2/PostgreSQL 17, Docker e interface desktop/mobile. O grupo de interface também executa as oito rotinas HTTP e restauração com provas de regras, acesso privado e armazenamento.

Backup anterior à publicação: AgendaClara-20261114T190110Z-67d83412-f33e-4a66-8c4e-7d6afea1a19e.acbackup, PostgreSQL, 56169 bytes; SHA-256 5b25812dd8c76289ceb411e636fbce32ce94abf418ddfe803d859e6ca76a0ec1; verificado em 2026-11-14T19:01:27.725643700Z. Operação sob demanda. Cotas ausentes continuam sinalizadas para conferência nos painéis; não foi presumido consumo zero.

## Distribuição e teste local

[Publicação dos downloads 43629801523](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43629801523): concluída, com oito rotinas HTTP e restauração das provas de armazenamento, portal e regras.

| Pacote oficial | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.11.1-demo.zip | 29122305 | 519f82954abc07e0cc7ff71f16a35f2abe2e2c945b79e32ade9e24caff1bbea2 |
| AgendaClara-0.11.1-fonte.zip | 540957 | ea4ebe4d84dd4cd52601ec965e626cc0d66fe7c3b1b98fd61e332ff2efc3df01 |

Ambos baixados e conferidos contra SHA256SUMS.txt em D:/Projetos/Entregas/AgendaClara-0.11.1-GitHub. O iniciar-demo.cmd oficial foi executado em Java 26 sobre dados preparados com o pacote oficial 0.11.0. Preservou configuração atual, condições originais da reserva, sessão privada e mesmo link. O esquema continuou V9, sem migração. Fluxos HTTP de regras e portal aprovados no pacote, incluindo recusa de frações em minutos e versões. Dois cenários de navegador passaram no pacote: desktop e Pixel 7 emulado. Não foi usado aparelho físico nesta revisão.

Totais da integração Linux: 280 casos em cada execução; H2 com 278 aprovados e dois condicionais desativados; PostgreSQL com 279 aprovados e um condicional desativado. Os 12 casos novos passaram, inclusive no PostgreSQL. Os 26 cenários completos de navegador passaram no CI. Testes negativos que provocam violações de chave estrangeira/CHECK no banco são esperados e tiveram suas respostas verificadas.
## Site publicado

Render dep-dbsb0muk1f9s738pk2e0: live em 2026-11-14T19:05:04.393111Z, commit 7ef85d19f67269ff38c3fc095a9c5b32345d03b3. Conferência direta por HTTPS retornou versão 0.11.1, armazenamento persistent e download 0.11.1. Mantido o serviço web Free existente, sem criar banco, disco ou outro serviço.

A prova preparada na 0.11.0 confirmou preservação das regras atuais, condições originais, sessão privada e mesmo link no site. Testes HTTP de regras e portal do cliente aprovados, incluindo frações recusadas, revisão antiga, isolamento, remarcação, cancelamento, rotação/revogação e repetição segura. Os testes criam apenas dados fictícios e cancelam/ocultam seus registros ao finalizar.

Consulta de logs de nível error entre 2026-11-14T19:03:00Z e 2026-11-14T19:06:46Z: nenhum registro retornado. A observação é limitada a essa janela. Proteções e limites normais do site permaneceram ativos.

Os servidores de teste Windows foram encerrados normalmente com Ctrl+C. Chaves, bancos, cookies e provas continuam em pastas privadas ignoradas pelo Git. A configuração render.yaml passou no esquema oficial, com o download novo e somente serviço Free. Nenhum agendamento foi criado e não há instalação adicional necessária.

Interface HTTPS: dois cenários aprovados na versão 0.11.1, desktop em 21,1 s e Pixel 7 emulado em 15,2 s. Total 38,6 s; evidência privada em .cache/review-live-browser.log.
