# Agenda Clara 0.11.1

Revisão independente de segurança e concorrência das regras de agendamento.

Reservas e alterações do cliente revalidam os prazos depois das consultas e imediatamente antes da gravação, usando o mesmo instante para a decisão e o registro. A remarcação verifica tanto o prazo da reserva original quanto a antecedência do novo horário. Exceções do responsável continuam limitadas a atendimentos futuros.

Valores JSON fracionários destinados a campos inteiros são recusados com HTTP 400, incluindo minutos e versões. O bloqueio de regras exige transação de escrita ativa. Não muda o esquema do banco (V9), as condições existentes nem o formato dos backups.

Web, PWA e pacote local usam as mesmas correções. Sem dependência, extensão, serviço pago ou agendamento adicional. Veja [a revisão](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/REVISAO-REGRAS-0.11.1.md).
