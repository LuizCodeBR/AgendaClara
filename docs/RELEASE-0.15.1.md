# Agenda Clara 0.15.1

Revisão de segurança e concorrência dos acessos profissionais.

- Revogação invalida caixa demo e links pendentes de recuperação/confirmacão; contas já revogadas na 0.15.0 também são recusadas.
- Reativação renova a caixa junto com a conta; solicitação antiga não consegue reabrir a caixa nova.
- Atendimento de colega recusado antes de bloquear qualquer agenda.
- Convite só aparece após conferir versão/e-mail/validade; históricos atrasados são descartados na troca de contexto, preservando rascunhos abertos.
- Mais testes de concorrência, interface, reativação e revogação da recuperação no navegador e na rotina HTTP.

Mesmo site, PWA e pacote local. Sem nova dependência, migração ou recurso pago. Banco V13 e backup ACBK0001 preservados. Encerre a instalação anterior, faça backup e preserve data/mail-outbox.key. Não execute 0.14.1 ou anterior no banco V13.

Guia: docs/ACESSO-PROFISSIONAIS.md. Revisão: docs/REVISAO-ACESSOS-0.15.1.md. Resultados: docs/VALIDACAO-0.15.1.md.
