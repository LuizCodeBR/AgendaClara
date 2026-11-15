# Agenda Clara 0.14.1

Revisão de segurança e concorrência dos relatórios e da exportação CSV.

- Consulta e exportação vinculadas à empresa esperada na tela; troca de conta em outra aba exige novo acesso.
- Falha de autorização cancela consultas e arquivos pendentes, evitando reaparecimento de respostas antigas.
- Resposta de formato inesperado não é baixada como CSV.
- Tempo limite gera orientação sem anexo ou detalhes internos.
- Normalização Unicode reforçada e testes ampliados de conteúdo CSV, isolamento e alterações simultâneas.

Sem nova instalação, dependência ou recurso pago. Site, PWA e pacote local atualizados com o mesmo código. V12 e formato ACBK0001 preservados.

Recarregue a página/PWA. No Windows, encerre a versão anterior, preserve data e mail-outbox.key e execute iniciar-demo.cmd do pacote novo com Java 21–27. Guia em docs/RELATORIOS.md e revisão em docs/REVISAO-RELATORIOS-0.14.1.md.
