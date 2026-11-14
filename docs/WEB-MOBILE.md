# Site online, app mobile e versão local

**Site:** https://agenda-clara-luizcodebr.onrender.com

**Downloads:** https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.11.1

## Três formas de acesso

A tela inicial permite entrar no site, instalar a PWA no aparelho ou abrir as instruções da versão local.

- **Site:** acesso por uma URL HTTPS, sem instalação no computador ou celular.
- **App mobile:** o mesmo site instalado pelo navegador compatível. Usa a mesma API e o mesmo banco do site.
- **Cópia local:** JAR com banco H2 próprio. Não sincroniza automaticamente com a hospedagem.

Não há aplicativo nativo em loja Android/iOS nesta versão. Para instalar no iPhone: Compartilhar → Adicionar à Tela de Início. No Android: opção de instalação do Chrome ou navegador compatível.

## Hospedagem de demonstração no Render

`render.yaml` descreve um único serviço web Docker no **plano free**. Não cria banco ou serviço pago. A aplicação é pública, usa HTTPS fornecido pela hospedagem e mantém a sessão com cookie Secure.

O ambiente online usa o perfil postgres, STORAGE_MODE=persistent, APP_DEMO_ENABLED=true e `APP_PUBLIC_DEMO=true`. A interface avisa para usar apenas dados fictícios. As contas são compartilhadas para avaliação; não representam acesso individual de clientes reais.

### Limites relevantes

A hospedagem gratuita pode suspender o serviço por inatividade e levar algum tempo para reabrir. O sistema de arquivos do Render é temporário, mas o banco da aplicação está no Neon Free. Contas, reservas e mensagens permanecem após reinício e nova publicação. A chave dos e-mails é um segredo estável configurado no serviço. Consulte [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md) para cotas e recuperação.

Portanto, esta configuração é uma **demonstração pública**, não a configuração de utilização por um negócio com dados reais.

