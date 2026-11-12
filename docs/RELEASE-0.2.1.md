# Agenda Clara 0.2.1 — revisão de segurança e arquitetura

Proteções de frequência para API, login, reservas e alterações administrativas; limite de 16 KiB para JSON e formulários; remoção do hash de senha da sessão após login.

Consultas diretas ao banco, listagem da equipe em duas consultas e leituras transacionais da agenda. A interface web e PWA apresenta mensagens de limite e de conexão também durante a preparação de alterações.

Inclui 77 casos Java e 19 testes da interface, mais a matriz de navegadores e bancos do GitHub Actions. Consulte docs/REVISAO-SEGURANCA-0.2.1.md e a página de Actions para o resultado da execução.

- **Site/PWA:** https://agenda-clara-luizcodebr.onrender.com
- **AgendaClara-0.2.1-demo.zip:** aplicativo compilado; exige apenas Java 21 a 27.
- **AgendaClara-0.2.1-fonte.zip:** projeto completo; exige JDK e Node conforme o README.
- **SHA256SUMS.txt:** somas de verificação dos downloads.

Extraia a demonstração em pasta gravável e execute iniciar-demo.cmd (Windows) ou bash iniciar-demo.sh (Linux/macOS). Abra http://localhost:8080 e mantenha o servidor aberto.

As contas são fictícias e compartilhadas. Use somente dados de teste. A hospedagem gratuita não oferece persistência operacional; a cópia local utiliza seu próprio banco em arquivo. As cotas são locais à instância e não substituem proteção de produção.
