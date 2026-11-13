# Validação 0.8.0 — operação sob demanda

## Escopo
Backup e verificação em uma execução solicitada, preservação da última cópia válida, bloqueio de concorrência, relatório HTML/JSON e interpretação conservadora de cotas. O usuário escolheu não ativar agendamentos. Nenhum recurso pago foi provisionado.

## Confirmado localmente
- Build completo: 182 testes Java encontrados, 180 aprovados, nenhum erro/falha; dois condicionais não executados (PostgreSQL Docker do CI e criação de link simbólico não permitida no Windows).
- Interface: 63 testes aprovados; TypeScript e Vite aprovados.
- Dez novos testes operacionais: nove aprovados no Windows e um condicional; validam backup real, preservação após falha, concorrência, arquivo adulterado, pasta indevida, caminho inválido, limites 70/90/100%, dados desconhecidos/antigos, período encerrado, observação de outro recurso e rejeição de campos inesperados.
- Rotina real contra Neon Free: backup criptografado de 19.024 bytes criado e verificado. Banco atual medido: 8.527.872 bytes. Isso não representa o armazenamento total do projeto.
- Saída 2 com backup VERIFIED: consumos mensais indisponíveis permaneceram UNKNOWN, sem usar os zeros da API genérica como prova de consumo zero.
- Chave e credenciais reutilizadas da pasta privada no D:, sem impressão ou inclusão em arquivos de código.

## Verificações em andamento
CI Java 21/26, H2/PostgreSQL; cenários de navegador; restauração do backup Neon em novo destino local; pacote distribuído e publicação online serão registrados ao concluir. Não considerar esta lista como aprovada antes da atualização final.

## Fontes e instruções
Consulte [OPERACAO-BACKUPS-LIMITES.md](OPERACAO-BACKUPS-LIMITES.md), [PERSISTENCIA-GRATUITA.md](PERSISTENCIA-GRATUITA.md) e [ARMAZENAMENTO-BACKUPS.md](ARMAZENAMENTO-BACKUPS.md). As referências de cotas foram consultadas em 13/11/2026.