Referência: [limitações oficiais do plano gratuito](https://render.com/docs/free).

## Variáveis da demonstração

| Variável | Uso |
| --- | --- |
| SPRING_PROFILES_ACTIVE=postgres | PostgreSQL externo, acesso privado configurado no serviço |
| APP_DEMO_ENABLED=true | Exemplos fictícios no primeiro banco vazio |
| STORAGE_MODE=persistent | Exige PostgreSQL e chave de e-mail estável |
| DB_URL / DB_USER / DB_PASSWORD | Credenciais privadas; URL JDBC direta com TLS |
| MAIL_ENCRYPTION_KEY | Segredo estável, com cópia protegida fora do banco |
| APP_PUBLIC_DEMO=true | Aviso de demonstração pública |
| SERVER_ADDRESS=0.0.0.0 | Receber conexões da hospedagem |
| PORT | Definida pelo provedor e lida pelo servidor |
| COOKIE_SECURE=true | Cookie somente por HTTPS |
| SERVER_FORWARD_HEADERS_STRATEGY=framework | Reconhecer HTTPS atrás do proxy do provedor |
| SOURCE_URL | Link público para o código e a documentação |
| DEMO_DOWNLOAD_URL | Link para o ZIP compilado, após a publicação da entrega |

Os endereços de distribuição só aparecem como links quando configurados. O projeto não apresenta um botão de download apontando para um arquivo inexistente.

## Publicação e verificação

1. Compilar e executar os testes.
2. Publicar o código e a entrega compilada no repositório.
3. Criar o serviço Docker, confirmando plano free e espaço do usuário.
4. Aguardar a compilação e a implantação terminarem.
5. Verificar página inicial, manifesto e ícones por HTTPS.
6. Testar reserva, login e cancelamento usando apenas dados fictícios.
7. Conferir layout em computador e celular.

Atualizações automáticas estão desligadas na configuração inicial. Uma nova implantação é uma ação explícita.

## Evolução para utilização real

A API possui configuração para PostgreSQL. Antes de um negócio usar dados reais, a aplicação precisa configurar e validar o envio real de e-mails, retenção e recuperação adequadas ao negócio, comunicação ao cliente e operação contínua com proteção contra abuso. Confirmação e recuperação estão disponíveis para teste desde 0.5.0. O cadastro próprio para avaliação está disponível desde 0.4.0; na hospedagem atual os cadastros persistem, mas continuam sendo dados fictícios para avaliação.

A hospedagem e o banco persistente devem ser escolhidos conforme os limites e custos reais do provedor, sem converter automaticamente esta demonstração em um serviço pago.

## Equipe e horários (0.2.0)
O painel permite cadastrar profissionais e configurar a semana pelo site, celular ou PWA. As alterações são feitas online e entram na disponibilidade pública após salvar. O download local contém o mesmo painel. Consulte [PROFISSIONAIS.md](PROFISSIONAIS.md).

## Proteções de uso (0.2.1)

Web, PWA instalada e cópia local usam o mesmo código de proteção da API. Repetições muito rápidas podem retornar uma orientação para aguardar; a interface não confirma nem reenvia a alteração automaticamente. Falha de conexão durante a preparação da operação também recebe uma mensagem clara. Atualize/reabra a página para carregar a interface publicada mais recente.

## Empresas e contas (0.5.0)
O cadastro e a publicação funcionam pela mesma interface no site, celular/PWA e instalação local. Consulte [CADASTRO-EMPRESAS.md](CADASTRO-EMPRESAS.md) para o fluxo, persistência e limites.
## Fila e acompanhamento (0.6.0)
A aba E-mails e envios está disponível no site, PWA e pacote local. Usa o mesmo servidor, com atualização enquanto a tela está visível e conectada. Não há armazenamento do histórico privado no service worker. O pacote local mantém banco e chave em data; a hospedagem usa PostgreSQL persistente no Neon Free. Consulte [FILA-EMAILS.md](FILA-EMAILS.md).

## Revisão 0.6.1
Site, PWA e download local incluem as correções de espera manual, monitor em lotes lentos e chave concorrente. A interface diferencia espera do serviço de tentativa automaticamente agendada. Veja [REVISAO-SEGURANCA-0.6.1.md](REVISAO-SEGURANCA-0.6.1.md).

## Armazenamento e backups (0.7.0)
A API informa o modo operacional local, ephemeral ou persistent. O cadastro web/mobile mantém o aceite de dados fictícios e apresenta o aviso correspondente. A demonstração online usa Neon Free, sem contratação paga. O pacote local inclui backup.cmd e backup.sh. Consulte [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md) e [a configuração gratuita](PERSISTENCIA-GRATUITA.md).

## Rotina sob demanda (0.8.0)
O responsável pela instalação pode criar e verificar uma cópia, preservar o último resultado válido e consultar um relatório privado de limites com operacao.cmd/operacao.sh. Site e PWA seguem usando o mesmo banco persistente; não há nova exportação global por HTTP. Conforme a escolha do usuário, nenhum agendamento foi ativado. Consulte [OPERACAO-BACKUPS-LIMITES.md](OPERACAO-BACKUPS-LIMITES.md).

A versão 0.8.1 reforça as ferramentas do operador sem novos endpoints globais ou mudanças de esquema. Consulte [a revisão](REVISAO-BACKUPS-0.8.1.md).

## Serviços por profissional (0.9.0)

Site, PWA e pacote local compartilham a seleção de Serviços atendidos em Equipe e horários e o filtro de profissionais por serviço. A nova versão requer migração V7; preserve data e a chave ao atualizar localmente e recarregue a página/PWA após a publicação. Consulte [o guia](SERVICOS-PROFISSIONAIS.md).

## Minha reserva (0.10.0)

Site, PWA e pacote local oferecem consulta, cancelamento e remarcação por link privado. O comprovante tem Gerenciar minha reserva; a agenda do responsável permite gerar e revogar acessos. O segredo sai do endereço após abrir e o cookie fica restrito à reserva. A cópia local tem banco independente; compartilhe links do site online para acesso em outro dispositivo. Recarregue a PWA após atualizar. Consulte [Minha reserva](MINHA-RESERVA.md).

## Regras por empresa (0.11.1)
Web, PWA e pacote local incluem Regras de agendamento, prazos na confirmação e ações separadas em Minha reserva. A migração V9 preserva dados anteriores. Recarregue o aplicativo depois de atualizar. Consulte [o guia](REGRAS-AGENDAMENTO.md).
