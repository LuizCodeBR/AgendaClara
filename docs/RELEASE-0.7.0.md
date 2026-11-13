# Agenda Clara 0.7.0 — armazenamento e backups

- Backup local criptografado reunindo banco H2 e chave original dos e-mails.
- Verificação de senha/integridade e restauração em pasta nova, preservando a instalação original.
- Exportação PostgreSQL com pg_dump e restauração transacional em banco vazio.
- Testes de senha incorreta, adulteração, chave incompatível, banco em uso e recuperação das mesmas contas/mensagens.
- Modo de armazenamento explícito no servidor e aviso de cadastro correspondente em web/mobile.
- Pacote portátil inclui backup.cmd e backup.sh; o backup H2 exige somente Java.
- Iniciador Linux/macOS corrigido para a versão atual.

O conjunto de backup é limitado a 64 MiB e usa senha exclusiva. O operador deve guardar essa senha e manter cópias protegidas fora do disco do banco.

Atualização de hospedagem em 13/11/2026: a demonstração usa Render Free com PostgreSQL 17 no Neon Free, sem contratação paga. Contas e mensagens foram verificadas após nova implantação, e um backup real foi restaurado em PostgreSQL local separado. Consulte docs/PERSISTENCIA-GRATUITA.md para cotas, recuperação e chave estável. Os binários e hashes da entrega permanecem os mesmos; os registros atualizados ficam no ramo principal.

Leia docs/ARMAZENAMENTO-BACKUPS.md e docs/VALIDACAO-0.7.0.md. O ZIP fonte conserva os registros do commit da entrega; o ramo principal recebe a confirmação final.
