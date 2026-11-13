# API — versão 0.4.0

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
| POST | /api/admin/professionals | name e specialty; retorna 201, ativo sem expediente |
| PUT | /api/admin/professionals/{id} | name, specialty, active, version e hours; substituição atômica |

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