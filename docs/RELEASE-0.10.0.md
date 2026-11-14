# Agenda Clara 0.10.0

Clientes podem consultar, remarcar e cancelar a própria reserva por um link privado, sem criar conta. O comprovante oferece o link e a ação Gerenciar minha reserva. O responsável pode gerar ou revogar o acesso na agenda, inclusive para reservas antigas.

Preço, duração, profissional e serviço são preservados na remarcação. O servidor valida disponibilidade e versão da reserva, revalida o acesso depois de adquirir o bloqueio e impede que abas diferentes alterem outro atendimento. Sessões usam cookie HttpOnly por reserva; o segredo sai do endereço e não é guardado no armazenamento do navegador.

A migração V8 preserva dados existentes. Backups incluem os novos vínculos e sessões com a chave original. Use a ferramenta nova para restaurar esquemas 6, 7 ou 8. Banco e chave devem ser preservados ao atualizar.

Site, PWA e pacote local compartilham a atualização. A entrega dos links é manual; não há envio externo configurado. Sem nova dependência, recurso pago ou rotina agendada.

Consulte [Minha reserva](MINHA-RESERVA.md) e [validação da entrega](VALIDACAO-0.10.0.md). O pacote compilado exige somente Java 21 a 27; o código-fonte exige também Node compatível, conforme README.
