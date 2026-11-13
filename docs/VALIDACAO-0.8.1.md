# Validação 0.8.1 — revisão de backups e concorrência

## Situação durante o desenvolvimento
Quatro falhas reproduzidas antes das correções: consumo inválido bloqueando backup, origem diferente reutilizando histórico, disputa inicial e alerta antigo perdendo severidade. As reproduções passaram após os ajustes. A junção Windows já era recusada.

A suíte completa inicial aprovou 63 testes da interface e 194 testes Java, com três condicionais não executados no Windows. Uma verificação adicional do envelope foi acrescentada depois, e a suíte Java está sendo repetida. CI, restauração real, download oficial e implantação ainda serão registrados após a conclusão; não considerar essas etapas aprovadas antecipadamente.

O usuário mantém execução somente sob demanda e nenhum recurso pago foi criado. Projeto e temporários locais no D:. Consulte [REVISAO-BACKUPS-0.8.1.md](REVISAO-BACKUPS-0.8.1.md).
