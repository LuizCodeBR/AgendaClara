# API — versão 0.1.0

A interface e a API usam a mesma origem. Datas de consulta usam ISO `YYYY-MM-DD`; instantes enviados usam ISO com offset, como `2030-06-04T09:00:00-03:00`.

## Acesso e CSRF

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | /api/auth/csrf | token e headerName; cria/prepara a sessão |
| POST | /api/auth/login | formulário username/password; 204 no sucesso |
| GET | /api/auth/me | nome, e-mail e estabelecimento; exige login |
| POST | /api/auth/logout | encerra a sessão; 204 |

Antes de uma alteração, consulte `/api/auth/csrf`. Envie o cookie recebido e o token no cabeçalho informado por `headerName`. Depois do login o token deve ser consultado novamente. A interface faz isso automaticamente.

## Consulta e reserva pública

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | /api/public/config | sinalização de demonstração e versão |
| GET | /api/public/businesses | estabelecimentos disponíveis |
| GET | /api/public/{slug}/catalog | serviços, profissionais, fuso e data sugerida |
| GET | /api/public/{slug}/availability?professionalId=UUID&serviceId=UUID&date=YYYY-MM-DD | horários disponíveis |
| POST | /api/public/{slug}/bookings | confirma uma reserva |

Corpo da reserva:

```json
{
  "requestKey": "UUID-NOVO-PARA-A-TENTATIVA",
  "professionalId": "UUID-DO-CATALOGO",
  "serviceId": "UUID-DO-CATALOGO",
  "startsAt": "2030-06-04T09:00:00-03:00",
  "customerName": "Cliente de exemplo",
  "customerPhone": "11999991234"
}
```

Os valores acima são ilustrativos; use identificadores reais do catálogo e uma data dentro do horizonte atual.

O servidor calcula término e preço. Sucesso novo retorna 201; repetição idêntica retorna 200 com o mesmo código. O comprovante não inclui nome ou telefone do cliente. Conflito de horário ou chave retorna 409.

## Painel do responsável

Todas as rotas abaixo exigem sessão. O estabelecimento é obtido do usuário autenticado.

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | /api/admin/catalog | catálogo do próprio estabelecimento |
| GET | /api/admin/agenda?date=YYYY-MM-DD | reservas e bloqueios do dia |
| POST | /api/admin/appointments/{id}/cancel | cancela uma reserva futura |
| PATCH | /api/admin/appointments/{id}/reschedule | corpo startsAt com novo instante |
| POST | /api/admin/blocks | professionalId, startsAt, endsAt e reason |
| DELETE | /api/admin/blocks/{id} | remove um bloqueio |
| POST | /api/admin/services | name, description, durationMinutes e priceCents |

Duração de serviços: 15 a 240 minutos, em múltiplos de 15. Preço: 0 a 1.000.000 centavos. Telefones: 8 a 15 dígitos após normalização. Remarcação mantém profissional, duração e preço originais.

## Erros

400: campos inválidos, data fora do horizonte ou horário fora do expediente.
401: acesso sem sessão.
403: CSRF ausente/inválido ou operação não autorizada.
404: registro inexistente ou pertencente a outro estabelecimento.
409: sobreposição, repetição incompatível ou recurso sendo atualizado.

Erros de regras usam Problem Details com `status`, `title` e `detail`. Erros de validação podem incluir a lista `errors`. O cliente deve consultar novamente os horários após um conflito, sem presumir que a disponibilidade exibida anteriormente continua válida.
