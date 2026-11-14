# Conclusão, faltas e histórico — 0.12.0

## Registrar o resultado
1. Entre em Meu negócio e abra Minha agenda.
2. Consulte a data do atendimento. Após o término previsto, a reserva confirmada mostra Resultado ainda não registrado.
3. Escolha Concluir atendimento ou Registrar falta.
4. Confira o resultado. Uma justificativa inicial é opcional; confirme para salvar.
5. Consulte os contadores e filtros Concluídos e Faltas.

O horário do servidor decide a permissão. O instante exato do término é aceito; antes dele a API recusa, mesmo que o relógio do navegador esteja adiantado. O painel atualiza a disponibilidade das ações a cada 30 segundos sem consultar a API. Recarregar a agenda também atualiza a exibição.

## Corrigir um erro
Abra Corrigir resultado. Concluído pode virar Não compareceu e vice-versa. Escreva uma justificativa com 5 a 500 caracteres após retirar espaços nas extremidades. O registro anterior permanece no histórico. A correção não reabre a reserva nem altera horários, serviço, profissional, preço ou regras contratadas. Reserva cancelada não recebe resultado.

Uma falha temporária permite repetir a mesma tentativa. Uma tela com versão antiga recebe conflito e precisa atualizar; o rascunho fica visível até usar Atualizar e descartar rascunho. Justificativa pendente protege a navegação, o fechamento da janela e o fechamento do formulário.

## Consultar o histórico
Use Histórico no cartão do atendimento. Os registros mais recentes aparecem primeiro, com data, autor, ação, situação anterior/nova e justificativa. Remarcações mostram os dois horários. O histórico é restrito ao responsável da empresa, em páginas de até 50 eventos, com Carregar registros anteriores.

A partir desta versão são registrados criação de reserva, cancelamento, remarcação, resultado e correção. Criações feitas pela página pública e alterações pelo acesso privado aparecem como Cliente. Alterações administrativas mostram o nome do responsável autenticado. Preparações internas aparecem como Sistema.

O histórico começa na implantação da versão 0.12.0. Reservas anteriores não ganham eventos inventados. Alterações de acesso privado continuam com sua proteção própria e podem incrementar a versão da reserva sem gerar evento de atendimento; por isso a numeração pode ter intervalos.

A API não oferece edição ou exclusão do histórico. Não é um registro criptográfico inviolável: operadores com acesso direto ao banco possuem capacidades além do painel.

## Cliente, web, mobile e local
Minha reserva mostra Concluído/Não compareceu enquanto o acesso privado estiver válido. O cliente não classifica nem consulta o histórico interno. A sessão e o link mantêm os prazos já existentes.

Site e PWA usam a mesma interface e banco online; o pacote local tem H2 independente. Não há gravação offline das alterações privadas. O servidor precisa estar ativo e acessível. Use dados fictícios na demonstração.

## Segurança, atualização e backups
As novas operações exigem responsável autenticado e CSRF. A empresa vem da sessão. A classificação bloqueia o mesmo profissional usado pelas outras alterações, reconfirma a versão e grava situação e evento na mesma transação. Falha no histórico desfaz a mudança inteira.

Uma chave de tentativa identifica classificação/correção. Repetir a mesma chave com os mesmos dados não refaz a operação; devolve a situação atual, inclusive se houver correção posterior. Reusar a chave com outros dados retorna 409. Uma nova tentativa para o mesmo resultado sem alteração também é recusada, evitando eventos duplicados.

A migração V10 substitui a restrição dos estados da reserva e cria appointment_history, sem classificar registros existentes. Chaves estrangeiras compostas mantêm reserva, empresa e autor coerentes. O histórico e seus índices fazem parte do backup do banco.

Preserve banco e chave originais. Encerre o servidor local normalmente, faça backup e ensaie a atualização em uma cópia. A ferramenta 0.12 aceita backups dos esquemas 6 a 10; ACBK0001 permanece. Não execute ferramentas antigas sobre o esquema novo.

Não foi acrescentada dependência, extensão, serviço pago ou tarefa recorrente. Projeto e entregas desta instalação ficam no D:. [Validação](VALIDACAO-0.12.0.md).
