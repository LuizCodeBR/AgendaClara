# Revisão de segurança e concorrência — acessos profissionais 0.15.1

15/11/2026. Revisão dedicada do código existente, separada da etapa de implementação; não é auditoria externa nem certificação.

## Escopo
Rotas administrativas e profissionais, sessão/CSRF, isolamento por empresa/titular, token/validade/uso único, convite/aceite/revogação, recuperação de senha, caixa demo, transações, histórico reduzido e respostas concorrentes da interface. Sem alteração dos papéis ou inclusão de permissão administrativa.

## Achados e correções
1. **Alta no modo demo — capacidade de recuperação sobrevivente.** A versão 0.15.0 desabilitava o login mas mantinha digest da caixa demo e ações pendentes. O controlador também reabria a caixa por e-mail depois do aceite já confirmado. A combinação permitia que solicitação antiga disputasse recursos de recuperação com uma reativação. Revogação agora remove caixa e invalida ações atomicamente. Aceite devolve internamente uma prova nova criada na mesma transação; o controlador só anexa essa prova ao cookie. Abertura por sessão revalida identidade/credenciais sob bloqueio. Contas desabilitadas não solicitam/consomem recuperação ou confirmação e não consultam a caixa, inclusive dados legados da 0.15.0. Não foi constatada exploração no ambiente online; a evidência é reprodução controlada e análise do fluxo.
2. **Média — bloqueio de agenda alheia antes da autorização.** A tentativa de resultado bloqueava a linha do profissional do atendimento antes de verificar o titular. Duas solicitações cruzadas poderiam disputar bloqueios de agendas/contas. Agora o vínculo é verificado antes de qualquer mutex; resposta continua 404, sem alteração de dados. Revalidação da conta após o bloqueio próprio permanece.
3. **Baixa — convite substituído ainda exibido.** O responsável podia receber a resposta de emissão e, ao atualizar a equipe, já encontrar convite alterado por outra janela. O segredo só é exibido se profissional ativo, versão, e-mail e validade ainda corresponderem à emissão e não houver conta habilitada. O prazo de emissão é normalizado para microssegundos, igual à precisão persistida, para evitar divergência falsa na reconciliação. Em divergência, há aviso e reconciliação obrigatória.
4. **Baixa — histórico atrasado em outro contexto.** Mudança de data/atualização não encerrava todas as consultas pendentes do histórico. Agora elas são abortadas e suas respostas descartadas. Fechar uma consulta lenta permite abrir outra; a finalização da antiga não libera indevidamente o controle da nova. Retorno de foco não descarta justificativa de resultado aberta.

## Evidência
Antes das correções, dois testes novos falharam no backend (caixa após revogação e bloqueio alheio) e dois na interface (convite substituído e histórico atrasado). Logs red: .cache/access-review-red-backend.log e .cache/access-review-red-ui.log. Depois, os mesmos casos passaram.

Casos adicionados incluem: recuperação/confirmacão legadas recusadas, reativação com prova distinta e sessão antiga recusada, abertura aguardando bloqueio durante revogação/reativação, dois profissionais tentando o mesmo e-mail, aceite versus revogação com versão, rollback conjunto da conta/caixa, histórico fechado/foco e preservação do rascunho. Navegador e rotina HTTP verificam caixa vazia e reset antigo inválido após revogar; navegador também reativa a mesma identidade e recusa senha anterior.

Resultados completos e publicação: [VALIDACAO-0.15.1.md](VALIDACAO-0.15.1.md).

## Regras preservadas e limites
- Tokens aleatórios de 32 bytes, somente digest persistido, 24 horas, uso único e nova checagem sob bloqueio.
- Permissões no servidor, dados mínimos, cabeçalho de conta esperada, CSRF e no-store.
- Operação que confirma antes da revogação pode concluir; se a revogação confirmar primeiro, operação profissional obsoleta é recusada. Consultas já autorizadas podem terminar. Dados já vistos não são apagados remotamente sem nova comunicação.
- Aceite que vence a disputa incrementa a versão: revogação usando versão antiga recebe 409, exige atualização e nova confirmação. A interface não comunica sucesso falso.
- Convite manual autoriza quem possui o link, sem comprovar a titularidade do e-mail. Continua sem envio automatizado/serviço pago.
- Caixa demo é uma simulação privada; não é provedor de e-mail. Mensagem já entregue ou copiada não pode ser recolhida, mas o link invalidado não redefine a senha.
- V13/ACBK0001 preservados. Restaurar backup recupera o estado daquele momento; revisar acessos após restauração. Não executar binário 0.14.1 ou anterior no banco V13.
- Mobile testado por emulação; não é ensaio em aparelho físico. Nenhuma garantia de ausência absoluta de vulnerabilidades.

## Referências técnicas
[OWASP — recuperação de senha](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html): tokens com validade/uso único, proteção contra abuso e invalidar sessões após alteração.
[PostgreSQL 17 — bloqueios explícitos](https://www.postgresql.org/docs/17/explicit-locking.html): conflitos de bloqueios e importância de ordem consistente para evitar deadlocks.
