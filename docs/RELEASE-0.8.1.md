# Agenda Clara 0.8.1 — revisão dos backups e do monitoramento

- Histórico vinculado à origem correta do banco, com migração conservadora dos registros anteriores.
- Erro no arquivo de consumo gera aviso sem impedir a proteção dos dados por backup.
- Primeira inicialização protegida pelo mesmo bloqueio usado pelas demais etapas, incluindo disputa entre processos.
- Tentativas interrompidas ficam registradas e a última cópia válida permanece preservada.
- Permissões privadas desde a criação e leituras limitadas de arquivos e credenciais.
- Cliente PostgreSQL com ambiente reduzido, TLS e timeouts consistentes, nomes de banco escapados e coordenação de restaurações.
- Alertas antigos de limite esgotado mantêm a severidade e mostram sua validade.

Execução somente sob demanda. Nenhum recurso pago ou agendamento foi ativado. Site e PWA continuam no mesmo banco persistente; a cópia local tem banco próprio e requer apenas Java 21 a 27.

O arquivo de backup permanece ACBK0001, compatível com versões anteriores. Metadados antigos são preservados sem vínculo até um novo backup válido. Em uma restauração PostgreSQL, a chave é salva em pasta nova antes da etapa nativa e pode permanecer ali se a restauração falhar.

[Revisão detalhada](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/REVISAO-BACKUPS-0.8.1.md) · [Validação](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/VALIDACAO-0.8.1.md).
