# API — versão 0.11.0

A interface e a API usam a mesma origem. Datas de consulta usam ISO `YYYY-MM-DD`; instantes enviados usam ISO com offset, como `2030-06-04T09:00:00-03:00`.

## Acesso e CSRF

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | /api/auth/csrf | token e headerName; cria/prepara a sessão |
| POST | /api/auth/register | cria empresa e responsável; 201 novo, 200 repetição idêntica; exige CSRF |
| POST | /api/auth/login | formulário username/password; 204 no sucesso |
| GET | /api/auth/me | nome, e-mail e estabelecimento; exige login |
| POST | /api/auth/logout | encerra a sessão; 204 |

Antes de uma alteração, consulte `/api/auth/csrf`. Envie o cookie recebido e o token no cabeçalho informado por `headerName`. Depois do login o token deve ser consultado novamente. A interface faz isso automaticamente.

## Consulta e reserva pública

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | /api/public/config | sinalização de demonstração, versão e storageMode (local, ephemeral ou persistent); não expõe conexão, senha ou caminhos |
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
| GET | /api/admin/business | empresa própria, publicação, versão e itens de configuração |
| PUT | /api/admin/business/publication | published e version; publica/oculta somente a própria empresa |
| GET | /api/admin/catalog | catálogo do próprio estabelecimento |
| GET | /api/admin/agenda?date=YYYY-MM-DD | reservas e bloqueios do dia |
| POST | /api/admin/appointments/{id}/cancel | cancela uma reserva futura |
| PATCH | /api/admin/appointments/{id}/reschedule | corpo startsAt com novo instante |
| POST | /api/admin/blocks | professionalId, startsAt, endsAt e reason |
| DELETE | /api/admin/blocks/{id} | remove um bloqueio |
| POST | /api/admin/services | name, description, durationMinutes e priceCents |
| GET | /api/admin/professionals | todos os profissionais do próprio negócio, inclusive inativos; versão e semana |
| POST | /api/admin/professionals | name e specialty; retorna 201, ativo sem expediente e sem serviços |
| PUT | /api/admin/professionals/{id} | name, specialty, active, version, hours e serviceIds; substituição atômica |

Duração de serviços: 15 a 240 minutos, em múltiplos de 15. Preço: 0 a 1.000.000 centavos. Telefones: 8 a 15 dígitos após normalização. Remarcação mantém profissional, duração e preço originais.

## Erros

400: campos inválidos, data fora do horizonte ou horário fora do expediente.
401: acesso sem sessão.
403: CSRF ausente/inválido ou operação não autorizada.
404: registro inexistente ou pertencente a outro estabelecimento.
409: sobreposição, repetição incompatível ou recurso sendo atualizado.

Erros de regras usam Problem Details com `status`, `title` e `detail`. Erros de validação podem incluir a lista `errors`. O cliente deve consultar novamente os horários após um conflito, sem presumir que a disponibilidade exibida anteriormente continua válida.

## Configuração de profissionais (0.2.0)

Exemplo de PUT, usando id e version obtidos no GET administrativo:

```json
{
  "name": "Ana Exemplo",
  "specialty": "Cortes e barba",
  "active": true,
  "version": 0,
  "hours": [
    { "weekday": 1, "opensAt": "09:00", "closesAt": "12:00" },
    { "weekday": 1, "opensAt": "13:00", "closesAt": "18:00" }
  ]
}
```

weekday usa 1=segunda a 7=domingo. Dia omitido fica fechado. Limite: 4 intervalos/dia e 28 entradas; horários no mesmo dia, em múltiplos de 15 minutos. Sobreposição: 400. Intervalos adjacentes são unidos. O retorno inclui a versão incrementada e horários normalizados.

Versão desatualizada, inativação com compromissos ou expediente incompatível com reserva confirmada futura/em andamento: 409, sem gravação parcial. Catálogo público lista somente ativos. Sem horas, não há disponibilidade. Consulte PROFISSIONAIS.md.
## Limites de entrada e frequência (0.2.1)

JSON tem limite de 16 KiB pelos bytes efetivamente recebidos, inclusive sem Content-Length; excesso retorna 413. Formulários também têm limite de 16 KiB no servidor HTTP. Login aceita e-mail de até 180 caracteres e senha de até 72 bytes UTF-8.

Cotas por instância/minuto: API 1200, login global 120, e-mail normalizado 20, reservas 60 e alterações administrativas 120. Uma chamada consome as cotas aplicáveis. Excesso retorna 429, Retry-After em segundos e detail com orientação de espera. Não repetir alterações automaticamente; preservar a chave da reserva se houver dúvida sobre uma confirmação anterior.

As cotas usam janelas fixas, são compartilhadas pelos visitantes e reiniciam com o processo. Configuração, cobertura dos testes e limitações estão em [REVISAO-SEGURANCA-0.2.1.md](REVISAO-SEGURANCA-0.2.1.md).

