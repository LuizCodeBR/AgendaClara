# Agenda Clara 0.6.0 — fila persistente e acompanhamento de e-mails

A emissão de um link de acesso e sua mensagem passam a ser gravadas na mesma transação. Pendências podem ser recuperadas após uma interrupção quando banco e chave são preservados.

- Conteúdo protegido com AES-256-GCM e chave vinculada à instalação.
- Processamento concorrente com concessão, recuperação de interrupções e proteção contra conclusão atrasada.
- Até três tentativas por ciclo, tratamento de Retry-After e um ciclo manual elegível.
- Identificador e conteúdo estáveis para deduplicação no provedor, limitados ao prazo de envio.
- Painel E-mails e envios com histórico privado por empresa, filtros, falhas e verificação do processador.
- Caixa de teste persistida até a validade; mesma interface em web, celular/PWA e pacote local.
- Migração V6 preserva contas, credenciais e links anteriores.

**O modo de demonstração não envia e-mails reais.** O adaptador Resend está preparado; remetente e credenciais ainda precisam ser configurados. Aceitação pelo provedor não comprova recebimento. A hospedagem gratuita continua usando armazenamento efêmero.

No pacote local, preserve a pasta data completa, incluindo mail-outbox.key. Sem a chave original, os conteúdos protegidos não podem ser recuperados. O Compose conserva banco e chave em volumes próprios.

O ZIP demo exige apenas Java 21 a 27. Extraia em pasta gravável e execute iniciar-demo.cmd, ou bash iniciar-demo.sh. O ZIP fonte contém os requisitos de compilação.

Consulte FILA-EMAILS.md, EMAIL-ACESSO.md e VALIDACAO-0.6.0.md em docs no repositório. O ZIP fonte registra a documentação no commit da entrega; o ramo principal recebe a confirmação final de publicação.
