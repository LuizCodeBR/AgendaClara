# Validação 0.13.0 — resumo diário

14/11/2026, Windows, projeto em D:/Projetos/AgendaClara. [Guia](RESUMO-DIARIO.md).

## Verificações locais confirmadas

- Backend Java 26/H2: 336 casos, 333 aprovados, três condicionais desativados, zero falhas/erros. São 19 novos casos: DailySummaryTest (18) e DailySummaryMigrationTest (1).
- Interface: 123 testes aprovados, incluindo 15 novos casos de formatação, tamanho, privacidade do link, contato, concorrência de respostas, conflito, expiração, cópia e falha da área de transferência. TypeScript/Vite compilados.
- Navegador: os 28 cenários anteriores passaram; os dois novos cenários reais, desktop e Pixel 7 emulado, passaram após ajustar os rótulos acessíveis dos controles. Nova execução completa será conferida no CI.
- Rotina HTTP daily-summary-smoke passou em H2 local e no PostgreSQL de ensaio: consulta por empresa, contato privado/normalizado/versionado, catálogo público sem contato, remarcação e exclusão dos cancelados. Não abriu o WhatsApp nem enviou mensagem.
- render.yaml validado contra o esquema oficial atualizado; um serviço web Free e somente link do download alterado.

## Migração e preservação

Ensaio isolado em branch temporária do projeto Neon Free existente, compute fixo 0,25 CU, br-square-pine-bc4gto32. V10 → V11 preservou os totais: 38 empresas, 34 profissionais, 50 reservas e 15 eventos de histórico. Os 34 contatos começaram vazios. Depois da comparação, a rotina HTTP criou apenas dados fictícios isolados. Branch configurada com expiração e sua remoção automática foi confirmada pela listagem vazia.

Cópia local separada de banco/chave da 0.12.1: atualização para V11 preservou integralmente os dois exemplos fictícios e seus históricos, além de reserva/sessão privada capturados antes da migração. Originais preservados. Os testes de backup H2/PostgreSQL incluem verificação do contato após restaurar.

## Escopo dos testes

Somente BOOKED na data pelo início no fuso da empresa, ordenação, exclusão de cancelados/concluídos/faltas, dia vazio, remarcação, profissional inativo, limites de data e 200 itens, autorização/CSRF/empresa, telefone malformado, versões antigas/fracionárias, duas alterações simultâneas e leitura consistente durante cancelamento/alteração de contato. O texto não contém telefone do cliente, token, referência, preço ou justificativa interna.

Os cenários novos de navegador usam API e banco reais. Verificam o endereço/texto do link sem segui-lo. Não se fez teste de entrega/leitura no WhatsApp nem se utilizou aparelho físico.

## Operação

Nenhuma nova dependência, extensão, serviço pago ou tarefa recorrente. Código, caches, provas privadas e entregas no D:. Backups e monitoramento continuam sob demanda. Download oficial, restauração do pacote e publicação online serão registrados após confirmação.

## Ajuste do ambiente de integração
A primeira execução CI identificou acúmulo de pools dos contextos Spring em cache, levando o PostgreSQL de testes ao limite de clientes ao iniciar SecurityReviewTest. O cache de contextos da suíte foi limitado a oito pelo Maven Surefire; a expulsão fecha os contextos/pools antigos. O limite de conexões do aplicativo e seus testes de concorrência permanecem os mesmos. Outro grupo teve falha transitória ao baixar Maven antes dos testes. A nova rodada será registrada após confirmação. Referência: [cache de contextos Spring](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html).