## Exceções de expediente por data (0.3.0)

GET, PUT e DELETE em /api/admin/professionals/{id}/exceptions exigem responsável autenticado; alterações exigem CSRF. PUT e DELETE usam a data no caminho; DELETE exige version na consulta. O corpo do PUT contém version, reason e hours; hours vazio fecha o dia.

O GET retorna version, firstDate, lastDate, weeklyHours e exceptions. A versão pertence à configuração do profissional e é compartilhada com a edição da semana. Alteração desatualizada ou que invalide atendimento confirmado retorna 409. Data ou intervalos inválidos retornam 400. Profissional de outro estabelecimento retorna 404.

A exceção substitui a semana na consulta, confirmação e remarcação. Os detalhes, exemplos e limites estão em [EXCECOES.md](EXCECOES.md).

## Cadastro e publicação (0.4.0)
POST /api/auth/register recebe requestKey (UUID), businessName (2–120), category (1–80), address (1–200), zoneId, ownerName (2–100), email (até 180), password e demoAcknowledged. Todos os textos são obrigatórios. O aceite é obrigatório quando app.demo.public=true. Os fusos e a política de senha estão em CADASTRO-EMPRESAS.md. Resposta: businessId, businessName, slug e email; não autentica automaticamente. Sessão já autenticada, e-mail indisponível ou reutilização incompatível da chave retornam 409 genérico.

GET /api/admin/business retorna business, published, version, services, professionals, hasHours e ready. PUT /api/admin/business/publication recebe {"published": true, "version": 0}. Configuração incompleta retorna 400; versão desatualizada, 409. Não recebe identificador de empresa como autorização.

GET /api/public/config inclui registrationEnabled. Empresas privadas são omitidas da listagem pública; consultas e reservas por seu slug retornam 404. A lista é um diretório público das empresas publicadas.

Novos cadastros têm cota padrão global de 5 requisições/minuto e o mesmo limite de corpo de 16 KiB. Falhas de validação retornam 400; CSRF ausente, 403; frequência excedida, 429 com Retry-After. REGISTRATION_ENABLED=false desativa o formulário e faz a API retornar 400 para novos cadastros.
## Confirmação e recuperação de acesso (0.5.0)
| Método | Rota | Corpo / resultado |
| --- | --- | --- |
| POST | /api/auth/email/request | email; 202 genérico |
| POST | /api/auth/email/confirm | token; 200 ou 400 para link inválido/vencido/utilizado |
| POST | /api/auth/password/request | email; 202 genérico |
| POST | /api/auth/password/reset | token e password; 200, revoga sessões anteriores e invalida outros links |
| GET | /api/auth/demo-mailbox | lista privada pelo cookie AGENDA_DEMO_MAILBOX; vazia sem prova ou fora do modo demo |
| POST | /api/auth/demo-mailbox/open | exige login; prepara cookie para a própria conta não compartilhada no modo demo |

As alterações exigem CSRF, inclusive sem login. O cadastro prepara a caixa simulada e a primeira confirmação automaticamente nesse modo. Mailbox não retorna mensagens de outro e-mail apenas por informar seu endereço. O GET não consome links; apenas POST explícito altera a conta.

Configuração pública inclui mailMode (demo/resend/disabled). GET /api/admin/business inclui emailVerified, verificationRequired, verificationSimulated e horizonDays. emailVerified pode representar simulação apenas no modo demo, identificada por verificationSimulated; o banco conserva a distinção. Contas legadas preservam a regra anterior; contas novas precisam confirmar antes de publicar.

Detalhes de validade, limites, transporte, migração e preparação do provedor: [EMAIL-ACESSO.md](EMAIL-ACESSO.md). Nunca registre tokens ou senhas em logs nem envie o segredo em query HTTP; a interface recebe links no fragmento e o envia no corpo protegido por CSRF.
## Acompanhamento de e-mails (0.6.0)

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | /api/admin/emails?status=FAILED | totais da empresa, estado do processador e até 50 registros dos últimos sete dias; filtro opcional |
| POST | /api/admin/emails/{id}/retry | 204 após agendar um ciclo manual elegível; exige CSRF |

O status aceita PENDING, PROCESSING, ACCEPTED, FAILED, EXPIRED ou CANCELLED; outro valor retorna 400. A empresa é obtida da sessão, sem parâmetro de autorização por estabelecimento. A consulta inclui mode, counts, oldestPendingAt, lastCheckAt, health e messages. Itens incluem identificador, finalidade, modo, situação, tentativas, reagendamentos, datas, lastError resumido e canRetry.

