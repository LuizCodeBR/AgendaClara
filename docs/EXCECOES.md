# Folgas e horários especiais — Agenda Clara 0.3.0

Uma exceção define o expediente de **um profissional em uma data**. Serve para um feriado, uma folga ou uma abertura extraordinária, inclusive em um dia da semana normalmente fechado.

## Como usar

1. Entre em **Meu negócio** e escolha **Folgas e horários especiais**.
2. Selecione o profissional e a data.
3. Informe um motivo, como “Feriado” ou “Atendimento especial”.
4. Para fechar o dia, mantenha **Não atender nesta data** marcado. Para atender, desmarque e configure os intervalos.
5. Clique em **Salvar exceção**. A página pública de reservas já considera a nova configuração.

A tela informa o expediente semanal daquela data. A exceção **substitui todos os intervalos semanais daquele dia**, não acrescenta horários à semana. Por exemplo: se a semana prevê 09h–12h e a exceção prevê 19h–20h, apenas 19h–20h será oferecido.

Os bloqueios da agenda continuam sendo descontados. Um profissional inativo permanece indisponível para novas reservas, mesmo com uma exceção de abertura.

## Limites

- Configuração de hoje até 365 dias adiante, conforme o fuso do estabelecimento.
- Até quatro intervalos, com início e fim em passos de 15 minutos, no mesmo dia.
- Intervalos sobrepostos são recusados; intervalos contíguos são unidos ao salvar.
- O motivo fica no painel do estabelecimento; não aparece na disponibilidade pública.
- O calendário é preenchido manualmente. Não há importação automática de feriados nacionais, estaduais ou municipais nesta versão.
- A exceção é por profissional e data; aplicação em lote e períodos de férias são evoluções possíveis.

## Reservas existentes

O sistema recusa salvar ou remover uma exceção que deixe um atendimento confirmado futuro ou em andamento fora dos horários válidos. Ele informa a referência e a data do atendimento para que o responsável remarque ou cancele primeiro.

Exemplo: você abriu excepcionalmente 19h–20h e recebeu uma reserva às 19h. Se a semana prevê somente 09h–12h, remover a exceção será recusado enquanto a reserva estiver confirmada.

Reservas canceladas ou já encerradas não impedem mudar o expediente futuro. O histórico dos atendimentos é preservado.

## Remover e editar

Para retornar à semana, clique em **Remover exceção e usar expediente semanal** e confirme. A remoção também protege as reservas existentes.

Enquanto houver alterações, salve ou descarte antes de trocar de profissional, data ou área do aplicativo. Em caso de erro, o rascunho permanece. Se outra sessão mudou o cadastro ou os horários, use a opção explícita de descartar e recarregar antes de refazer a alteração.

A proteção do rascunho não recupera alterações após fechamento forçado do navegador.

## API e persistência

| Método | Rota | Função |
| --- | --- | --- |
| GET | /api/admin/professionals/{id}/exceptions | Versão, período configurável, semana e exceções futuras |
| PUT | /api/admin/professionals/{id}/exceptions/{YYYY-MM-DD} | Criar ou substituir a exceção |
| DELETE | /api/admin/professionals/{id}/exceptions/{YYYY-MM-DD}?version=N | Remover e voltar à semana |

Todas as rotas exigem o responsável do estabelecimento; alterações exigem CSRF. O identificador da empresa vem da sessão. Exemplo do corpo do PUT:

~~~json
{
  "version": 3,
  "reason": "Atendimento especial",
  "hours": [
    { "opensAt": "19:00", "closesAt": "20:00" }
  ]
}
~~~

Uma lista hours vazia representa dia fechado. A versão é compartilhada com a configuração do profissional e da semana: edições desatualizadas recebem 409 e não sobrescrevem alterações recentes. O GET retorna firstDate, lastDate, weeklyHours e exceptions junto da versão.

A migração V3 acrescenta date_hours e date_hours_window. Não apaga registros existentes. Há teste de atualização de um banco V2 com estabelecimento, profissional, expediente e reserva já gravados.

## Arquitetura e concorrência

EffectiveHours resolve a exceção por data e usa a semana quando não existe exceção. Disponibilidade, confirmação e remarcação usam essa regra.

Configuração diária, semanal e gravações da agenda utilizam o mesmo bloqueio de linha do profissional em READ COMMITTED. Assim, uma reserva e uma mudança de expediente concorrentes não podem confirmar um estado incompatível. A atualização da semana verifica as exceções de cada data antes de validar compromissos.

As tabelas possuem chaves estrangeiras compostas por estabelecimento, e o registro diário é único por empresa, profissional e data. A lista é limitada ao período configurável. Motivos passados ficam no banco, mas a tela desta versão gerencia o período atual e futuro.

Voltar para uma versão anterior do aplicativo pode fazer com que ela ignore as exceções, embora as tabelas continuem no banco. A estratégia de retorno precisa considerar essa diferença de comportamento.

Os resultados da entrega estão em [VALIDACAO-0.3.0.md](VALIDACAO-0.3.0.md).

## Atualização do H2 e de uma instalação existente

O teste de migração com uma reserva antiga reproduziu um defeito do H2 2.4.240 na validação de CHECK após troca de conexões. O componente foi atualizado para **2.5.252**, que inclui a correção registrada no [histórico oficial](https://h2database.com/html/changelog.html) e no [problema 4302](https://github.com/h2database/h2database/issues/4302). O teste que revelou o problema foi mantido.

A conferência local também criou um banco com a Agenda Clara 0.2.2/H2 2.4.240, encerrou o servidor e abriu esse arquivo com a 0.3.0/H2 2.5.252. Cadastros, expediente, serviços e sete atendimentos fictícios foram comparados antes e depois; a migração V3 e novas reservas foram verificadas.

Antes de atualizar uma instalação própria, encerre o Java e guarde uma cópia da pasta data. Para portabilidade entre versões do H2, o fornecedor recomenda exportação e restauração por script SQL; veja [Upgrade, Backup and Restore](https://h2database.com/html/tutorial.html#upgrade_backup_restore). O teste de um arquivo não garante compatibilidade de qualquer banco histórico ou modificado fora do aplicativo.

Nenhum instalador adicional é necessário: o H2 atualizado já está no JAR. Para uma cópia nova, basta extrair o pacote e usar seu iniciador.
