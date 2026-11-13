# Site online, app mobile e versão local

**Site:** https://agenda-clara-luizcodebr.onrender.com

**Downloads:** https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.6.0

## Três formas de acesso

A tela inicial permite entrar no site, instalar a PWA no aparelho ou abrir as instruções da versão local.

- **Site:** acesso por uma URL HTTPS, sem instalação no computador ou celular.
- **App mobile:** o mesmo site instalado pelo navegador compatível. Usa a mesma API e o mesmo banco do site.
- **Cópia local:** JAR com banco H2 próprio. Não sincroniza automaticamente com a hospedagem.

Não há aplicativo nativo em loja Android/iOS nesta versão. Para instalar no iPhone: Compartilhar → Adicionar à Tela de Início. No Android: opção de instalação do Chrome ou navegador compatível.

## Hospedagem de demonstração no Render

`render.yaml` descreve um único serviço web Docker no **plano free**. Não cria banco ou serviço pago. A aplicação é pública, usa HTTPS fornecido pela hospedagem e mantém a sessão com cookie Secure.

O ambiente usa o perfil demo e `APP_PUBLIC_DEMO=true`. A interface avisa para usar apenas dados fictícios. As contas são compartilhadas para avaliação; não representam acesso individual de clientes reais.

### Limites relevantes

A hospedagem gratuita pode suspender o serviço por inatividade e levar algum tempo para reabrir. Seu armazenamento é temporário: reservas de teste podem desaparecer depois de suspensão, reinício ou nova publicação. Os exemplos são recriados quando o banco está vazio.

Portanto, esta configuração é uma **demonstração pública**, não a configuração de utilização por um negócio com dados reais.

Referência: [limitações oficiais do plano gratuito](https://render.com/docs/free).

## Variáveis da demonstração

| Variável | Uso |
| --- | --- |
| SPRING_PROFILES_ACTIVE=demo | H2 e exemplos fictícios |
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

A API possui configuração para PostgreSQL. Antes de um negócio usar dados reais, a aplicação precisa configurar e validar o envio real de e-mails, armazenamento durável e backups da fila, comunicação ao cliente e operação com persistência e proteção contra abuso. Confirmação e recuperação estão disponíveis para teste desde 0.5.0. O cadastro próprio para avaliação está disponível desde 0.4.0; na hospedagem gratuita os cadastros são temporários.

A hospedagem e o banco persistente devem ser escolhidos conforme os limites e custos reais do provedor, sem converter automaticamente esta demonstração em um serviço pago.

## Equipe e horários (0.2.0)
O painel permite cadastrar profissionais e configurar a semana pelo site, celular ou PWA. As alterações são feitas online e entram na disponibilidade pública após salvar. O download local contém o mesmo painel. Consulte [PROFISSIONAIS.md](PROFISSIONAIS.md).

## Proteções de uso (0.2.1)

Web, PWA instalada e cópia local usam o mesmo código de proteção da API. Repetições muito rápidas podem retornar uma orientação para aguardar; a interface não confirma nem reenvia a alteração automaticamente. Falha de conexão durante a preparação da operação também recebe uma mensagem clara. Atualize/reabra a página para carregar a interface publicada mais recente.

## Empresas e contas (0.5.0)
O cadastro e a publicação funcionam pela mesma interface no site, celular/PWA e instalação local. Consulte [CADASTRO-EMPRESAS.md](CADASTRO-EMPRESAS.md) para o fluxo, persistência e limites.
## Fila e acompanhamento (0.6.0)
A aba E-mails e envios está disponível no site, PWA e pacote local. Usa o mesmo servidor, com atualização enquanto a tela está visível e conectada. Não há armazenamento do histórico privado no service worker. O pacote local mantém banco e chave em data; a hospedagem gratuita continua efêmera. Consulte [FILA-EMAILS.md](FILA-EMAILS.md).
