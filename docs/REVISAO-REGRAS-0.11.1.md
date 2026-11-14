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

Resultados completos de distribuição e publicação serão registrados após as verificações.

## Compatibilidade e operação

Não há migração nova: esquema V9 e ACBK0001 permanecem. Regras, reservas, contas, chaves e links existentes são preservados. Código no D:/Projetos/AgendaClara. Nenhuma dependência ou extensão nova. Render e Neon mantêm os planos gratuitos; backups e limites continuam sob demanda. Web e PWA compartilham o servidor online; a cópia local tem banco próprio.

Referência da configuração: [Spring Boot — propriedades JSON](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.json).

Validação local concluída: Windows/Java 26/H2, 280 casos, 277 aprovados, três condicionais desativados, zero falhas. Interface: 93 testes aprovados; TypeScript/Vite compilados. Testes HTTP de regras e portal do cliente aprovados no JAR 0.11.1.