# Agenda Clara 0.7.0 — armazenamento e backups

- Backup local criptografado reunindo banco H2 e chave original dos e-mails.
- Verificação de senha/integridade e restauração em pasta nova, preservando a instalação original.
- Exportação PostgreSQL com pg_dump e restauração transacional em banco vazio.
- Testes de senha incorreta, adulteração, chave incompatível, banco em uso e recuperação das mesmas contas/mensagens.
- Modo de armazenamento explícito no servidor e aviso de cadastro correspondente em web/mobile.
- Pacote portátil inclui backup.cmd e backup.sh; o backup H2 exige somente Java.
- Iniciador Linux/macOS corrigido para a versão atual.

O conjunto de backup é limitado a 64 MiB e usa senha exclusiva. O operador deve guardar essa senha e manter cópias protegidas fora do disco do banco.

A demonstração hospedada continua gratuita e efêmera até a autorização/configuração de banco durável. O plano concreto de PostgreSQL e backups gerenciados está em docs/PLANO-PERSISTENCIA-RENDER.md. Não foi contratado recurso pago automaticamente.

Leia docs/ARMAZENAMENTO-BACKUPS.md e docs/VALIDACAO-0.7.0.md. O ZIP fonte conserva os registros do commit da entrega; o ramo principal recebe a confirmação final.
