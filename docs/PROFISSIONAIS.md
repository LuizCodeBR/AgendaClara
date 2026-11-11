# Profissionais e expediente — versão 0.2.0

## Como usar
1. Abra **Meu negócio** e entre com uma conta fictícia.
2. Selecione **Equipe e horários**.
3. Clique em **Cadastrar profissional**, informe nome e especialidade e confirme.
4. O cadastro nasce ativo, mas sem expediente. Ative os dias desejados e ajuste os intervalos.
5. Clique em **Salvar cadastro e horários**. As reservas públicas passam a usar essa configuração.

O mesmo painel funciona no site, no celular e na PWA instalada. No celular os controles ficam empilhados; não há uma segunda base de código.

## Regras
- Nome: obrigatório, até 100 caracteres; especialidade: obrigatória, até 160.
- Cada dia aceita até quatro intervalos no mesmo dia, com limites em múltiplos de 15 minutos.
- Dias sem intervalos ficam fechados. Sobreposição é rejeitada. Intervalos contíguos são unidos.
- O expediente é interpretado no fuso exibido do estabelecimento.
- A opção de copiar segunda para terça a sexta substitui esses dias no rascunho. Só o botão de salvar confirma.
- Novo profissional não possui horários até o responsável configurá-los.
- Inativos não aparecem no catálogo público nem aceitam novas reservas. Os horários e o histórico são preservados para reativação.
- Não é possível inativar alguém com atendimento confirmado futuro ou em andamento.
- Uma alteração de expediente não pode deixar um desses atendimentos fora da nova semana.
- Reservas passadas ou canceladas não bloqueiam a mudança. Bloqueios de períodos continuam valendo.

Quando houver conflito, a operação inteira é recusada: nem o nome, nem o estado, nem os horários são parcialmente alterados. O painel mantém o rascunho. Para liberar uma mudança incompatível, descarte o rascunho, remarque ou cancele os atendimentos pela agenda e tente novamente.

## Concorrência e acesso
Todas as operações pertencem ao estabelecimento do usuário autenticado; não se aceita um businessId do navegador como autorização. Rotas administrativas exigem responsável autenticado e alterações exigem CSRF.

Configuração e agendamento bloqueiam a mesma linha do profissional no banco. Assim, uma reserva e o fechamento do expediente não podem confirmar simultaneamente uma combinação inválida. O contador version impede que uma aba sobrescreva uma edição mais recente: a API responde 409 para versões antigas.

O campo profissional representa alguém que atende clientes. Ele não cria uma conta de login. Não há exclusão definitiva; use inativação.

## Arquivos principais
- team/TeamController.java: rotas administrativas.
- team/TeamService.java: validação, compromissos existentes e transação.
- team/TeamRepository.java: consultas sempre limitadas ao estabelecimento.
- catalog/WorkingHoursPolicy.java: regra compartilhada entre reserva e configuração.
- V2__professional_configuration.sql: migração aditiva de estado e versão.
- frontend/src/ProfessionalsPanel.tsx e team.css: interface responsiva.
- ProfessionalConfigurationTest.java: integração, segurança e concorrência.
- ProfessionalsPanel.test.tsx: comportamento do editor.
- frontend/e2e/booking.spec.ts: fluxo completo em desktop e celular.

## Validação
A suíte da API inclui 58 casos (26 desta etapa); a interface inclui 15 casos (6 desta etapa). O fluxo de navegador cobre cadastro, configuração, reserva pública, rejeição de redução incompatível e inativação após cancelamento, em desktop e celular. Os resultados efetivamente executados e links dos relatórios ficam em VALIDACAO-0.2.0.md.
