# Agenda Clara 0.6.1 — revisão de segurança e concorrência

Correções reproduzidas por testes na fila de e-mails:

- Tentativas manuais respeitam o prazo solicitado pelo provedor, inclusive após esgotar o ciclo automático.
- O monitor recebe verificações durante lotes lentos e evita indicar ausência de atividade indevidamente.
- Inicializações simultâneas coordenam a leitura/criação da chave, sem ler conteúdo parcialmente gravado.
- A interface distingue espera do provedor de uma próxima tentativa automática.

Inclui testes de disputa entre tentativas manuais, 12 inicializações concorrentes em três rodadas e quatro processos Java separados. A instalação 0.6.0 permanece compatível, preservando banco e chave. Não há nova migração nem dependência.

Web, mobile/PWA e pacote local compartilham as correções. O ZIP demo exige Java 21 a 27; extraia em pasta gravável e execute iniciar-demo.cmd ou bash iniciar-demo.sh. Preserve a pasta data completa ao atualizar e encerre instâncias locais antigas antes da troca.

A demonstração não envia e-mails reais. O Render gratuito continua efêmero e não representa persistência comercial. Aceitação pelo provedor não comprova entrega.

Consulte docs/REVISAO-SEGURANCA-0.6.1.md e docs/VALIDACAO-0.6.1.md. O ZIP fonte registra a documentação no commit da entrega; o ramo principal recebe a confirmação final de publicação.
