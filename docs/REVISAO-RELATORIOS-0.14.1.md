# Revisão de segurança e concorrência dos relatórios — 0.14.1

Revisão técnica em 15/11/2026, a partir do código 0.14.0. Escopo: autorização e contexto da empresa, leitura simultânea, respostas pendentes no navegador e CSV. Evidências locais no D:. Não equivale a certificação ou auditoria externa.

## Achados reproduzidos e correções

### Empresa da tela diferente da sessão ativa — prioridade média
Duas abas compartilham o cookie. Na 0.14.0, entrar na empresa B pela segunda aba e exportar pela primeira, ainda mostrando A, podia baixar um CSV de B com os filtros de A. A sessão continuava autorizada a B: o problema era a associação silenciosa entre tela e conta, não acesso a empresa sem autorização.

Reproduzido por teste HTTP e navegador real na versão antiga. A interface agora envia X-Agenda-Business nas opções, consulta e exportação. O servidor compara com a empresa da sessão e recusa divergência com 412, antes de consultar o relatório e sem anexo. A autorização continua baseada exclusivamente na sessão; o cabeçalho nunca escolhe a empresa consultada nem concede acesso.

O cabeçalho é opcional para compatibilidade com clientes anteriores. A interface 0.14.1 sempre o envia. Clientes externos que queiram a mesma proteção devem informar a empresa que esperam consultar. Sem ele, continua valendo o isolamento pela empresa autenticada. Recarregue a página/PWA após atualizar.

### Resposta inesperada salva como CSV — prioridade média
A interface conferia o status HTTP, mas aceitava qualquer Blob. Uma resposta HTML com status 200, simulando uma página intermediária de entrada/erro, podia ser baixada com extensão CSV. Não foi demonstrada execução de código ou fórmula a partir disso.

A interface exige tipo text/csv antes de criar o download. Formato diferente gera orientação e não produz arquivo. O usuário pode consultar novamente. Testes unitários e de navegador cobrem o caso.

### Falha de autorização durante operações simultâneas — prioridade média
O painel tratava 401, mas não encerrava o contexto para 403 ou para a nova precondição 412. O tratamento foi unificado: remove relatório/opções, cancela consultas/downloads em andamento e solicita novo acesso uma única vez. Respostas tardias, mesmo já preparadas, não reativam dados nem downloads.

Testes cobrem falha das opções enquanto uma consulta ou CSV aguarda, respostas tardias, cliques repetidos, troca de filtros, saída da tela e perda de conexão.

### Tempo limite sem resposta controlada — prioridade baixa
Uma QueryTimeoutException não possuía tratamento específico no relatório. A falha foi reproduzida por injeção controlada da exceção no teste, sem causar sobrecarga no serviço público.

O controlador converte QueryTimeoutException e TransactionTimedOutException em 503 application/problem+json, no-store e sem Content-Disposition. A mensagem orienta reduzir período/usar filtros e não expõe SQL ou detalhes internos. Não há repetição automática.

### Controle Unicode ainda presente nos rótulos — prioridade baixa
O normalizador usava a classe ASCII Cntrl, que não removia U+0085. Um teste confirmou sua permanência no campo exportado. Não foi demonstrada execução de fórmula por esse caractere.

A normalização passou a substituir controles da categoria Unicode Cc por espaços, mantendo o tratamento de Cf, separadores de linha e espaços repetidos. Prefixos textuais fixos e aspas escapadas foram preservados. Treze casos verificam fórmulas aparentes, aspas/separadores, quebras de linha, caracteres de largura completa e controles em todos os rótulos exportados.

## Verificações de concorrência e autorização
- Novas reservas confirmadas entre a agregação dos grupos e a dos dias, com período vazio e não vazio: métricas consistentes; nova consulta inclui a reserva.
- Cancelamento durante leitura: mantido o teste anterior de snapshot consistente.
- Nenhuma divergência foi reproduzida nesses testes; o isolamento transacional existente foi preservado. A documentação do H2 admite phantom reads em REPEATABLE_READ em geral, portanto os testes descrevem os cenários efetivamente verificados, não uma garantia universal sobre o motor.
- Cabeçalho de outra empresa não concede acesso. Sessão ausente retorna 401; função sem permissão, 403; cabeçalho inválido, 400; contexto divergente, 412.
- Mesmo um parâmetro businessId na URL não escolhe a empresa. Consultas e CSV sem filtros continuam restritos à sessão.
- CSV não expõe cliente, telefone, link privado ou credencial. no-store e nosniff verificados.
- Limites existentes de período, quantidade de grupos e duração da consulta preservados.

## Compatibilidade e operação
Mesmo código no site, PWA e pacote local. Sem biblioteca nova, mudança de esquema, recurso pago ou tarefa recorrente. V12 e ACBK0001 preservados. Faça backup, encerre a instalação antiga e mantenha data/mail-outbox.key ao atualizar localmente.

Foram acrescentados 18 casos de backend, oito da interface e quatro cenários de navegador (duas situações em desktop/mobile). Veja [os resultados de execução](VALIDACAO-0.14.1.md).

## Limites
A validação de CSV usa parsing e verificação dos campos, não automação dentro de Excel/LibreOffice. Prefixos não devem ser removidos ao processar conteúdo não confiável; não há promessa de sanitização universal para toda ferramenta de planilha. Mobile é navegador emulado. A leitura continua sendo uma fotografia dos dados; a exportação consulta novamente os filtros. Uma tela já aberta não recebe atualização automática quando outro dispositivo modifica o banco.

Referências: [OWASP — CSV Injection](https://owasp.org/www-community/attacks/CSV_Injection), [isolamento no H2](https://h2database.com/html/advanced.html#transaction_isolation), [isolamento no PostgreSQL](https://www.postgresql.org/docs/current/transaction-iso.html).
