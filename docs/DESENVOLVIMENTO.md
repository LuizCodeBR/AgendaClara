# Desenvolvimento e execução — Agenda Clara

O [README](../README.md) contém o caminho rápido para site, pacote Java e código-fonte. Esta página concentra opções de desenvolvimento.

## Compilação e testes

Java 21 a 27 e Node 24.18+ da linha 24, ou 26+. Maven Wrapper incluído. A matriz automática verifica Java 21/26 com H2/PostgreSQL.

Windows, na raiz:

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
~~~

Linux/macOS:

~~~bash
bash scripts/build.sh
~~~

Os comandos verificam a interface, compilam React/TypeScript e executam testes Java. A interface compilada entra no JAR. Na primeira execução há downloads pela internet.

Para atualização automática da interface, execute a API com o Maven Wrapper e, em outro terminal, npm run dev dentro de frontend. O Vite encaminha /api para 8080. O service worker é registrado apenas na compilação de entrega. Os iniciadores configuram o perfil demo para banco local e exemplos.

## Banco de testes

Os testes Java usam banco e relógio próprios. Use TEST_DB_URL somente para banco descartável: a suíte limpa dados desse banco. PostgreSQL é verificado pela CI em serviço isolado. A aplicação local usa data/; não aponte o banco de testes para ela.

## Navegador

Com a aplicação compilada e iniciada em outro terminal, no Windows:

~~~powershell
$env:PLAYWRIGHT_BROWSERS_PATH = 'D:\Projetos\AgendaClara\.cache\browsers'
$env:TEMP = 'D:\Projetos\AgendaClara\.cache\tmp'
$env:TMP = $env:TEMP
Set-Location D:\Projetos\AgendaClara\frontend
npm exec -- playwright install chromium
npm run test:e2e
~~~

Ajuste os caminhos à sua pasta. O navegador precisa ser baixado uma vez. Para outra origem local, defina E2E_BASE_URL, por exemplo http://127.0.0.1:8081. Execute a suíte completa em demonstração descartável e com os limites de requisições da [CI](../.github/workflows/ci.yml); usar repetidamente os limites normais pode retornar 429. Preserve os limites publicados.

## Rotinas HTTP

Na raiz, com a aplicação em execução:

~~~bash
node scripts/smoke.mjs http://127.0.0.1:8080
node scripts/professional-access-smoke.mjs http://127.0.0.1:8080
node scripts/reports-smoke.mjs http://127.0.0.1:8080
~~~

As rotinas criam/alteram exemplos e fazem sua limpeza ao concluir. Use demonstração separada. Provas de persistência/backup nos fluxos de [CI](../.github/workflows/ci.yml) e [release](../.github/workflows/release.yml).

## Docker e PostgreSQL opcionais

Com Docker Engine/Desktop e Compose:

~~~bash
docker compose up --build
# Encerrar preservando os dados:
docker compose down
~~~

Acesse http://localhost:8080. O Compose publica a aplicação somente em 127.0.0.1 e mantém o banco sem porta pública. A senha é fictícia. Banco/chave usam volumes postgres-data e mail-data; configure o armazenamento do Docker no D: se optar por essa execução.

PostgreSQL próprio: SPRING_PROFILES_ACTIVE=postgres, DB_URL (JDBC), DB_USER e DB_PASSWORD. APP_DEMO_ENABLED=true autoriza exemplos. Sem ele, não há contas ou empresas automáticas. A primeira conta pode ser criada na interface quando REGISTRATION_ENABLED=true. Consulte [Cadastro](CADASTRO-EMPRESAS.md) e [Persistência](PERSISTENCIA-GRATUITA.md) para adaptar a instalação.

## Distribuição e arquivos

- backend/: aplicação e testes Java.
- frontend/: React, PWA e testes da interface/navegador.
- scripts/: execução, compilação, rotinas e empacotamento.
- docs/: decisões, guias, capturas e validações.
- data/: banco local e chave da fila; ignorados pelo Git.
- .cache/: dependências, temporários e evidências locais; ignorados pelo Git.
- target/: JAR e relatórios Java; ignorados pelo Git.

Os iniciadores mantêm caches na pasta do projeto. O pacote oficial é gerado pelo fluxo de release. O fechamento de documentação posterior à tag está no main; os ZIPs da 0.15.1 preservam a entrega original.

A [reprodução das capturas](imagens/README.md) é opcional e não integra os testes de produto.
