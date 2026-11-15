# Portfólio — Agenda Clara

**Referência funcional:** 0.15.1. **Situação:** escopo concluído para portfólio, em manutenção corretiva. O [README](../README.md) atende a quem quer experimentar; as [decisões técnicas](DECISOES-TECNICAS.md) apoiam a conversa sobre implementação.

## Descrição curta para o portfólio

Agenda Clara é um sistema full stack de agendamento para pequenos negócios, desenvolvido com Java/Spring Boot, React/TypeScript e banco relacional. Reúne reservas públicas, gestão da empresa e acesso próprio dos profissionais. Demonstra regras de negócio, concorrência, permissões, histórico, relatórios CSV e recuperação de dados, com site/PWA, pacote local e testes automáticos.

## Destaques para currículo ou apresentação

- Implementação de reservas com validação transacional de disponibilidade, reconhecimento de tentativas repetidas e controle de versão.
- Separação de acesso por empresa e profissional, convites de uso único e revogação coordenada com alterações em andamento.
- Entrega web/PWA e pacote Java local, com PostgreSQL/H2, migrações e provas automatizadas de backup/restauração.

Use essas descrições de acordo com o que consegue explicar e demonstrar. Evite atribuir volume de usuários, resultado comercial, ganho de produtividade ou capacidade de carga que não foram medidos.

## Demonstração de cinco minutos

| Tempo aproximado | O que mostrar | Ponto a explicar |
| --- | --- | --- |
| 0:00–0:40 | Tela inicial e Barbearia Horizonte | Quem usa: cliente, responsável e profissional |
| 0:40–1:50 | Serviço, profissional, disponibilidade e confirmação fictícia | Servidor calcula preço/duração e confere o horário novamente |
| 1:50–2:40 | Meu negócio → conta Marina → agenda | A reserva aparece; outra empresa tem seu próprio escopo |
| 2:40–3:40 | Indicadores por período e CSV | Mesma data da reserva; valores são de serviços, sem comprovar pagamento |
| 3:40–4:30 | Captura mobile de Minha agenda ou conta em janela privada | Profissional recebe somente os próprios atendimentos |
| 4:30–5:00 | Decisões técnicas e CI | Escolher uma regra, apontar implementação e teste |

Prefira uma instalação local descartável para demonstração planejada. A hospedagem pública é compartilhada e pode ter alterações de outros visitantes. O pacote local exige só Java; o primeiro acesso online pode aguardar a inicialização gratuita.

Selecione o próximo dia com expediente e use a mesma data na agenda e no relatório. As capturas guardam o estado de 15/11/2026 com atendimentos em 16/11/2026.

## Acesso profissional ao vivo, se houver tempo

1. Entre como responsável, abra Acessos da equipe e escolha profissional ativo sem acesso habilitado.
2. Gere convite com e-mail fictício e abra o link em janela privada.
3. Ative com senha própria, entre e selecione a data da reserva daquele profissional.
4. Volte ao responsável, atualize os acessos e revogue. Ao atualizar Minha agenda, a sessão profissional perde acesso.

Use base local de demonstração e credenciais somente durante a apresentação. Não exponha convite, recuperação, cookies ou link privado da reserva em uma captura pública. Para apresentação curta, use as imagens em vez de criar a conta.

Conclusão e falta ficam disponíveis após o término previsto. Em uma reserva futura, explique a regra e mostre o teste correspondente; não altere relógio ou status no banco para encenar resultado.

## Três assuntos para estudar antes de uma entrevista

### Reserva simultânea

Explique por que a consulta de disponibilidade não garante que o horário continuará livre. Localize bloqueio e checagem de sobreposição em ScheduleService. Compare com ConcurrencyTest. Considere custo de espera e bloqueio da política da empresa.

### Repetição e versão

Descreva uma resposta perdida depois da gravação. Explique a chave da tentativa e por que a mesma chave com conteúdo diferente é recusada. Depois, descreva uma edição a partir de tela antiga e o papel da versão.

### Permissões e revogação

Mostre que o servidor obtém empresa e profissional da sessão. Explique versão de credenciais, revalidação após bloqueio e quem vence a disputa entre gravação e revogação. Dados já vistos e consultas já autorizadas têm limites próprios.

As [decisões técnicas](DECISOES-TECNICAS.md) contêm as referências exatas para essas leituras.

## Evidências

- [Galeria](imagens/README.md): seis capturas da aplicação real, dados fictícios e dois fluxos mobile emulados.
- [CI da 0.15.1](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43758801503): Java 21/26, H2/PostgreSQL, Docker, interface, navegador e recuperação.
- [Validação](VALIDACAO-0.15.1.md): contagens, ambiente, publicação, hashes e limites.
- [Revisão dos acessos](REVISAO-ACESSOS-0.15.1.md): problemas reproduzidos, correções e concorrência.
- [Download executável](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.15.1): demonstração sem compilação.

As contagens não são porcentagens de cobertura. Não apresente a revisão como auditoria externa nem a emulação mobile como teste em aparelho físico.

## Limites intencionais

WhatsApp manual; e-mail real depende do remetente/provedor; PWA exige conexão; local e online têm bancos independentes; valor de serviço não é cobrança/pagamento; hospedagem gratuita tem limites. Não há declaração de validação comercial ou capacidade para determinado número de usuários.

## Critério de manutenção

Reabrir desenvolvimento por falha reproduzível, dependência com correção relevante ou necessidade de usuário/cliente com escopo definido. Novas funcionalidades entram em proposta separada. Sem backups ou monitoramento periódico agendados. A apresentação está concluída para permitir iniciar outro projeto.