O painel não retorna destinatário, corpo, assunto, link, chave nem payload criptografado. A repetição exige falha elegível da própria empresa; outra empresa recebe 404 e estado incompatível recebe 409. Uma mensagem permite somente um ciclo manual, mantendo seu identificador e conteúdo. Na versão 0.6.1, nextAttemptAt também informa a espera solicitada pelo provedor quando status=FAILED; isso não agenda uma tentativa automática. Antes desse prazo, canRetry=false e a API recusa antecipação. ACCEPTED indica aceitação pelo provedor; no modo demo indica disponibilidade na caixa simulada. Não comprova recebimento externo. Consulte [FILA-EMAILS.md](FILA-EMAILS.md).

## Serviços por profissional (0.9.0)

O catálogo público e administrativo inclui serviceIds em cada profissional. A configuração GET inclui os vínculos de ativos e inativos. PUT /api/admin/professionals/{id} exige serviceIds: lista sem duplicatas, somente serviços do negócio autenticado, até 500 IDs e sujeita ao limite global do corpo. A consulta de disponibilidade e a reserva retornam 409 para combinação não vinculada. Retirar vínculo de reserva confirmada futura/em andamento ou enviar versão antiga também retorna 409. GET /api/admin/business acrescenta hasBookableProfessional, usado em ready para exigir serviço e expediente no mesmo profissional ativo. POST /api/admin/services não vincula automaticamente a equipe. Consulte [SERVICOS-PROFISSIONAIS.md](SERVICOS-PROFISSIONAIS.md).

## Área do cliente (0.10.0)

POST /api/customer/session recebe {token}; exige CSRF e cria cookie HttpOnly/SameSite=Strict, Secure quando habilitado, com Path=/api/customer/reservations/{id}. Retorna uma visão mínima com reservationKey, referência, empresa, cliente, serviço, profissional, horário, preço, status e version. Nenhum telefone ou segredo é devolvido nessa visão.

| Método | Rota | Corpo |
| --- | --- | --- |
| GET | /api/customer/reservations/{id} | — |
| GET | /api/customer/reservations/{id}/availability?date=YYYY-MM-DD | — |
| POST | /api/customer/reservations/{id}/cancel | {version} obrigatório |
| PATCH | /api/customer/reservations/{id}/reschedule | {version, startsAt} obrigatórios |
| DELETE | /api/customer/reservations/{id}/session | —; encerra somente essa reserva |

Essas rotas exigem a sessão privada correspondente ao id. O UUID e a referência pública nunca autorizam acesso. Alterações exigem CSRF; 401 indica acesso inválido, expirado ou revogado e 409 indica conflito.

O comprovante de uma nova reserva inclui customerAccessToken e customerAccessValidUntil quando há chave estável. São credenciais privadas; não registre o corpo da resposta. Repetições públicas retornam o token inicial apenas enquanto ele permanece vigente. Emissões posteriores nunca são expostas por repetição da reserva original.

O responsável autenticado tem GET/POST/DELETE /api/admin/appointments/{id}/customer-access, restritos ao próprio estabelecimento. POST recebe {version, requestKey}; DELETE recebe {version}. Gerar ou revogar acesso incrementa a versão da reserva e invalida sessões anteriores. O painel passou a enviar version no cancelamento e remarcação; a API administrativa aceita sua ausência para compatibilidade.

Consulte [Minha reserva](MINHA-RESERVA.md) para validade, entrega manual e recuperação.

## Regras de agendamento (0.11.0)

GET /api/admin/appointments/{id}/availability?date=YYYY-MM-DD consulta horários do atendimento próprio para o responsável, com duração original e exclusão da reserva atual, sem os prazos online do cliente. Exige login e isolamento por empresa.

GET/PUT /api/admin/business/booking-policy exigem responsável autenticado; PUT exige CSRF. O estabelecimento vem da sessão.

```json
{
  "bookingNoticeMinutes": 120,
  "cancellationNoticeMinutes": 1440,
  "rescheduleNoticeMinutes": 720,
  "version": 0
}
```

Todos os campos são obrigatórios. Intervalos: inteiros de 0 a 10080; version não negativa. Mudança efetiva incrementa a versão; valores idênticos mantêm a revisão. Versão antiga retorna 409.

Catalog inclui bookingPolicy. BookingRequest acrescenta policyVersion: use bookingPolicy.version do catálogo. Após a primeira revisão, omissão ou revisão antiga retornam 409. A interface atualiza o catálogo, preserva contato e exige nova escolha. Repetir uma reserva já concluída mantém sua resposta original.

BookingReceipt e AppointmentView incluem bookingPolicy da confirmação. A visão privada acrescenta bookingPolicy, canCancel, canReschedule, cancelUntil e rescheduleUntil. canChange continua indicando atendimento futuro confirmado; as duas novas permissões indicam cada ação disponível. Datas dos prazos são instantes UTC, exibidos no fuso da empresa.

Ações do cliente exigem prazo válido e antecedência do novo horário. O responsável mantém o atendimento de exceções futuras. Regras novas não são aplicadas retroativamente. Consulte [Regras de agendamento](REGRAS-AGENDAMENTO.md).
