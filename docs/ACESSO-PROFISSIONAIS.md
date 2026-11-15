# Acesso próprio dos profissionais — 0.15.0

## Liberar o acesso
1. Entre com a conta responsável e configure o profissional em Equipe e horários.
2. Abra Acessos da equipe, escolha a pessoa e informe um e-mail fictício exclusivo na demonstração.
3. Escolha Gerar convite. Confira o destinatário e compartilhe o link em uma conversa privada. Copiar não envia mensagem.
4. Em outro navegador ou após sair da conta atual, o profissional abre o convite e define sua senha.
5. Depois da ativação, escolhe Entrar na minha agenda e entra com o e-mail do convite e a senha definida.

O convite vale 24 horas, só pode ser usado uma vez e não comprova posse do e-mail. A conta não entra automaticamente após a ativação. O link só aparece na resposta da geração; não é recuperável pelo painel. Gerar outro invalida o anterior. Se houver falha de conexão, atualize os acessos antes de tentar novamente: o servidor pode ter recebido a operação.

A senha exige ao menos 15 caracteres Unicode e no máximo 72 bytes UTF-8, sem truncamento. Não use senhas reais na demonstração pública. Contas compartilhadas @demo.agendaclara.dev continuam exclusivas dos exemplos e não podem ser convidadas.

## Minha agenda
O mesmo endereço /#/painel identifica a função após o login. O profissional recebe Minha agenda; o responsável mantém o painel completo. A consulta começa em hoje no fuso da empresa e aceita até 365 dias antes/depois, com limite explícito de 200 atendimentos por dia. Mostra início/fim, nome do cliente, serviço e situação, incluindo cancelados e resultados registrados. Atualize para conferir alterações em outro aparelho. Retornar o foco à janela também atualiza a agenda quando não há gravação em andamento.

Após o término previsto, registre conclusão ou falta. Correções exigem justificativa de pelo menos cinco caracteres. Prazo, versão da reserva, repetição da tentativa e gravação conjunta do histórico usam o mesmo serviço do responsável. O profissional não cancela/remarca reservas nem edita horários nesta etapa.

O histórico profissional é resumido e paginado: situação, momento e função de quem alterou. Não inclui justificativas privadas, nomes das contas, telefones, preços, referências, links privados ou credenciais. O responsável conserva o histórico completo, com identificação da conta profissional que realizou a alteração.

## Permissões
| Operação | Responsável | Profissional |
| --- | --- | --- |
| Agenda | Empresa inteira | Próprios atendimentos |
| Conclusão/falta/correção | Empresa inteira | Próprios atendimentos, mesmas regras |
| Histórico | Completo da empresa | Resumo dos próprios atendimentos |
| Relatórios/CSV | Sim | Não |
| Equipe, expediente, regras, serviços, publicação e fila de e-mails | Sim | Não |
| Convites e revogação | Sim | Não |

Empresa e profissional vêm da conta autenticada. Identificadores ou funções enviados pelo navegador não concedem permissões. APIs profissionais exigem X-Agenda-Account com a conta esperada, inclusive para distinguir profissionais da mesma empresa quando outra aba troca o login. APIs administrativas de acessos usam o mesmo cabeçalho. Divergência retorna 412; função incorreta, 403; ausência de sessão, 401. Alterações e prévia/aceite de convite exigem CSRF.

## Revogar ou reativar
Em Acessos da equipe, escolha Revogar acesso e convites e confirme. Isso invalida convites, desabilita a conta e incrementa a versão de credenciais. Sessões antigas perdem acesso na próxima requisição. Dados já exibidos não são apagados remotamente de outra tela sem nova comunicação com o servidor.

Revogar login não inativa o cadastro nem cancela reservas. As configurações e os históricos ficam preservados. Inativar o profissional também impede uso da conta enquanto estiver inativo, mas continua sujeito às proteções existentes para atendimentos futuros.

Para reativar uma conta revogada, gere outro convite usando o mesmo e-mail. A pessoa define nova senha; a identidade e o histórico são preservados, e sessões anteriores continuam inválidas. Alteração do e-mail da conta e múltiplos vínculos de empresa ficam fora desta etapa. Um e-mail já utilizado por outra conta não pode ser apropriado.

## Concorrência e segurança
Emissão, rotação, revogação, ativação e alterações da agenda coordenam pela linha do profissional. Versões dos acessos recusam comandos antigos. O aceite reconsulta o convite depois do bloqueio e antes de concluir; somente o digest SHA-256 de um segredo aleatório de 32 bytes é persistido.

Gravações profissionais revalidam a conta depois de obter o bloqueio e mantêm o bloqueio da conta até concluir. Se a revogação confirmar primeiro, a alteração que aguardava é recusada. Se a gravação obtiver os bloqueios primeiro, poderá concluir antes da revogação; o histórico registra o resultado. Consultas já autorizadas e em andamento podem terminar; a revogação é observada na próxima requisição.

Senhas usam BCrypt; cookies de sessão permanecem HttpOnly/SameSite, com CSRF e no-store. Respostas antigas são abortadas na troca de data, saída ou perda de conexão. Segredos não são gravados no armazenamento offline do navegador. O convite usa o fragmento da URL e a interface o remove da barra ao abrir; compartilhar o link fornece acesso à ativação e deve ser feito privadamente.

## Recuperação e demonstração
Reaproveitada a recuperação de senha existente. Na ativação em modo demo, o navegador recebe acesso à caixa privada de e-mails de teste da nova conta, sem envio real. Esqueci minha senha permite solicitar a mensagem enquanto essa caixa estiver válida. Após entrar, é possível reabri-la pelo fluxo existente. Perder a senha e o acesso à caixa de teste não comprova identidade: o responsável pode revogar a conta e gerar novo convite para o mesmo e-mail.

Links simulados não provam titularidade real do endereço. Operação com e-mails reais depende do remetente/provedor configurado, conforme EMAIL-ACESSO.md; nenhum serviço de envio foi contratado ou ativado nesta entrega.

## Atualização, local e backups
V13 acrescenta função, vínculo e estado da conta, convites e o novo ator do histórico. Contas antigas permanecem OWNER, habilitadas, sem vínculo profissional; senhas e versões de credenciais são preservadas. Profissionais existentes não ganham login automaticamente.

Mesmo código no site, PWA e pacote local. Site/PWA compartilham o banco online; a instalação local é independente e seus convites apontam para a própria origem. Um endereço localhost não funciona no celular de outra pessoa: utilize o site HTTPS para acesso compartilhado.

Arquivos desta instalação em D:/Projetos/AgendaClara e entregas em D:/Projetos/Entregas. Sem dependência nova, extensão obrigatória, contratação ou tarefa recorrente. Mantenha as cotas Free dos provedores.

Faça backup antes de atualizar, encerre a instalação anterior e preserve data e mail-outbox.key. Ferramentas 0.15.0 aceitam esquemas 6 a 13 e mantêm ACBK0001. Restauração preserva contas, estado de acesso e digest/validade dos convites; restaurar um backup também pode recuperar acessos/convites que eram válidos naquele momento. Confira e revogue acessos necessários após uma recuperação. Convites vencidos continuam vencidos pelo relógio atual.
