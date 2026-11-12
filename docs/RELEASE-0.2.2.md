# Agenda Clara 0.2.2 — revisão de uso e confiabilidade

Corrige a exibição da agenda após falha ao trocar a data, links inválidos ou de empresas inexistentes, fechamento de janelas durante gravações, perda do rascunho de expediente pela navegação e a data de atendimentos noturnos no comprovante e na remarcação.

Inclui 30 testes da interface e 10 cenários preparados para navegador desktop/celular, além dos 77 casos Java existentes e da matriz H2/PostgreSQL. Consulte Actions e docs/VALIDACAO-0.2.2.md para evidências.

- Site/PWA: https://agenda-clara-luizcodebr.onrender.com
- AgendaClara-0.2.2-demo.zip: aplicativo compilado; exige apenas Java 21 a 27.
- AgendaClara-0.2.2-fonte.zip: projeto completo; exige JDK e Node conforme o README.
- SHA256SUMS.txt: somas de verificação.

Extraia o pacote em pasta gravável e execute iniciar-demo.cmd no Windows, ou bash iniciar-demo.sh no Linux/macOS. Acesse http://localhost:8080 e mantenha o servidor aberto.

A hospedagem gratuita continua sendo uma demonstração com contas fictícias compartilhadas e dados descartáveis. Use somente dados de teste. A cópia local tem banco próprio em arquivo.
