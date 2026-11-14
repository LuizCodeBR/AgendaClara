# Agenda Clara 0.9.0 — serviços por profissional

Escolha os serviços atendidos por cada profissional em Equipe e horários. A página de reservas apresenta somente profissionais compatíveis com o serviço, e a API confirma essa regra antes de gravar.

- Serviços, cadastro e expediente salvos juntos, com proteção de versão e de concorrência.
- Retirada de serviço recusada quando afeta atendimento confirmado futuro ou em andamento.
- Migração preserva as combinações e reservas existentes, inclusive configuração de profissionais inativos.
- Novos profissionais e serviços exigem vinculação explícita.
- Publicação exige serviços e horários no mesmo profissional ativo.
- Site, mobile/PWA e pacote local com o mesmo fluxo.
- Backups compatíveis com esquemas 6 e 7; formato criptográfico mantido.

Preserve a pasta data e a chave ao atualizar uma instalação local. Faça backup antes de migrar e encerre o servidor local durante a atualização. A demonstração continua usando dados fictícios e e-mails simulados.

Mantidos os serviços gratuitos; backups e monitoramento somente sob demanda.

[Como usar](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/SERVICOS-PROFISSIONAIS.md) · [Validação](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/VALIDACAO-0.9.0.md).
