# Confirmação de e-mail e recuperação de senha — 0.6.1

## Testar sem serviço de e-mail
A demonstração usa uma **caixa de e-mails de teste privada**. Nenhuma mensagem é enviada a um endereço real. Isso permite avaliar web, celular/PWA e pacote local sem criar conta em um provedor.

1. Cadastre uma empresa com e-mail fictício e senha exclusiva para testes.
2. Na confirmação do cadastro, abra a mensagem na Caixa de e-mails de teste.
3. Escolha Abrir confirmação de e-mail e, na próxima tela, Confirmar este e-mail.
4. Entre no painel, configure o atendimento e publique a página de reservas.
5. Para recuperar a senha, saia do painel e escolha Esqueci minha senha.
6. Informe o e-mail usado no cadastro, solicite o link e abra a mensagem de recuperação na mesma caixa.
7. Escolha e confirme uma nova senha. Depois, entre novamente. A senha anterior deixa de funcionar.

A caixa está vinculada à conta criada neste navegador por um cookie próprio HttpOnly/SameSite=Strict, válido por uma hora. Esse cookie continua disponível depois de sair do painel. Não basta informar um e-mail para ver mensagens de outra pessoa.

Se a caixa expirar, entre na conta para reabri-la e solicite outro link. Se perdeu a senha e também o acesso à caixa de teste, o modo simulado não comprova a identidade: use outra conta fictícia ou configure envio real para recuperação efetiva. Ao criar outra conta no mesmo navegador, a caixa passa a corresponder à nova conta. Contas compartilhadas @demo.agendaclara.dev não recebem links nem podem trocar a senha fixa por recuperação.

As mensagens são guardadas criptografadas no banco até sua validade; a caixa apresenta até cinco mensagens recentes. Reiniciar a aplicação preserva a caixa quando banco e chave original são mantidos. O banco guarda apenas o digest do cookie, nunca seu valor original. Consulte [FILA-EMAILS.md](FILA-EMAILS.md).

## O que a confirmação libera
Novas contas podem entrar e preparar serviços, equipe e horários antes da confirmação. Publicar exige a configuração mínima e a confirmação do e-mail.

Contas anteriores à versão 0.5.0 preservam o acesso e a regra de publicação anterior durante a transição. A migração não declara que seus e-mails foram verificados. Antes de operação comercial, o responsável deve concluir a confirmação dessas contas legadas.

Confirmação simulada e confirmação real usam campos separados. Ao habilitar envio real, confirmações e links de demonstração não provam posse do e-mail. Páginas de contas novas que dependem apenas da confirmação simulada deixam de ser públicas até a confirmação real. O painel identifica uma confirmação simulada como teste.

## Segurança dos links
- Segredo aleatório de 32 bytes, gerado com SecureRandom, codificado em 43 caracteres URL-safe.
- A tabela de tokens guarda somente SHA-256 do segredo, finalidade, usuário, validade e consumo. A cópia do link necessária ao envio fica criptografada na outbox; ela também existe na mensagem e no navegador que abre o link.
- Confirmação: 24 horas. Recuperação: 15 minutos. Links são de uso único e têm finalidade e modo de entrega vinculados.
- Reenvio após o intervalo de um minuto invalida o link anterior da mesma finalidade.
- Abrir o endereço não consome o link. A alteração exige ação explícita por POST e CSRF.
- O segredo fica no fragmento da URL, não é enviado em parâmetros HTTP ao servidor e é removido da barra de endereço pela interface. Referrer-Policy é no-referrer.
- URLs públicas são montadas a partir de APP_BASE_URL, validada na inicialização. Não se usa Host fornecido por visitantes. Sem URL fixa, apenas a demonstração local deriva um endereço de loopback e a porta local.
- A recuperação retorna o mesmo status 202 e mensagem para conta existente, inexistente, compartilhada ou indisponível. O envio ao provedor não bloqueia essa resposta. Isso reduz exposição de existência da conta; não é uma garantia de tempo constante para consultas ao banco.
- Há cota global de ações de acesso por processo, limite de corpo de 16 KiB e intervalo de um minuto por conta/finalidade. Solicitar recuperação não bloqueia a conta nem muda a senha.
- A nova senha segue a mesma política do cadastro: mínimo de 15 caracteres Unicode, máximo de 72 bytes UTF-8, BCrypt e sem truncamento silencioso.
- A troca incrementa credentials_version. Cada chamada autenticada à API compara essa versão com o banco: sessões antigas são recusadas e invalidadas na próxima chamada, inclusive em processos diferentes conectados ao mesmo banco.
- A recuperação também confirma o endereço no modo de entrega correspondente, invalida outros links da conta e gera uma notificação de troca sem incluir a senha. Não autentica automaticamente.

