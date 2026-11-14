# Regras de agendamento — 0.11.0

Cada empresa configura três antecedências independentes. A mesma implementação atende site, PWA e pacote local.

## Configurar no painel

1. Entre em Meu negócio com a conta da sua empresa.
2. Abra **Regras de agendamento** no menu, ou **Configurar regras de agendamento** em Página de reservas.
3. Escolha a antecedência mínima para reservar, para cancelar pelo cliente e para remarcar pelo cliente.
4. Confira os valores e use **Salvar regras de agendamento**.

Há opções de zero até sete dias, apresentadas em minutos ou horas. A API aceita minutos inteiros entre 0 e 10080; um valor personalizado já existente é preservado pela tela. Zero permite reservar ou alterar até antes do início, conforme disponibilidade. Não é possível alterar um atendimento já iniciado.

Alterações valem para novas reservas. As já confirmadas mantêm os valores e a versão registrados na confirmação. A migração mantém empresas e reservas antigas em zero, sem restrições adicionais.

Se outra janela alterar as regras antes de salvar, o sistema recusa a versão antiga. O rascunho permanece na tela; **Recarregar regras e descartar rascunho** consulta os valores vigentes. Sair com alterações pendentes é protegido.

## Exemplo

Para um atendimento na quinta-feira às 14h:

| Regra | Configuração | Prazo |
| --- | --- | --- |
| Reservar | 2 horas | Até quinta-feira às 12h |
| Cancelar pelo cliente | 24 horas | Até quarta-feira às 14h |
| Remarcar pelo cliente | 12 horas | Até quinta-feira às 2h |

Prazos positivos aceitam o instante exato do limite. Zero exige que o atendimento ainda não tenha começado. O servidor mede tempo decorrido e a interface apresenta as datas no fuso da empresa.

As três configurações são independentes. Um prazo de cancelamento maior que a antecedência de uma reserva pode deixar o cancelamento online indisponível desde a confirmação. Essa situação é permitida e explicada na configuração; as regras e datas são mostradas antes de confirmar.

## Cliente

A página pública mostra as regras antes da confirmação. A grade remove horários que não respeitam a antecedência mínima. O servidor confere novamente ao confirmar, inclusive se o horário apareceu na tela antes de o prazo terminar.

Se a empresa alterar as regras enquanto o cliente preenche os dados, a confirmação é recusada com pedido de atualização. Nome e telefone são preservados, mas é preciso conferir as novas regras e escolher novamente o horário.

O comprovante mostra as condições registradas. Minha reserva apresenta os prazos e oferece separadamente as ações de cancelar e remarcar enquanto cada uma estiver disponível. Depois do prazo, a consulta continua funcionando; o cliente deve falar com o estabelecimento para uma exceção.

Uma remarcação usa a mesma antecedência mínima, preço, duração, profissional, serviço e regras da reserva. Os prazos são recalculados a partir do novo início e mostrados antes de confirmar o novo horário. Alterar as regras atuais da empresa não muda essa reserva.

Repetir uma confirmação já concluída mantém sua resposta e regras originais. Repetir um cancelamento concluído é seguro. A repetição imediata de uma remarcação concluída não provoca outra alteração, inclusive se o prazo terminou depois da primeira confirmação.

## Responsável e exceções

Os prazos online do cliente não bloqueiam o responsável. Ele pode cancelar ou remarcar atendimentos futuros pelo painel, mantendo as validações de disponibilidade, expediente, preço, duração e versão. Atendimento já iniciado continua protegido.

A interface informa que o responsável pode atender essas exceções. Não há taxa, cobrança automática, pagamento, mensagem externa ou alteração da reserva apenas porque um prazo terminou.

## Dados, API e concorrência

A migração V9 acrescenta três intervalos e uma versão em business, além da cópia desses valores em appointment. Defaults zero preservam dados e comportamentos anteriores. Os CHECKs do banco e a validação HTTP limitam os intervalos a sete dias e proíbem versões negativas.

GET/PUT **/api/admin/business/booking-policy** exigem login do responsável. O estabelecimento vem da sessão; a requisição não escolhe a empresa. PUT exige CSRF, os três valores e a versão atual. Conflitos retornam 409.

Catálogo público e administrativo incluem bookingPolicy. Novas confirmações enviam policyVersion. O campo pode ser omitido apenas enquanto a configuração ainda tem versão zero, para compatibilidade com clientes antigos. Quando as regras já têm revisão, é obrigatório informar a revisão vigente. Repetir uma reserva existente devolve as condições originais antes de avaliar regras novas.

O servidor coordena alterações e captura de regras com um bloqueio breve na linha da empresa, antes do bloqueio do profissional. O UPDATE não altera a chave da empresa, mantendo compatibilidade com verificações de chave estrangeira no PostgreSQL. A política é lida de novo após a espera; prazo e disponibilidade são conferidos antes de gravar. Reservas da mesma empresa podem aguardar essa transação curta, mesmo entre profissionais distintos; empresas diferentes permanecem independentes.

A reserva pública também reconfirma a publicação depois de obter o bloqueio. A preparação interna de cadastros não publicados mantém seu fluxo próprio. Consultas usam leitura consistente; alterações usam READ_COMMITTED.

## Atualização e backups

Preserve banco e chave originais juntos. Encerre o servidor local normalmente com Ctrl+C e faça backup antes de substituir o JAR. O aplicativo novo migra automaticamente o esquema ao iniciar.

A ferramenta 0.11.0 aceita esquemas 6, 7, 8 e 9. Os backups incluem as regras atuais e as condições individuais das reservas, além dos acessos privados. O formato ACBK0001 permanece. Não execute uma ferramenta antiga sobre o banco migrado.

Não foi adicionada dependência, extensão ou instalação obrigatória. Site e PWA compartilham o servidor online; o pacote local tem banco independente. Recarregue a página/PWA depois de atualizar. Render e Neon permanecem Free e as rotinas de backup e limites continuam sob demanda.

Consulte [a validação](VALIDACAO-0.11.0.md), [Minha reserva](MINHA-RESERVA.md) e [backup e recuperação](ARMAZENAMENTO-BACKUPS.md).
