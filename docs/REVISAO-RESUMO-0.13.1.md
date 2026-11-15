# Revisão do resumo diário — 0.13.1

15/11/2026. Revisão do código e dos limites de confiança da 0.13.0, com casos de falha escritos antes das correções. Não é uma auditoria externa nem uma garantia de ausência de vulnerabilidades.

## Achados e correções

1. **Resultado incerto ao salvar contato (prioridade média).** A resposta podia se perder depois de o servidor gravar o número. Descartar o rascunho liberava o contato antigo. Toda falha de gravação agora bloqueia edição/compartilhamento até consultar o estado atual. O rascunho continua visível; a consulta explícita o descarta.
2. **Conferência exclusivamente local (prioridade média).** A caixa de conferência liberava uma prévia carregada anteriormente, mesmo após mudanças em outra janela. Confirmá-la agora faz nova consulta autenticada e compara empresa, data/fuso, profissional, contato/versão e todos os itens. Mudança atualiza a prévia e exige nova conferência. Falha ou sessão encerrada não libera o compartilhamento. Não há envio automático nem consulta periódica.
3. **Validade contada da chegada (prioridade média).** Uma consulta lenta recebia cinco minutos adicionais ao chegar. O prazo agora começa antes da requisição e usa relógio civil e monotônico; recuo do relógio não prolonga a validade. A hora apresentada pelo servidor é capturada antes de ler o banco, não no fim de uma leitura demorada.
4. **Retorno do segundo plano (prioridade média).** Ao sair do aplicativo/ocultar a aba, restaurar uma página preservada pelo navegador ou perder conexão, a conferência perde a validade. Uma resposta que já estava em andamento não pode revalidá-la. É necessário atualizar explicitamente.
5. **Conclusão tardia da cópia (prioridade baixa).** Uma recusa tardia da área de transferência podia selecionar uma prévia diferente da original. Agora o resultado é ignorado se a visualização ou sua validade mudou. Texto já copiado pelo sistema operacional não pode ser recolhido.
6. **Mistura de instantes após salvar (prioridade baixa).** O contato recém-gravado era combinado com reservas da consulta anterior. Após sucesso, a tela recarrega o resumo completo e exige nova conferência.

Os cinco primeiros cenários de regressão falharam na versão anterior. O caso de leitura lenta do servidor e os demais casos complementam a cobertura. As evidências finais de execução estão em VALIDACAO-0.13.1.md.

## Fronteiras verificadas

- Empresa obtida da sessão; profissional e reservas consultados com filtro de empresa. Outro estabelecimento recebe 404. Conta sem papel de responsável e usuário anônimo não acessam as rotas privadas.
- Gravação exige CSRF e versão. Remoção de telefone e edição simultânea do cadastro disputam o mesmo bloqueio; apenas uma versão vence. A leitura consistente anterior continua coberta por teste durante cancelamento/alteração de contato.
- Respostas privadas com Cache-Control no-store. PWA não armazena/reenvia respostas da API, nomes, contato ou gravações offline.
- Texto simples, rótulos sem controles de linha/direção, destino fixo wa.me, telefone validado e texto codificado, proteção da janela de origem e ausência de Referer.
- Links não são abertos nos testes: verifica-se o conteúdo preparado. Dados e números de teste são fictícios.

## Limites e atualização

A consulta de conferência é uma fotografia consistente; uma alteração posterior ainda pode ocorrer antes ou depois do envio manual. Não bloqueamos a agenda durante a leitura nem prometemos revogar um link/texto já copiado. Confira a conversa no WhatsApp. Ao abrir o link, número e conteúdo são transferidos ao serviço externo e podem permanecer no histórico do navegador.

Mesma interface para web, PWA/mobile e local. Sem alteração de esquema (permanece V11), nova dependência, extensão, serviço pago, tarefa recorrente ou API de envio. Backup ACBK0001 e leitura dos esquemas 6–11 preservados. Atualização local: encerrar o servidor, fazer backup, usar o pacote novo com a mesma pasta de dados e chave. Site e banco local continuam independentes.

Referências verificadas: [visibilidade e temporizadores](https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API), [relógio monotônico](https://developer.mozilla.org/en-US/docs/Web/API/Performance/now), [link oficial WhatsApp](https://faq.whatsapp.com/5913398998672934).