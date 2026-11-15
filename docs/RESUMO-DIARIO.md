# Resumo diário por profissional — 0.13.1

## Usar no site, celular/PWA ou pacote local

1. Entre em Meu negócio com a conta responsável e abra Resumo diário.
2. Selecione o profissional e a data. O padrão é hoje no fuso do estabelecimento.
3. Confira a Prévia da mensagem. Ela contém nome da empresa e do profissional, data, fuso, horário de início, nome do cliente, serviço e momento da geração.
4. Para abrir uma conversa direcionada, informe o Telefone com DDI e salve. Exemplo de formato: +55 11 99999-1234. Confirme o número real com o profissional; a aplicação valida o formato, não a existência da conta nem a titularidade.
5. Marque Conferi a prévia e o destinatário. A aplicação consulta novamente o servidor. Se a agenda ou o contato mudou, confira a nova prévia e marque novamente. Aguarde a conclusão antes de copiar/abrir.
6. Escolha Copiar resumo ou Abrir WhatsApp para conferir. No WhatsApp, confira a conversa e confirme o envio. A Agenda Clara não envia sozinha e não comprova envio, entrega ou leitura.
7. Depois de alterações na agenda, use Atualizar resumo e confira novamente.

O contato pode ficar vazio: nesse caso, copie o texto e escolha a conversa manualmente. Salvar vazio remove o número. O contato é privado ao responsável da empresa e não cria login para o profissional. Nesta versão ele é editado na própria tela Resumo diário.

## Conteúdo e atualização

Somente reservas em BOOKED, pelo início na data escolhida e no fuso da empresa. Cancelados, concluídos e faltas ficam fora. Reservas ainda confirmadas com horário passado continuam incluídas: o resumo não inventa classificação. Remarcação aparece no novo dia/horário após atualizar. Os nomes de serviço vêm do registro da reserva. Profissional inativo pode ser consultado para datas anteriores.

Um dia sem reservas exibe Nenhum atendimento confirmado. A consulta aceita até 365 dias antes/depois de hoje, com limite de 200 atendimentos por profissional/dia. Excedente provoca mensagem explícita, sem truncar silenciosamente.

A mensagem é uma cópia do momento consultado. Não acompanha mudanças depois do compartilhamento. O limite de cinco minutos começa no início da consulta, incluindo o tempo de espera. Também é necessário atualizar ao retornar de outra aba/aplicativo ou após perder conexão. Ajustes do relógio para trás não prolongam a validade. Esse prazo melhora a conferência; não bloqueia outras pessoas de alterarem a agenda durante a leitura.

Resumos longos são divididos entre atendimentos em partes numeradas, mantendo cada URL codificada em até 6000 caracteres. Isso é um limite conservador do aplicativo; a compatibilidade final também depende do navegador e do WhatsApp. Confira e compartilhe cada parte. Cópia indisponível seleciona o texto para copiar pelo dispositivo.

## Privacidade e segurança

A empresa vem da sessão autenticada; resumo e contato usam rotas administrativas. Não há link público do resumo nem inclusão do telefone do cliente, preço, chave privada, referência da reserva ou justificativa de correção no conteúdo. O texto e o contato não são gravados em armazenamento offline do navegador.

A prévia usa texto simples; controles invisíveis e quebras de linha dos nomes são normalizados para impedir linhas falsas. O link usa destino fixo https://wa.me, telefone composto por dígitos e texto codificado. A abertura usa nova aba com proteção contra acesso à janela de origem e sem Referer.

Ao abrir o WhatsApp, o número e o conteúdo escolhido são transferidos ao serviço externo na URL. O endereço pode permanecer no histórico do navegador. Revise os dados antes dessa ação e use informações fictícias na demonstração. Copiar o texto também o coloca na área de transferência do dispositivo.

Alterar o contato exige CSRF, versão atual e bloqueio do profissional na transação. Duas edições concorrentes não sobrescrevem uma à outra; a versão é compartilhada com equipe/horários. Consultas usam leitura consistente e cancelam respostas antigas na interface. Não são registradas alegações de envio a partir de um clique.

## Atualização e backups

Migração aditiva V11 acrescenta whatsapp_phone, vazio para profissionais existentes; não inventa número nem altera reservas, contas, serviços ou histórico. As ferramentas 0.13.0 aceitam esquemas 6 a 11, com ACBK0001. Backup inclui o contato; restauração preserva-o junto aos dados e à chave original.

Faça backup antes de atualizar. No local, encerre normalmente o servidor e preserve a mesma pasta de dados/chave; não execute ferramentas antigas em banco V11. A cópia local continua independente do banco online.

Nenhuma dependência, extensão, API paga, envio automático ou tarefa recorrente foi acrescentada. Projeto e entregas desta instalação ficam no D:. O WhatsApp precisa estar disponível no aparelho/navegador para abrir a conversa; copiar o texto funciona como alternativa.

Referência: [links oficiais do WhatsApp](https://faq.whatsapp.com/5913398998672934).

A revisão 0.13.1 preserva V11. Após salvar contato, recarregamos o resumo completo. Se a gravação falhar, o servidor pode ter recebido a alteração: use Atualizar e descartar edição do contato para consultar o cadastro antes de tentar novamente. Consulte [a revisão](REVISAO-RESUMO-0.13.1.md).
