# Revisão de uso e confiabilidade — Agenda Clara 0.2.2

Data: 11/11/2026 (America/Sao_Paulo). Esta rodada prioriza a necessidade atual do projeto: um recrutador conseguir abrir, testar e compreender o agendamento pela web, pelo celular e pelo pacote local, sem resultados enganosos ou perda acidental do expediente em edição.

## Problemas demonstrados e correções

| Prioridade | Situação anterior | Comportamento corrigido |
| --- | --- | --- |
| Alta | Ao trocar a data da agenda, uma falha de consulta deixava os clientes e bloqueios do dia anterior sob o título da nova data | Resultados antigos são removidos; contadores não exibem valores como se fossem da nova data; o erro oferece uma nova tentativa |
| Alta | Um link inicial para empresa inexistente era substituído silenciosamente pela primeira empresa da lista | A página informa que o estabelecimento não foi encontrado e oferece retorno ao início |
| Média | Uma sequência percentual inválida no link causava URIError durante a abertura | Leitura de rota protegida; página de recuperação também para rotas desconhecidas |
| Média | Escape podia fechar a janela nativa mesmo quando o componente recusava sair durante a gravação | O cancelamento nativo é interceptado; fechar/Escape/clique no fundo ficam bloqueados enquanto a operação está pendente |
| Média | O menu principal e a navegação pelo histórico podiam desmontar a edição de expediente sem salvar | O rascunho permanece aberto; é necessário salvar ou descartar antes de trocar de página dentro do aplicativo |
| Média | Comprovante e remarcação extraíam o dia de uma data UTC, mas mostravam a hora no fuso local | Dia e hora usam o fuso do estabelecimento; exemplo: 22h de 4 de junho não aparece como 5 de junho |

Erros de catálogo e agenda agora possuem estados independentes. Uma consulta não apaga a mensagem de erro da outra. A abertura inicial do painel também pode ser recuperada pela opção de recarregar o catálogo.

## Arquitetura

- Leitura das rotas centralizada em routing.ts, com tratamento explícito de rota desconhecida.
- Proteção de navegação informada pelo painel ao aplicativo principal; mantém o componente com o rascunho montado.
- Agenda exibida somente quando o resultado pertence à data selecionada e não está carregando ou em erro.
- Modal com estado busy comum aos diálogos de cadastro, cancelamento, remarcação e bloqueio.
- Identificadores de acessibilidade dos diálogos gerados por useId.
- Conversão da data local reutilizada no comprovante, remarcação e cálculo do dia atual.
- Nenhuma dependência nova, alteração do esquema ou mudança nas regras de gravação do servidor.

A proteção do rascunho cobre a edição de profissionais e expediente. Não constitui salvamento automático ou recuperação após fechamento forçado do navegador. O aviso de saída do navegador continua dependendo do comportamento e das permissões dele.

## Testes

Sete testes novos reproduziram inicialmente falhas de agenda, rotas e fechamento de janela. Após as correções, a suíte da interface passou a **30 testes** — onze a mais que na versão 0.2.1.

Os casos acrescentados verificam:
- retirada de dados antigos após erro ao consultar outra data;
- recuperação da mesma data e da abertura inicial do catálogo;
- link malformado, estabelecimento inexistente e rotas válidas;
- bloqueio e posterior liberação do fechamento de janela;
- preservação do rascunho ao usar menu, marca e alteração do endereço;
- comprovante noturno e conversão de dia em fusos a oeste e a leste de UTC.

Dois cenários novos de Playwright exercitam links inválidos e a combinação rascunho/gravação/Escape em navegador real. Cada cenário roda em desktop e celular emulado, totalizando **10 cenários de navegador** com os seis existentes.

Os 77 casos Java continuam verificando regras, segurança, concorrência e banco. Links e resultados definitivos estão em [VALIDACAO-0.2.2.md](VALIDACAO-0.2.2.md).

## Critério para as próximas revisões

1. Antes de cada entrega: revisar o fluxo alterado, mensagens de erro, uso pelo teclado/celular e compatibilidade local.
2. Ao alterar autenticação, dados ou reservas: revisar autorização, concorrência, repetição segura e exposição de dados.
3. Antes de publicar: validar o pacote, executar os testes adequados e conferir a versão online.
4. Antes de atender clientes reais: contas próprias, recuperação de acesso, persistência, backup/restauração e operação. Essas necessidades continuam separadas do escopo atual de demonstração.

Nenhuma automação periódica foi criada. As revisões acompanham as mudanças solicitadas e as evidências encontradas. Não foi necessário programa, extensão ou plano pago adicional.
