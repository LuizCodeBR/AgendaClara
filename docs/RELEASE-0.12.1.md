# Agenda Clara 0.12.1 — revisão dos resultados e histórico

Correções de validação, transações, estados finais e paginação do histórico. A API recusa resultados numéricos e justificativa com caractere nulo. Cancelamento exige atendimento ainda confirmado, mesmo após retrocesso do relógio. Bloqueio e inclusão de histórico exigem transação de escrita. Consultas antigas da janela de histórico são canceladas e não substituem o atendimento atual.

Novos testes de regressão e concorrência cobrem colisão da chave entre profissionais, remarcação durante espera, disputa com acesso privado e ausência de leituras parciais. A interface testa respostas atrasadas e renderização segura do texto.

Sem migração nova, dependências novas ou recursos pagos. Esquema V10 e backups ACBK0001 preservados. Use o mesmo banco e chave ao atualizar. Web, PWA e pacote local compartilham as correções. Consulte a revisão e a validação no repositório.
