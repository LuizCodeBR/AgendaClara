# Indicadores e relatórios — 0.14.0

## Como consultar
Entre em **Meu negócio → Indicadores e relatórios** como responsável. Escolha **Hoje**, **Últimos 7 dias**, **Últimos 30 dias** ou preencha as datas. Os atalhos incluem hoje; o padrão é 30 dias. Combine profissional e serviço e pressione **Consultar relatório**.

O intervalo inclui as duas datas e aceita até 90 dias por consulta, entre 1900 e 2100. A data considerada é o início atual do atendimento no fuso da empresa, não a data de criação da reserva nem a data em que o resultado foi registrado. Uma remarcação muda o dia ao qual a reserva pertence. Cancelamentos são contados pelo dia reservado, não pelo dia em que foram cancelados.

## O que significam os números
| Indicador | Regra |
| --- | --- |
| Atendimentos do período | Todas as reservas encontradas, inclusive canceladas. |
| Concluídos | Resultado COMPLETED registrado. |
| Faltas | Resultado NO_SHOW registrado. |
| Cancelamentos | Situação CANCELLED. |
| Resultados pendentes | Reserva BOOKED cujo término previsto já chegou, segundo o relógio do servidor. |
| Confirmados ainda não encerrados | Reserva BOOKED com término posterior à consulta, inclusive em andamento. |
| Valor dos serviços concluídos | Soma dos preços preservados nas reservas COMPLETED. Não comprova recebimento de pagamento. |

As cinco situações somam o total. Pendência não vira falta automaticamente. Corrigir um resultado atualiza os números sem contar os eventos do histórico como atendimentos novos. Preços novos do catálogo não alteram o valor de reservas antigas.

O gráfico mostra o total por dia, incluindo cancelados. O resumo por profissional e o detalhamento por profissional/serviço mostram as mesmas métricas. As tabelas exibem 25 linhas por página; no celular, deslize a tabela horizontalmente para acessar as demais colunas.

Profissionais inativos continuam disponíveis para consultas históricas. O nome do profissional é o cadastro atual; o nome do serviço é o preservado na reserva. Renomear um serviço pode gerar linhas distintas para nomes históricos do mesmo serviço.

## Exportação CSV
Clique **Exportar CSV** após consultar. O arquivo inclui:
- Uma linha TOTAL.
- Uma linha PROFISSIONAL_SERVICO para cada combinação com atendimentos.
- Empresa, período, fuso, instante da consulta, filtros, identificadores de profissional/serviço e todas as métricas.

O CSV faz uma nova consulta autenticada usando os filtros aplicados. Pode refletir mudanças posteriores à tela; o instante da consulta fica no arquivo. Não é um fechamento contábil nem um relatório histórico imutável. Para combinar linhas em uma planilha, filtre o campo Tipo: não some TOTAL novamente com suas linhas detalhadas.

Formato: UTF-8 com BOM, separador ponto e vírgula, aspas escapadas, quebras CRLF e valores em reais com vírgula decimal. Excel e LibreOffice podem abrir/importar CSV; selecione UTF-8 e ponto e vírgula se a configuração regional não detectar as colunas.

Nomes recebem prefixos textuais visíveis, como `Profissional: `, e controles/quebras de linha são normalizados. Isso impede que rótulos fornecidos pelo usuário comecem como fórmulas na célula; não remova os prefixos ao tratar entradas não confiáveis. Aspas, ponto e vírgula e nomes semelhantes a fórmulas fazem parte dos testes. Referência: [OWASP CSV Injection](https://owasp.org/www-community/attacks/CSV_Injection).

Não são exportados nomes ou telefones de clientes, referências de reserva, links privados, segredos ou histórico individual. O arquivo ainda contém informações comerciais da empresa: quem o baixa decide onde guardá-lo e compartilhá-lo.

## Atualização, privacidade e limites
Os dados são consultados sob demanda. Alterar filtros oculta o resultado anterior até uma nova consulta; exportações pendentes são canceladas ao mudar filtros, sair da tela ou perder conexão. Respostas atrasadas não substituem a visualização atual. Sem conexão, uma consulta já exibida é identificada como anterior; atualizar/exportar exige reconexão.

Não há consulta automática periódica, envio por mensagem ou gravação do relatório no navegador. As APIs exigem OWNER, identificam a empresa pela sessão e usam no-store. IDs de outra empresa não dão acesso aos seus dados. CSV e tela aplicam as mesmas regras.

O servidor agrega os dados no banco e limita o período a 90 dias, cada cadastro de opções a 2000 itens e o detalhamento a 2000 combinações. Ultrapassar o limite gera erro explícito: reduza o período ou filtre; não há corte silencioso. As tabelas são paginadas na interface. Há limite transacional de 15 segundos e proteção contra totais fora da precisão numérica suportada.

Cada relatório usa uma transação de leitura consistente. Se uma reserva for cancelada enquanto o relatório é calculado, todas as seções representam a mesma leitura. Uma consulta posterior mostra o cancelamento. Os limites de dias são calculados no fuso da empresa, inclusive em mudança de horário de verão.

## Web, aplicativo e atualização local
O mesmo código atende o site, a PWA e o pacote local. O site/PWA compartilha o banco online; a instalação local mantém seu banco separado. Recarregue/reabra a PWA após atualizar. Não há sincronização automática entre bancos local e online.

A migração V12 acrescenta apenas o índice por empresa e início do atendimento. Não altera reservas, resultados, histórico ou contatos. Faça backup, encerre a instalação anterior e preserve a pasta data e a chave mail-outbox.key ao iniciar a 0.14.0. Use a ferramenta de backup nova, compatível com esquemas 6 a 12.

Não há dependência nova, extensão obrigatória do VS Code, serviço adicional ou plano pago. O pacote compilado continua exigindo apenas Java 21–27; o código-fonte mantém seus requisitos de Java e Node.

## Testes
ReportTest cobre filtros/isolamento, situações, limites de datas, dias de 23 horas, preços históricos, somas acima de 32 bits, nomes hostis no CSV, remarcação, correções de resultado e cancelamento concorrente. ReportMigrationTest verifica a atualização V11 → V12 preservando dados e a compatibilidade do backup.

A interface tem testes de filtros, respostas tardias, erros, sessão expirada, cancelamento de exportação, paginação e download. O teste de navegador reports.spec.ts executa o fluxo desktop/mobile com CSV real. scripts/reports-smoke.mjs verifica a API e compara relatórios antes/depois de backup/restauração. Resultados de execução ficam em VALIDACAO-0.14.0.md.