O bloqueio do usuário no banco serializa emissão e consumo. Duas tentativas simultâneas com o mesmo link têm um único sucesso. A mensagem protegida é gravada na mesma transação do link, antes do commit; o envio externo só começa após a confirmação da transação.

## Preparar envio real
Foi implementado um adaptador HTTP para a API do Resend. Não foi criada conta no provedor, contratado plano nem configurada credencial real nesta entrega. O envio externo ainda precisa de uma validação de entrega com remetente autorizado.

O adaptador utiliza HTTPS, destino fixo api.resend.com/emails, tempo máximo de conexão/requisição e Idempotency-Key estável por mensagem. Não é necessário instalar biblioteca ou extensão adicional. HTTP evita depender das portas SMTP bloqueadas na hospedagem gratuita do Render.

No Render, abra o serviço, vá a Environment e configure os valores diretamente nos campos privados:

| Variável | Valor para envio real |
| --- | --- |
| MAIL_MODE | resend |
| APP_BASE_URL | origem HTTPS da instalação, por exemplo https://agenda-clara-luizcodebr.onrender.com |
| MAIL_FROM | remetente autorizado no provedor |
| RESEND_API_KEY | chave privada com permissão de envio; não colocar no Git ou em mensagens |
| MAIL_ENCRYPTION_KEY | 32 bytes aleatórios em Base64, preservados como segredo da instalação; não colocar no Git |
| COOKIE_SECURE | true em HTTPS |
| SPRING_PROFILES_ACTIVE | postgres para operação com banco persistente próprio |
| APP_DEMO_ENABLED | false em uma instalação real nova |
| APP_PUBLIC_DEMO | false somente quando a instalação tiver operação real preparada |

MAIL_FROM pode ser um endereço ou Nome <endereco@dominio>. APP_BASE_URL não aceita credenciais, caminho, parâmetros nem fragmento. HTTP é permitido apenas para loopback local. O modo resend não inicia sem chave, remetente e URL pública válidos. Não troque a demonstração pública para dados reais sem preparar persistência, backups e gestão das contas.

Depois de configurar o provedor e seu domínio/remetente, valide recebimento, spam, confirmação, recuperação e aviso de senha alterada com endereços autorizados. Conferir que a API aceitou uma mensagem não garante entrega à caixa de entrada. Consulte as instruções atuais do provedor antes de configurar DNS.

## Entrega e falhas
O envio externo usa outbox persistente no banco, com conteúdo criptografado e processamento em segundo plano. São até três tentativas por ciclo, com esperas de 30 e 60 segundos ou intervalo maior solicitado pelo provedor. Uma falha elegível permite um ciclo manual adicional. Repetições preservam identificador e conteúdo. Conteúdo, links, destinatários e credenciais não são registrados nos logs.

A fila recupera pendências após interrupções quando banco e chave original são preservados. O painel E-mails e envios mostra histórico e falhas somente da própria empresa. O prazo de envio respeita a validade do link e fica limitado a 23 horas para permanecer dentro da janela de deduplicação do provedor. Aceitação não comprova entrega. Não expomos uma caixa de pré-visualização no modo resend. Consulte [FILA-EMAILS.md](FILA-EMAILS.md) para estados, monitoramento e backup.

## Configuração por ambiente
| Variável | Padrão |
| --- | --- |
| MAIL_MODE | disabled no perfil normal; demo no perfil demo |
| APP_BASE_URL | vazio na demonstração local; obrigatório para modo público/envio real |
| ACCESS_REQUESTS_PER_MINUTE | 20 ações por processo |
| REGISTRATION_REQUESTS_PER_MINUTE | 5 cadastros por processo |

O modo demo exige APP_DEMO_ENABLED=true. O Compose local já configura essa combinação. O perfil postgres sem demonstração deixa o envio disabled até a configuração explícita. Os testes automatizados usam caixa simulada e dados fictícios; nenhum e-mail real é disparado pelos roteiros.

O ambiente gratuito online usa PostgreSQL persistente no Neon Free e chave estável no Render. Reinícios preservam contas, tokens ainda válidos e mensagens; cotas, validade e recuperação estão em [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md). No pacote local, o banco e a chave ficam em data; mensagens simuladas ainda válidas persistem quando ambos são preservados. A PWA depende do mesmo servidor.

## Referências e validação
- [OWASP: recuperação de senha](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
- [API de envio Resend](https://resend.com/docs/api-reference/emails/send-email).
- [Idempotência Resend](https://resend.com/docs/dashboard/emails/idempotency-keys).
- [Limitações do Render gratuito](https://render.com/docs/free).
- [Validação desta entrega](VALIDACAO-0.6.1.md).
