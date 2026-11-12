# Revisão de segurança e arquitetura — Agenda Clara 0.2.1

Data: 11/11/2026 (America/Sao_Paulo). Escopo: código da API e interface, sessão, isolamento de estabelecimentos, gravação concorrente, entrada de dados, dependências e distribuição web/PWA/local. Revisão de código e testes direcionados; não é certificação ou teste de invasão exaustivo.

## Resultado e alterações

| Achado | Avaliação no contexto da demonstração | Correção |
| --- | --- | --- |
| Login e reservas públicas sem limite de frequência na aplicação | Médio: tentativas repetidas consomem CPU, sessões e gravações | Controle antes de CSRF e autenticação, com cotas globais e por e-mail normalizado |
| JSON sem limite próprio antes de desserializar | Médio: alocação e processamento de corpos grandes | Limite de 16 KiB pelos bytes efetivamente recebidos, mesmo sem Content-Length |
| Hash de senha permanecia no objeto de usuário da sessão; representação textual automática incluía campos | Baixo, defesa em profundidade: não foi encontrada exposição desse hash pela API | CredentialsContainer apaga a referência ao hash após autenticação; toString contém somente o ID |
| Busca de um registro carregava listas completas; equipe fazia uma consulta de horários por profissional | Eficiência e manutenção | Consultas SQL diretas e parametrizadas; listagem da equipe passa a duas consultas |
| Consultas compostas de catálogo/disponibilidade/agenda sem transação própria | Consistência de leitura | Transações somente de leitura em REPEATABLE READ |
| Falha de conexão ou limite durante preparação CSRF tinha tratamento diferente | Experiência de uso | Tratamento comum na interface, mensagens do servidor preservadas e nenhuma repetição automática da alteração |

As classificações são qualitativas para este projeto; não são pontuações CVSS. Apagar a referência ao hash não garante sobrescrita imediata da memória da JVM.

## Controle de frequência

As cotas são por instância, em janelas fixas de 60 segundos iniciadas no primeiro acesso de cada chave:

| Grupo | Padrão por minuto | Variável de ambiente |
| --- | ---: | --- |
| Todas as requisições /api/ | 1200 | API_REQUESTS_PER_MINUTE |
| Tentativas de login, conjunto de usuários | 120 | LOGIN_REQUESTS_PER_MINUTE |
| Tentativas por e-mail normalizado | 20 | ACCOUNT_REQUESTS_PER_MINUTE |
| Confirmações públicas de reserva | 60 | BOOKING_REQUESTS_PER_MINUTE |
| Alterações administrativas | 120 | WRITE_REQUESTS_PER_MINUTE |

Cada requisição passa pelos grupos aplicáveis; tentativas recusadas por CSRF ou credenciais também podem consumir cota. Contadores não dependem de cookie, sessão nem cabeçalho de IP enviado pelo cliente. O e-mail é normalizado como na autenticação e sua chave é um SHA-256; isso não constitui anonimização de dados.

O mapa possui capacidade fixa de 2048 chaves e descarta entradas expiradas quando necessário. Não remove contadores ativos para admitir novas chaves. Atualizações são sincronizadas, inclusive sob concorrência. O tempo é monotônico.

Ao exceder uma cota, a API retorna **429**, **Retry-After** em segundos e mensagem de espera. O aplicativo exibe a orientação, preservando o fluxo de erro existente. Não há reenvio automático de reservas.

**Limites desta solução:** terceiros podem consumir cotas compartilhadas e provocar uma interrupção temporária para outros visitantes. Janelas fixas permitem rajadas próximas à virada da janela. Contadores reiniciam com o processo e não são compartilhados entre servidores. É proteção básica para a demonstração de uma instância, não solução contra DDoS nem confirmação de identidade de quem reserva. Para operação comercial, avaliar limites compartilhados, controle na borda e confirmação de contato. Não tornar os limites dependentes de X-Forwarded-For sem definir proxies confiáveis.

## Tamanho e autenticação

