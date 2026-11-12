# Agenda Clara 0.3.0 — folgas e horários especiais

O responsável pode fechar uma data ou definir até quatro intervalos especiais por profissional, incluindo abertura em dias normalmente fechados. A página pública aplica a exceção à consulta, confirmação e remarcação de reservas.

Salvar, remover ou editar a semana preserva os atendimentos confirmados. A proteção inclui reservas simultâneas e edições desatualizadas. A interface preserva o rascunho em erros e mostra o expediente semanal antes da remoção.

A migração V3 acrescenta as tabelas necessárias, com teste de atualização de um banco existente. O H2 foi atualizado para 2.5.252 para corrigir um defeito reproduzido na validação de registros após troca de conexões. Consulte docs/EXCECOES.md e docs/VALIDACAO-0.3.0.md.

- Site/PWA: https://agenda-clara-luizcodebr.onrender.com
- AgendaClara-0.3.0-demo.zip: aplicativo compilado; exige apenas Java 21 a 27.
- AgendaClara-0.3.0-fonte.zip: código completo; exige JDK e Node conforme o README.
- SHA256SUMS.txt: somas de verificação.

Extraia em pasta gravável e execute iniciar-demo.cmd (Windows) ou bash iniciar-demo.sh (Linux/macOS). Acesse http://localhost:8080 e mantenha o servidor aberto.

A hospedagem gratuita continua uma demonstração com contas fictícias compartilhadas e dados descartáveis. Use somente dados de teste. O pacote local usa seu próprio banco em arquivo. Os feriados são configurados manualmente por profissional e data.
