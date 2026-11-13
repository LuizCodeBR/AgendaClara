# Agenda Clara 0.8.0 — backups e limites sob demanda

- Um comando cria o backup criptografado, verifica sua integridade e registra a última cópia válida.
- Falha preserva a cópia anterior; execuções simultâneas são bloqueadas e nenhum backup antigo é apagado.
- Relatório HTML/JSON privado com tamanho do banco, estado da cópia, fontes e observações de cotas.
- Alertas em 70%, 90% e 100%; consumos desconhecidos e observações antigas não aparecem como uso zero ou estado saudável.
- Iniciadores Windows/Linux/macOS e adaptador privado para o Neon desta instalação, sem novas dependências do aplicativo.
- Conexão TLS e channel binding também propagados ao pg_dump.
- Ferramentas exclusivas do responsável pela instalação, sem exportação global no painel das empresas.

O usuário escolheu execução somente sob demanda: nenhum backup diário, monitor recorrente ou recurso pago foi ativado. O Neon Free não oferece a API detalhada de consumo; totais mensais ausentes devem ser conferidos nos painéis. Render e Neon permanecem gratuitos, com o mesmo banco persistente do site e da PWA. A cópia local tem banco próprio.

O pacote compilado requer somente Java 21 a 27 para uso local H2. Inclui operacao.cmd e operacao.sh, além das ferramentas anteriores de restauração. Limite de 64 MiB por backup, senha exclusiva e cópia externa protegida continuam necessários.

Leia [as instruções operacionais](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/OPERACAO-BACKUPS-LIMITES.md) e [o resultado de validação](https://github.com/LuizCodeBR/AgendaClara/blob/main/docs/VALIDACAO-0.8.0.md).
