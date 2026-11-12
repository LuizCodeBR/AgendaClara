# Agenda Clara

Agendamento para pequenos negócios com página pública de reservas, painel do responsável e interface instalável pelo navegador.

**Versão 0.2.2 — revisão de uso e confiabilidade.** O responsável pode cadastrar e editar profissionais e configurar o expediente semanal. A demonstração usa dois estabelecimentos fictícios; cadastro público de empresas continua planejado.

**Acessos:** [Abrir o site](https://agenda-clara-luizcodebr.onrender.com) · [Baixar a demonstração](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.2.2) · [Testes automáticos](https://github.com/LuizCodeBR/AgendaClara/actions)

## Site, app mobile e versão local

A tela inicial apresenta as três opções. O site hospedado e a PWA usam o mesmo servidor; a cópia local tem um banco próprio. A publicação gratuita é uma demonstração com dados descartáveis. Consulte [WEB-MOBILE.md](docs/WEB-MOBILE.md).

## Experimentar em poucos passos

### Windows — código-fonte

Pré-requisitos: **JDK 21 a 27** e **Node.js 24.18+ da linha 24, ou 26+**. Recomendação para novos ambientes: JDK 21 ou 25 LTS e Node 24 LTS. Git é útil para clonar, mas não é obrigatório para um ZIP.

1. Baixe e extraia o código-fonte em uma pasta de sua escolha.
2. Abra essa pasta no terminal.
3. Execute:

```powershell
.\iniciar.cmd
```

Na primeira execução, o comando baixa as dependências, executa os testes, compila a interface e a API e inicia a aplicação.

Abra **http://localhost:8080**. Mantenha o terminal aberto. Use **Ctrl+C** para encerrar.

Depois de uma compilação, para iniciar rapidamente sem reconstruir:

```powershell
.\iniciar.cmd -SkipBuild
```

Se a porta estiver ocupada:

```powershell
.\iniciar.cmd -SkipBuild -Port 8081
```

O comando Windows usa PowerShell com liberação de execução restrita a esse processo. Não altera a política permanente do computador.

### Linux e macOS — código-fonte

Com os mesmos requisitos de Java e Node:

```bash
chmod +x mvnw iniciar.sh scripts/build.sh
./iniciar.sh
# Em execuções seguintes:
./iniciar.sh --skip-build
```

As dependências precisam de internet no primeiro download. Depois de compilado, o JAR usa arquivos locais e não precisa de um serviço externo. A aplicação e o banco precisam estar em execução.

### Demonstração compilada — somente Java

O pacote de demonstração contém `agenda-clara.jar`, instruções e iniciadores. Ele exige **Java 21 a 27**, sem Node, Maven, Docker ou banco instalado.

Extraia-o em uma pasta gravável e execute `iniciar-demo.cmd` no Windows, ou `bash iniciar-demo.sh` no Linux/macOS.

Também é possível iniciar diretamente:

```bash
java -jar agenda-clara.jar --spring.profiles.active=demo
```

## Usar como aplicativo

Abra a demonstração no **Chrome ou Edge**. Use **Instalar app**, ou a opção de instalar o site no menu do navegador. O aplicativo abre em uma janela própria.

Essa é uma **PWA**, e não um executável nativo com servidor embutido. O servidor Java continua necessário. A instalação não instala Java nem inicia o servidor automaticamente.

Em outro aparelho, `localhost` aponta para esse próprio aparelho, não para o computador que iniciou o servidor. Para acesso compartilhado e instalação no celular, use o site publicado com **HTTPS**. A execução local escuta somente no computador local. A versão hospedada usa uma URL HTTPS própria.

Quando o servidor fica indisponível, a tela de reconexão é exibida. Disponibilidade, dados de clientes e alterações de reserva não são armazenados nem enviados posteriormente pelo service worker.

## Contas fictícias

Na opção **Meu negócio**, clique em uma conta para preencher o acesso.

| Estabelecimento | E-mail | Senha |
| --- | --- | --- |
| Barbearia Horizonte | marina@demo.agendaclara.dev | Demo@12345 |
| Estúdio Aurora | rafael@demo.agendaclara.dev | Demo@12345 |

Todos os dados são exemplos. O perfil de demonstração não deve ser usado para receber dados de clientes reais. Nesta etapa, todos os profissionais do estabelecimento podem realizar todos os serviços do catálogo.

## O que funciona

- Seleção de estabelecimento, serviço, profissional, data e horário.
- Reservas com preço e duração calculados no servidor.
- Expediente de demonstração: segunda a sábado, 09h–12h e 13h–18h, em Brasília.
- Grade de início a cada 15 minutos e horizonte de 60 dias.
- Impedimento de sobreposição, inclusive entre horários de início diferentes.
- Repetição segura de uma mesma confirmação após falha de conexão.
- Login, logout e painel exclusivo do estabelecimento.
- Agenda diária, filtro de status, cancelamento e remarcação.
- Bloqueio e liberação de períodos.
- Inclusão de novos serviços no catálogo.
- Cadastro, edição, ativação e inativação de profissionais.
- Expediente semanal por profissional, com intervalos, dias fechados e proteção das reservas existentes. Consulte [PROFISSIONAIS.md](docs/PROFISSIONAIS.md).
- Interface responsiva, manifesto de instalação e tela de reconexão.
- Banco local persistente e migração versionada do esquema.

Na primeira inicialização são criados exemplos de atendimentos no próximo dia útil da demonstração. Esses exemplos não são recriados a cada reinício. As reservas reais que você fizer na demonstração permanecem no banco local.

## Arquitetura e tecnologias

- **Java 21 / Spring Boot 4.1.2**: API e servidor da aplicação.
- **Spring Security**: sessão protegida por cookie HttpOnly, senha BCrypt e CSRF.
- **Spring JDBC**: consultas explícitas e transações com bloqueio por profissional.
- **Flyway**: criação e evolução do banco.
- **H2 em arquivo**: demonstração sem instalação de banco.
- **PostgreSQL**: configuração alternativa e matriz de testes preparada.
- **React 19 / TypeScript / Vite**: interface.
- **JUnit, Spring Test, Vitest e Testing Library**: testes.
- **Playwright**: cenários preparados para desktop e celular no fluxo automático.

O React compilado é servido pelo próprio Java. Para experimentar, basta um servidor e uma URL. Consulte [a arquitetura](docs/ARQUITETURA.md) e [o contrato da API](docs/API.md).

## Arquivos e armazenamento

```text
backend/          API, regras, banco e testes Java
frontend/         interface React, PWA e testes da interface
scripts/          execução, compilação e verificação de entrega
docs/             arquitetura e contrato da API
.github/          fluxo de verificações para GitHub Actions
data/             banco local da demonstração (ignorado pelo Git)
.cache/           dependências e temporários locais (ignorados pelo Git)
target/           JAR e relatórios Java (ignorados pelo Git)
```

Os iniciadores colocam dependências e arquivos temporários dentro do projeto. Se a pasta estiver no D:, esses arquivos também ficam no D:. Configurações normais do sistema operacional, do navegador e do editor seguem os locais definidos pelos próprios aplicativos.

## Desenvolvimento e testes

Windows, na raiz do projeto:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

Linux/macOS:

```bash
bash scripts/build.sh
```

Para verificar um servidor já iniciado:

```bash
node scripts/smoke.mjs http://127.0.0.1:8080
```

A verificação usa dados fictícios: cria uma reserva, repete a confirmação, entra no painel e cancela a reserva de teste.

Durante o desenvolvimento, é possível executar a API com o Maven Wrapper e a interface com `npm run dev`, dentro de `frontend`. O Vite encaminha `/api` para a porta 8080. O service worker é registrado somente na compilação de entrega.

Testes de navegador, opcionais no computador do desenvolvedor, com a aplicação compilada em execução:

```bash
cd frontend
npm exec -- playwright install chromium
npm run test:e2e
```

O comando de instalação acima baixa um navegador de testes. Os testes comuns não precisam desse download. Para mantê-lo no D:, configure `PLAYWRIGHT_BROWSERS_PATH` apontando para `.cache/browsers` da sua pasta do projeto antes de instalar.

Os testes Java usam banco separado e relógio fixo. Nunca apontar `TEST_DB_URL` para um banco com dados que devam ser preservados: os testes limpam a agenda desse banco.

## PostgreSQL e Docker — opcionais

Docker não é necessário para a demonstração local. Com Docker Engine/Desktop e Compose disponíveis:

```bash
docker compose up --build
```

Abra a mesma URL. O Compose mantém o PostgreSQL sem porta pública e publica a aplicação somente em `127.0.0.1:8080`. A senha presente no Compose é um exemplo local. Os dados ficam em um volume.

Para encerrar preservando os dados:

```bash
docker compose down
```

Para usar um PostgreSQL próprio, configure `SPRING_PROFILES_ACTIVE=postgres`, `DB_URL` (JDBC), `DB_USER` e `DB_PASSWORD`. Os exemplos só são criados quando `APP_DEMO_ENABLED=true`. Sem esse sinalizador, não há contas ou empresas automáticas; o cadastro inicial de produção é uma etapa futura.

## Próximas etapas

1. Exceções de expediente por data, feriados e ausências.
2. Cadastro público de empresas e provisionamento de contas próprias.
3. Recuperação de acesso e comunicação real de confirmação/cancelamento.
4. Persistência operacional e ampliação da proteção contra abuso antes de receber clientes reais.

A revisão atual está em [REVISAO-USO-0.2.2.md](docs/REVISAO-USO-0.2.2.md), com resultados em [VALIDACAO-0.2.2.md](docs/VALIDACAO-0.2.2.md). A revisão de segurança anterior está em [REVISAO-SEGURANCA-0.2.1.md](docs/REVISAO-SEGURANCA-0.2.1.md). Históricos: [0.2.0](docs/VALIDACAO-0.2.0.md) e [primeira versão](docs/VALIDACAO.md).
