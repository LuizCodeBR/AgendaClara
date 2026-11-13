# Agenda Clara 0.5.0 — confirmação e recuperação de acesso

Novas contas confirmam o e-mail antes de publicar a página de reservas. A recuperação troca a senha com link de uso único e revoga as sessões anteriores na próxima chamada à API.

- Confirmação com validade de 24 horas e recuperação de 15 minutos.
- Respostas genéricas, CSRF, limites de uso, assinatura dos tokens e serialização de consumo.
- Caixa de e-mails simulada privada para testar sem provedor, em web, PWA e pacote local.
- Confirmações simuladas separadas das reais; links de teste não autorizam envio real.
- Adaptador HTTP Resend pronto para configurar remetente, chave e URL da instalação.
- Migração V5 preserva credenciais e acesso legado sem declarar verificação inexistente.

**Não há envio real configurado.** A demonstração usa dados fictícios e uma caixa de teste. Na hospedagem gratuita, contas e mensagens são temporárias. A fila de envio real é limitada em memória e ainda precisa de persistência para requisitos comerciais de entrega garantida.

O pacote demo requer Java 21 a 27 e inclui interface, servidor e banco. Extraia em pasta gravável e execute iniciar-demo.cmd, ou bash iniciar-demo.sh. O código-fonte e os requisitos de compilação estão no outro ZIP.

Leia EMAIL-ACESSO.md e VALIDACAO-0.5.0.md no repositório. O ZIP de fonte contém a documentação no commit da entrega; o relatório do ramo principal recebe a confirmação final de publicação.
