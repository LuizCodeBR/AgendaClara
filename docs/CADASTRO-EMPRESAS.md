# Empresas e contas próprias — 0.5.0

## Experimentar
1. Abra a página inicial ou Meu negócio e escolha **Cadastrar meu negócio**. Link direto: `/#/cadastro`.
2. Informe nome da empresa, área, local público, fuso, nome do responsável, e-mail e senha.
3. Na demonstração pública, confirme o uso de dados fictícios. Use uma senha exclusiva de teste.
4. Depois de criar a conta, escolha **Entrar no meu painel**. O e-mail vem preenchido; informe a senha novamente.
5. Em Serviços, adicione seu catálogo. Em Equipe e horários, cadastre profissionais e seu expediente.
6. Abra Página de reservas. A lista mostra os itens que faltam. Publique para liberar o link.
7. Copie o endereço mostrado. Uma reserva feita nesse link aparece somente no painel da empresa.
8. Ocultar a página remove a empresa da lista pública e bloqueia novas solicitações públicas. Reservas confirmadas permanecem no painel.

O endereço compartilhado pertence à instalação atual. Um link com localhost/127.0.0.1 só funciona no próprio computador. Para clientes em outros aparelhos, utilize a instalação HTTPS online.

## Regras do cadastro
- Uma empresa e um responsável por conta. O e-mail é único em toda a instalação e é normalizado para minúsculas, sem espaços nas pontas.
- A conta ainda não permite editar e-mail, fuso ou perfil da empresa. Não há convites, múltiplos administradores ou exclusão de conta. Confirmação de e-mail e recuperação de senha foram implementadas em 0.5.0; na demonstração usam uma caixa de teste privada. Consulte EMAIL-ACESSO.md.
- O e-mail é identificador de acesso. A confirmação real exige configurar um provedor; a caixa simulada não comprova acesso a um endereço real.
- Novas empresas começam vazias e privadas; não recebem profissionais, clientes ou agendamentos dos exemplos.
- O servidor gera identificadores e um endereço público único; o usuário não escolhe identificadores de outras empresas.
- Fusos iniciais: Brasília/São Paulo, Manaus, Rio Branco e Fernando de Noronha.
- O local informado será público quando a empresa for publicada. Use somente uma localização que possa ser compartilhada.
- Senha com pelo menos 15 caracteres Unicode e até 72 bytes UTF-8, limite do BCrypt existente. Espaços são preservados; acentos e emojis usam mais bytes. O servidor recusa excesso em vez de truncar.
- Senhas são armazenadas com BCrypt; não são devolvidas, registradas em toString nem persistidas pela aplicação no armazenamento do navegador.
- O domínio @demo.agendaclara.dev é reservado às contas compartilhadas da demonstração.

## Armazenamento e limites de uso
**Render gratuito:** os novos cadastros são contas de teste temporárias. O arquivo local do servidor não é persistente; reinícios e novas implantações podem apagar empresas, contas e reservas. Isso é informado no formulário e na confirmação.

**Pacote local:** o H2 em arquivo preserva contas e agendamentos na pasta data entre reinícios. Não apague essa pasta e faça cópias de segurança com o aplicativo parado. A PWA usa o mesmo servidor, não um banco separado no celular.

**PostgreSQL:** o perfil postgres usa o banco configurado por DB_URL, DB_USER e DB_PASSWORD. Com APP_DEMO_ENABLED=false, não há contas compartilhadas automáticas e é possível criar a primeira conta pela interface. O operador é responsável por persistência, backups, HTTPS, recuperação de acesso e monitoramento.

O cadastro permite avaliar provisionamento e isolamento. Antes de uso comercial com dados reais, faltam ativação/validação do envio real, entrega persistente, gestão do acesso, política de retenção/exclusão, persistência operacional e proteção contra abuso adequada ao tráfego.

## Garantias e arquitetura
A migração V4 acrescenta publicação e versão na empresa, além de uma chave única de cadastro no usuário. Empresas antigas continuam publicadas e suas credenciais são preservadas.

Empresa e usuário são inseridos na mesma transação. Um e-mail já usado ou uma corrida entre requisições desfaz toda a operação perdedora, evitando empresas órfãs.

A interface mantém requestKey ao repetir um formulário sem alterações após falha de rede. O servidor verifica os dados originais e compara a senha com BCrypt. Mesma chave e dados iguais retornam a mesma empresa; dados diferentes retornam conflito. Não se grava uma assinatura rápida da senha. A recuperação de uma colisão acontece depois do rollback, em outra transação, compatível com PostgreSQL.

A página privada não aparece em /api/public/businesses e catálogo, disponibilidade e reserva por seu endereço retornam 404. Consultas administrativas continuam possíveis para o responsável.

Para contas novas, a publicação exige confirmação de e-mail, serviço, profissional ativo e ao menos um expediente semanal ou uma exceção aberta dentro do horizonte atual. Isso verifica configuração, não promete vaga: folgas, bloqueios e reservas continuam compondo a disponibilidade. A configuração pode mudar depois da publicação.

A publicação usa bloqueio da empresa e versão otimista; uma edição desatualizada retorna 409. O usuário autenticado define a empresa, mesmo que a requisição inclua outro businessId. Reservas continuam usando o bloqueio por profissional já existente. Ocultar não desfaz uma solicitação que já estava em andamento quando a página foi ocultada.

CSRF, cookies HttpOnly/SameSite, renovação da sessão no login, limite de corpo de 16 KiB e no-store permanecem. Há cota global de 5 solicitações de cadastro por minuto, independente de cookies e cabeçalhos de IP. A cota é por processo, não uma defesa distribuída nem garantia contra abuso em grande escala.

## Configuração
| Variável | Padrão | Uso |
| --- | --- | --- |
| REGISTRATION_ENABLED | true | Habilita/desabilita novos cadastros; contas existentes continuam funcionando |
| REGISTRATION_REQUESTS_PER_MINUTE | 5 | Cota global de solicitações de cadastro por processo |
| APP_PUBLIC_DEMO | false | Exige aceite de dados fictícios quando true; use true na hospedagem temporária |
| APP_DEMO_ENABLED | depende do perfil | Cria apenas os dois exemplos ausentes, sem impedir cadastro próprio |

Os testes de navegador usam cota 30 por minuto para os roteiros automáticos. O padrão do aplicativo e da hospedagem continua 5.

## Verificações
- JUnit: atomicidade, hash e normalização, entrada real/saída, CSRF, repetição e corridas simultâneas, limites de senha, cadastro desativado/aceite, publicação, versão, migração e isolamento.
- Vitest: formulário, repetição após falha, confirmação de senha/aceite, envio pendente, descarte, publicação e conflito.
- Playwright: criação até reserva e ocultação no desktop e Pixel 7 emulado, além dos fluxos anteriores.
- scripts/registration-smoke.mjs: duas contas próprias, sessões, configuração, reserva, isolamento e ocultação no pacote real.
- CI: Java 21 e 26 com H2 e PostgreSQL 17; aplicação Docker/PostgreSQL e navegadores.

Resultados da entrega em [VALIDACAO-0.4.0.md](VALIDACAO-0.4.0.md).
