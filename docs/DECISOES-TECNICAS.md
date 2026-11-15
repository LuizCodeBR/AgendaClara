# Decisões técnicas — Agenda Clara 0.15.1

Escolhas presentes no código e seus limites. Complementa a [arquitetura](ARQUITETURA.md), o [contrato HTTP](API.md) e a [validação](VALIDACAO-0.15.1.md). Cada decisão inclui uma pergunta para a apresentação.

## 1. Monólito por funcionalidades, com uma entrega

**Problema:** permitir avaliar interface, API e banco sem configurar vários serviços.

**Escolha:** React/TypeScript compilado e servido pelo Spring Boot. Catálogo, acessos, agenda, relatórios e operação têm responsabilidades separadas no mesmo aplicativo. Interface e API usam a mesma origem. O pacote usa H2 em arquivo; a hospedagem usa PostgreSQL. Flyway evolui o esquema e a CI verifica os dois bancos.

**Alternativa e custo:** frontend separado ou microsserviços criariam mais pontos de configuração. A entrega única facilita demonstração, mas os módulos compartilham processo, implantação e recursos. H2 não substitui a validação PostgreSQL; dialetos e bloqueios precisam de verificação nos dois ambientes.

**Evidência:** [pom.xml](../pom.xml), [Dockerfile](../Dockerfile), [CI](../.github/workflows/ci.yml) e [migrações](../backend/src/main/resources/db/migration).

**Pergunta:** por que uma entrega única atende ao tamanho e à distribuição deste projeto?

## 2. Revalidar horários dentro de uma transação

**Problema:** duas pessoas podem enxergar um horário livre e confirmar juntas. A tela não garante exclusividade.

**Escolha:** gravações da agenda coordenam pelo registro do profissional, com SELECT FOR UPDATE. Dentro da transação, o servidor verifica sobreposição e expediente efetivo. A reserva bloqueia a política da empresa antes do profissional e revalida a antecedência no limite da gravação. A proteção usa o banco, coordenando processos que compartilhem esse banco e respeitem esse protocolo.

**Alternativa e custo:** bloqueio apenas na memória de uma instância não coordena outro processo. A serialização pode causar espera; o bloqueio da política também pode serializar confirmações da mesma empresa entre profissionais diferentes. Não há benchmark que prove capacidade para determinado volume. Escritas externas ao protocolo precisam de proteção própria.

**Evidência:** [ScheduleService](../backend/src/main/java/br/com/luizcode/agenda/scheduling/ScheduleService.java), [CatalogRepository](../backend/src/main/java/br/com/luizcode/agenda/catalog/CatalogRepository.java), [ConcurrencyTest](../backend/src/test/java/br/com/luizcode/agenda/ConcurrencyTest.java) e [BookingPolicyConcurrencyTest](../backend/src/test/java/br/com/luizcode/agenda/BookingPolicyConcurrencyTest.java).

**Pergunta:** o que ocorre se ambas as telas consultarem disponibilidade antes de qualquer confirmação?

## 3. Distinguir repetição da tentativa e edição sobre versão antiga

**Problema:** uma confirmação pode ser gravada e sua resposta se perder. Outra janela pode alterar a mesma reserva enquanto a primeira permanece aberta.

**Escolha:** a chave da tentativa e os dados da operação reconhecem uma repetição; reutilizar a chave com outro conteúdo gera conflito. Versões detectam comandos baseados em dados antigos. A interface preserva a chave da mesma tentativa e exige reconciliação em conflitos ou respostas incertas.

**Alternativa e custo:** desabilitar o botão só reduz cliques naquela tela. A repetição precisa da mesma chave; gerar outra em toda retentativa elimina essa informação. Versão e idempotência resolvem problemas diferentes e não garantem execução única em sistemas externos.

**Evidência:** [ScheduleService](../backend/src/main/java/br/com/luizcode/agenda/scheduling/ScheduleService.java), [AppointmentOutcomeService](../backend/src/main/java/br/com/luizcode/agenda/scheduling/AppointmentOutcomeService.java), [ProfessionalAgendaPage](../frontend/src/ProfessionalAgendaPage.tsx) e [AppointmentOutcomeTest](../backend/src/test/java/br/com/luizcode/agenda/AppointmentOutcomeTest.java).

**Pergunta:** quando reconhecer uma repetição e quando responder com conflito?

## 4. Sessão por cookie, permissões no servidor e revogação

**Problema:** empresas e profissionais compartilham a aplicação. Alterar IDs no navegador ou trocar de conta em outra aba não pode conceder acesso indevido.

**Escolha:** Spring Security com sessão, cookie HttpOnly, BCrypt e CSRF. Empresa e profissional vêm da identidade autenticada; OWNER e PROFESSIONAL têm rotas distintas. APIs profissionais retornam campos reduzidos e conferem a conta esperada na tela. Estado da conta e versão de credenciais são consultados nas requisições. Gravações sensíveis revalidam a identidade sob bloqueio. Convites aleatórios guardam digest; revogação invalida convite, acesso e recuperação pendente.

