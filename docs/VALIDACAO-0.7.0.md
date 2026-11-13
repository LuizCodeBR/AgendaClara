# Validação — Agenda Clara 0.7.0

## Escopo
Backups locais criptografados, recuperação de banco e chave, rotinas PostgreSQL e configuração explícita de armazenamento. Arquivos de trabalho, temporários e evidências no D:.

## Verificações locais concluídas
- Build completo: 171 testes Java aprovados e um teste PostgreSQL nativo condicional não executado por ausência de cliente/Docker local.
- 63 testes de interface, TypeScript e Vite aprovados.
- 18 cenários Playwright aprovados em desktop e Pixel 7 emulado na base restaurada; nomes de clientes de teste únicos permitem repetir a suíte com histórico preservado.
- Nove testes de backup: restauração de conta/empresa/e-mail, banco em uso, destino existente, chave incompatível/ausente, senha incorreta, adulteração, truncamento, caminho inesperado e limites.
- Pacote executável criou, verificou e restaurou o backup em outro diretório.
- Aplicação iniciada com a cópia restaurada: mesma empresa, conta, senha e mensagem privada recuperadas por HTTP.
- Cinco roteiros HTTP existentes aprovados.

## Verificações de publicação
A matriz Java 21/26 e H2/PostgreSQL, o teste nativo de pg_dump/pg_restore, navegador, pacote oficial e site serão acompanhados antes da entrega. Os resultados finais serão acrescentados ao ramo principal.

## Limites
O Render atual permanece efêmero; banco durável pago requer aprovação. Não há envio real de e-mails, tarefa agendada local ou exclusão automática de backups. Os conjuntos são limitados a 64 MiB. Proteja a senha e mantenha cópia externa ao disco do banco.
