# Agenda Clara 0.14.0

Painel de indicadores e relatórios por período, disponível no site, na PWA e no pacote local.

- Filtros combinados por datas, profissional e serviço, com atalhos e limite de 90 dias.
- Total, concluídos, faltas, cancelamentos, resultados pendentes e valor dos serviços concluídos.
- Gráfico diário, resumo por profissional e detalhamento por profissional/serviço.
- Exportação CSV autenticada, sem dados pessoais de clientes e com proteção de rótulos contra fórmulas.
- Leitura consistente durante alterações concorrentes, preservação de preços históricos e suporte a profissionais inativos.
- Testes de regras, isolamento, concorrência, interface, download real e preservação após atualização/restauração.

O valor exibido não comprova pagamento. CSV executa uma nova consulta dos filtros e identifica seu instante. Dados são atualizados sob demanda.

V12 acrescenta somente um índice. As ferramentas novas aceitam backups de esquemas 6 a 12, mantendo ACBK0001. Faça backup, encerre a versão anterior e preserve data e mail-outbox.key ao atualizar.

Sem dependência, extensão ou recurso pago adicional. Windows: extraia o ZIP de demonstração no D: e execute iniciar-demo.cmd com Java 21–27. Guia completo em docs/RELATORIOS.md no código-fonte.