**Alternativa e custo:** JWT exigiria seu próprio ciclo de expiração/revogação; a sessão atende à aplicação de mesma origem. Consultar a conta acrescenta acesso ao banco. Sessões não são compartilhadas automaticamente entre instâncias; escalar o login exige definir essa estratégia. Consultas já autorizadas podem terminar e dados já exibidos não são apagados remotamente. A ordem de confirmação define quem vence a disputa entre gravação e revogação.

**Evidência:** [SecurityConfiguration](../backend/src/main/java/br/com/luizcode/agenda/identity/SecurityConfiguration.java), [CredentialVersionFilter](../backend/src/main/java/br/com/luizcode/agenda/identity/CredentialVersionFilter.java), [ProfessionalAccessService](../backend/src/main/java/br/com/luizcode/agenda/identity/ProfessionalAccessService.java) e [ProfessionalAccessTest](../backend/src/test/java/br/com/luizcode/agenda/ProfessionalAccessTest.java). [Revisão 0.15.1](REVISAO-ACESSOS-0.15.1.md).

**Pergunta:** qual a diferença entre esconder uma função e impedir sua execução no servidor?

## 5. Preservar condições e registrar alterações atomicamente

**Problema:** editar preço, duração ou prazos não deve mudar silenciosamente o que foi reservado. Um resultado sem seu histórico perderia o contexto.

**Escolha:** a reserva conserva preço, nome do serviço, intervalo e regras aceitas. Alteração e evento de histórico são gravados na mesma transação, com ator, versão, instante e motivo quando aplicável. Relatórios agregam reservas, sem contar eventos como atendimentos novos. Limites de dia são calculados no fuso da empresa; o CSV omite dados pessoais dos clientes e protege rótulos contra fórmulas.

**Alternativa e custo:** usar sempre o catálogo atual simplifica o modelo, mas altera o significado histórico. Preservar campos aumenta os dados armazenados. O relatório representa o estado atual por período; o CSV faz nova consulta. Não são fechamento financeiro imutável nem prova de pagamento. O histórico não é declarado inviolável por administradores do banco.

**Evidência:** [AppointmentHistory](../backend/src/main/java/br/com/luizcode/agenda/scheduling/AppointmentHistory.java), [ReportService](../backend/src/main/java/br/com/luizcode/agenda/reports/ReportService.java), [ReportCsv](../backend/src/main/java/br/com/luizcode/agenda/reports/ReportCsv.java) e [ReportTest](../backend/src/test/java/br/com/luizcode/agenda/ReportTest.java).

**Pergunta:** por que valor de serviço concluído não comprova pagamento recebido?

## 6. Fila transacional e recuperação verificável

**Problema:** gravar a conta e perder a mensagem deixa o fluxo incompleto. Reinício e perda de dados precisam de recuperação testada.

**Escolha:** a mensagem entra na fila persistente antes do commit da mesma transação de negócio. O processador usa concessão temporária do trabalho, tentativas limitadas e estado persistido. Conteúdo sensível é criptografado e a chave precisa ser preservada. Backups protegidos incluem banco e chave, são verificados e restaurados em destino separado; a CI confere dados e acessos após restaurar.

**Alternativa e custo:** envio síncrono depende do provedor e não resolve sozinho a consistência com o banco. A fila interna evita outro serviço, mas exige tratar interrupções, limites e recuperação. Aceitação pelo provedor não comprova recebimento e não há promessa universal de entrega única. Restaurar backup recupera os acessos válidos naquele momento; é necessário revisá-los.

**Evidência:** [MailDispatcher](../backend/src/main/java/br/com/luizcode/agenda/identity/MailDispatcher.java), [MailOutboxRepository](../backend/src/main/java/br/com/luizcode/agenda/mail/MailOutboxRepository.java), [MailOutboxTest](../backend/src/test/java/br/com/luizcode/agenda/MailOutboxTest.java), [BackupTest](../backend/src/test/java/br/com/luizcode/agenda/backup/BackupTest.java) e [workflow](../.github/workflows/ci.yml).

**Pergunta:** qual a diferença entre persistência, backup e demonstrar que ele pode ser restaurado?

## 7. PWA com consultas online e escopo encerrado

**Problema:** oferecer acesso mobile sem um cliente nativo separado e sem confirmar horários com dados offline desatualizados.

**Escolha:** interface responsiva, manifesto e service worker com reconexão. Dados privados da API e reservas não entram no cache ou em fila offline. WhatsApp depende de ação manual. O ciclo funcional encerra na 0.15.1; manutenção trata correções reproduzíveis e adaptações com necessidade definida.

**Alternativa e custo:** cliente nativo ou sincronização offline adicionariam instalação, conflitos e outro ciclo de entrega. A PWA precisa do servidor e de conexão; instalá-la não inicia um servidor local. A escolha reduz os clientes a manter, mas não oferece operação de agenda offline.

**Evidência:** [service worker](../frontend/public/sw.js), [manifesto](../frontend/public/manifest.webmanifest), [fluxos de navegador](../frontend/e2e/booking.spec.ts) e [guia web/mobile](WEB-MOBILE.md).

**Pergunta:** por que tratar a reconexão antes de confirmar uma reserva?

## Como demonstrar

Escolha duas ou três decisões e percorra problema → código → teste → limite. Use o [roteiro](PORTFOLIO.md). As evidências demonstram os cenários executados; não comprovam capacidade de carga, uso comercial, auditoria externa ou ausência absoluta de falhas.
