# Serviços atendidos por profissional — 0.9.0

## Como configurar
1. Entre em Meu negócio com a conta do estabelecimento.
2. Abra Equipe e horários e escolha um profissional.
3. Em Serviços atendidos, marque o que essa pessoa pode realizar.
4. Configure o expediente e clique em Salvar cadastro e horários.
5. Abra a página de reservas e escolha um serviço: somente os profissionais vinculados e ativos aparecem.

O mesmo painel é utilizado no site, celular/PWA e pacote local. Preço e duração continuam no catálogo do serviço. A especialidade é uma descrição livre; não define permissão de atendimento.

Novos profissionais começam sem serviços e sem horários. Novos serviços começam sem profissionais vinculados. O painel orienta onde concluir a configuração. É permitido salvar uma lista vazia; nesse estado o profissional não recebe novas reservas. A página de publicação exige pelo menos um profissional ativo com serviço vinculado e expediente semanal ou exceção aberta dentro do horizonte de reservas.

## Reservas e mudanças
- A consulta de disponibilidade e a confirmação validam o vínculo no servidor, além do filtro da tela.
- Trocar de serviço mantém um profissional ainda compatível ou seleciona o primeiro compatível; sempre limpa o horário escolhido.
- Quando não há profissional compatível, a tela explica a situação e impede confirmar.
- Atualizar serviços e profissionais recarrega o catálogo. Um conflito na confirmação também o recarrega e mantém nome e telefone digitados.
- Retirar um serviço utilizado por atendimento confirmado futuro ou em andamento retorna conflito. Cadastro, serviços e semana não são parcialmente salvos.
- Compromissos cancelados ou encerrados permanecem no histórico e permitem retirar o vínculo.
- A remarcação mantém o profissional, serviço, duração e preço da reserva; também valida o vínculo.
- A especialidade exibida é informativa; os serviços marcados são a configuração efetiva.

## Banco e atualização
A migração Flyway V7 cria professional_service. A chave primária impede repetições. As duas chaves estrangeiras compostas exigem que profissional e serviço pertençam ao mesmo estabelecimento.

A migração cadastra todas as combinações que a instalação anterior permitia, incluindo profissionais inativos, sem modificar nome, versão, reservas ou preços. Ela roda uma única vez. Remover um vínculo e reiniciar não o recria. As demonstrações novas recebem vínculos na criação inicial; o seeding respeita negócios já existentes.

Antes da atualização online foi criado um backup manual. A migração foi ensaiada em cópia temporária do Neon Free. A pasta data completa deve ser preservada ao atualizar o pacote local, incluindo a chave dos e-mails. Faça backup com o servidor H2 encerrado antes de substituir uma instalação existente.

A ferramenta de backup 0.9.0 aceita esquemas 6 e 7 e mantém o formato criptográfico ACBK0001. Versões futuras desconhecidas continuam recusadas na inspeção. Backups da versão nova exigem ferramenta compatível; não se deve voltar a executar uma versão antiga sobre banco já migrado.

## API e concorrência
O catálogo acrescenta serviceIds em cada profissional ativo. A configuração administrativa inclui serviceIds também para profissionais inativos.

PUT /api/admin/professionals/{id} exige serviceIds, com lista de UUIDs sem repetições e pertencentes ao negócio autenticado. Uma lista ausente ou inválida retorna 400; corpos acima do limite global retornam 413. A lista aceita até 500 itens, sujeita também ao limite global do corpo. O cadastro POST continua recebendo apenas name e specialty.

Cadastro, vínculos e semana são salvos na mesma transação, usando o bloqueio da linha do profissional compartilhado com as reservas. Se a retirada confirmar primeiro, a reserva incompatível é recusada; se a reserva confirmar primeiro, a retirada é recusada. O campo version também protege contra gravação de rascunho antigo de outra aba ou do editor de exceções.

Autenticação, CSRF e isolamento por estabelecimento continuam obrigatórios. Não se aceita identificador enviado pelo navegador como autorização de outro negócio.

## Verificação
Os novos testes cobrem API, constraints do banco, concorrência nas duas ordens, repetição segura da reserva, migração 6→7, preservação por backup e telas responsivas. scripts/professional-services-smoke.mjs também verifica o JAR real e é executado no CI e na publicação.

Resultados finais e links: [VALIDACAO-0.9.0.md](VALIDACAO-0.9.0.md).

## Compatibilidade atual (0.10.0)
A ferramenta 0.10.0 aceita esquemas 6, 7 e 8, incluindo os acessos de clientes da etapa [Minha reserva](MINHA-RESERVA.md). Preserve banco e chave e use a ferramenta nova para o banco migrado. As regras dos vínculos deste guia continuam vigentes.