- JSON da API: máximo de **16384 bytes**; a leitura para em 16385 para detectar excesso, inclusive com transferência em blocos. Excesso retorna **413**.
- Formulário: Tomcat configurado com máximo de 16 KiB; formulário excessivo sem tamanho declarado retorna erro de cliente, sem autenticar. A resposta pode ser 400/401 conforme o ponto de rejeição.
- Login: nome de usuário de até 180 caracteres e senha de até 72 bytes UTF-8, compatível com o limite do BCrypt. Valores excessivos recebem erro genérico de credenciais.
- Login válido continua usando sessão, BCrypt e CSRF; não foi introduzido JWT ou armazenamento de token em localStorage.
- Cookie: HttpOnly, SameSite=Lax; Secure habilitado na hospedagem HTTPS. A sessão expira após 30 minutos de inatividade.
- API mantém no-store, CSP e demais cabeçalhos de segurança também nas rejeições produzidas dentro da cadeia de segurança.

## Arquitetura e garantias preservadas

O projeto continua um monólito por funcionalidades, com React/PWA servido pelo Java. Não houve troca de linguagem, banco ou infraestrutura, nem alteração de esquema.

Buscas de profissional e serviço continuam vinculadas ao estabelecimento; profissional inativo permanece indisponível ao público. A listagem administrativa busca perfis e horários em duas consultas filtradas pela empresa, em vez de 1 + N consultas.

REPEATABLE READ mantém as leituras de uma mesma resposta dentro da transação. Isso **não reserva** o horário exibido: a confirmação continua revalidando expediente e conflitos sob bloqueio do profissional em READ COMMITTED.

A revisão preservou e os testes exercitam:
- autorização baseada na empresa do usuário autenticado;
- consultas SQL parametrizadas e chaves estrangeiras compostas;
- impedimento de reservas sobrepostas e conflitos com bloqueios;
- repetição segura da confirmação por chave e conteúdo;
- atomicidade da alteração de profissional e expediente;
- proteção de atendimentos futuros e em andamento;
- disponibilidade pública sem dados pessoais;
- PWA sem cache de API nem fila de alterações para envio posterior.

## Verificação

Validação local: **77 casos Java** aprovados, sem falhas ou casos ignorados; **19 testes da interface** aprovados; compilação TypeScript/Vite aprovada. São 19 casos Java e 4 da interface acrescentados à versão 0.2.0.

Os novos casos cobrem cotas concorrentes, expiração, capacidade limitada, normalização do e-mail, troca de sessão/cookies/cabeçalhos, limites de senha e corpo, JSON e formulário sem tamanho declarado, rotação de sessão, remoção do hash e tratamento de falhas no navegador.

Os fluxos existentes continuam cobrindo isolamento, concorrência e regras de agenda. A matriz remota testa Java 21/26 com H2/PostgreSQL, seis cenários de navegador em desktop/celular e Docker/PostgreSQL. Resultados finais e links da execução serão registrados em VALIDACAO-0.2.1.md.

Dependências consultadas em 11/11/2026 (12/11 em UTC):
- npm audit: zero alertas nos níveis informativo, baixo, moderado, alto e crítico.
- OSV querybatch: 66 coordenadas Maven de compilação/execução, sem alertas retornados para as versões consultadas.
- Não houve atualização de bibliotecas apenas para trocar versões. A consulta não cobre vulnerabilidades ainda desconhecidas, dependências de testes/plugins Maven ou a imagem inteira do sistema operacional.
- Flyway informa que a versão H2 é mais recente que sua última versão declarada como verificada; as duas migrações e os testes H2 passam. A matriz PostgreSQL verifica também o caminho alternativo.

## O que ainda falta para clientes reais

A demonstração tem contas compartilhadas e armazenamento efêmero no Render gratuito. Seus dados não devem ser tratados como registros operacionais de clientes. Localmente, o H2 em arquivo persiste entre reinícios.

Antes de uma entrega comercial: contas próprias e recuperação de acesso; persistência com backup e restauração testada; política de tratamento e exclusão de dados; monitoramento e controle de abuso adequado à quantidade de instâncias; confirmação de contato conforme necessidade do negócio. Esses pontos não foram apresentados como resolvidos por esta revisão.

## Referências

- [OWASP: prevenção de esgotamento de recursos](https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html).
- [Spring Security: arquitetura e ordem dos filtros](https://docs.spring.io/spring-security/reference/servlet/architecture.html).
- [OSV: consulta de vulnerabilidades por lote](https://google.github.io/osv.dev/api/#tag/query/operation/querybatch).
