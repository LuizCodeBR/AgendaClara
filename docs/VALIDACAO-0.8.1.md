# Validação 0.8.1 — segurança e concorrência de backups

Concluída em 13/11/2026. Código da entrega: 7a65ad82348eff7a0715ff608ed08825e611f887. Tag: v0.8.1. As alterações posteriores a essa tag atualizam somente documentação e a referência do download no render.yaml; os pacotes publicados mantêm o código testado.

## Problemas reproduzidos e corrigidos
Cinco testes iniciais na 0.8.0: quatro falhas e uma proteção já existente aprovada. Foram corrigidos o bloqueio do backup por consumo inválido, o reaproveitamento de histórico entre origens diferentes, a disputa na primeira inicialização e a perda da severidade de consumo excedido quando a observação envelhecia. A junção Windows já era recusada; a revisão preservou essa proteção.

Detalhes, reforços e limites: [REVISAO-BACKUPS-0.8.1.md](REVISAO-BACKUPS-0.8.1.md).

## Testes
| Verificação | Resultado |
| --- | --- |
| Java 26, Windows local | 198 casos: 195 aprovados, 3 condicionais não executados, nenhuma falha |
| CI Java 21 e 26, PostgreSQL | 198 casos por matriz: 197 aprovados, 1 exclusivo do Windows não executado |
| CI Java 21 e 26, H2 | 198 casos por matriz: 196 aprovados, 2 condicionais não executados |
| Interface | 63 testes aprovados, TypeScript e compilação aprovados |
| Navegador desktop e Pixel 7 emulado | 18 testes aprovados no CI e 18 na aplicação restaurada do Neon |
| Adaptador PowerShell | Leitor e encaminhamento de opções aprovados no Windows e no CI |
| Docker com PostgreSQL | Demonstração e verificações HTTP aprovadas |
| Publicação dos downloads | Compilação, cinco verificações HTTP, backup H2, relatório e restauração aprovados |

Os 16 novos testes Java cobrem as reproduções e proteções específicas da revisão. A suíte PostgreSQL também verifica recusa de restauração com bloqueio ocupado e recuperação normal após liberação. A concorrência inicial é exercitada com várias threads e com duas JVMs reais.

As condições locais não executadas são PostgreSQL via Docker, criação de link simbólico que depende de privilégio no Windows e FIFO exclusivo do Linux. As matrizes Linux executaram os casos aplicáveis, enquanto a junção Windows foi exercitada localmente. Não somar matrizes como se fossem testes distintos.

CI: [seis verificações aprovadas](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43513459228).
Publicação: [workflow aprovado](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43514531007).

## Backup e restauração reais
- Novo backup PostgreSQL do Neon criado e verificado na 0.8.1: 23.675 bytes, SHA-256 1466bf9fe174c2a92c566cf7f0ac465579e9a704f7be2792223b6413a3b7571c.
- A exportação real passou mesmo com PGHOSTADDR e PGSERVICE conflitantes no ambiente do processo chamador; essas variáveis foram excluídas do ambiente nativo.
- O estado operacional antigo foi migrado para formato 2 após o novo backup válido; as duas cópias existentes foram preservadas.
- A nova cópia foi restaurada em outro banco PostgreSQL 17.12 local, na porta de loopback 55437, usando uma nova pasta privada para a chave. Empresa, login, senha e o mesmo registro histórico de e-mail foram conferidos por HTTP.
- A prova antiga da caixa fictícia havia expirado por tempo. Sua prévia permaneceu protegida; a conferência utilizou login e histórico autorizado, sem flexibilizar expirações.
- Um arquivo real criado na 0.7.0 foi autenticado e verificado pela 0.8.1, mantendo compatibilidade com ACBK0001.
- O ZIP oficial baixado do GitHub foi executado no Windows, gerou e verificou um backup H2 e o restaurou em outra pasta. Empresa, conta, senha e a mesma mensagem privada foram recuperadas pelo aplicativo restaurado.
- Os servidores Java e o cluster PostgreSQL temporários foram encerrados ao final.

Os arquivos com senha, chave, cookies e provas privadas permanecem fora do Git e das evidências públicas.

## Web, mobile e local
Implantação Render dep-dbroe867bikc73b58jkg concluída em 2026-11-13T21:56:41.858709Z, no mesmo serviço Free. A API pública confirmou versão 0.8.1, armazenamento persistent, e-mail demo e download v0.8.1.

Cinco verificações HTTPS foram aprovadas: reservas e segurança HTTP, exceções de expediente, cadastro e isolamento, recuperação de acesso e fila de e-mails. A conta fictícia anterior à implantação manteve login, empresa e histórico.

Os painéis de e-mails e cadastro foram conferidos por HTTPS em desktop e Pixel 7 emulado, com persistência explícita, dados fictícios obrigatórios, saída de sessão e ausência de erros JavaScript ou transbordamento da página. O relatório privado foi conferido nos dois tamanhos, sem scripts nem requisições externas; sua tabela permite rolagem horizontal no celular. Não houve teste em aparelho físico nem instalação em loja.

Consulta aos logs de erro do Render entre a implantação e 2026-11-13T21:58:57Z: nenhum registro retornado. Esse é o resultado da janela observada, não uma garantia permanente de ausência de falhas.

Site: https://agenda-clara-luizcodebr.onrender.com
Downloads: https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.8.1

## Pacotes oficiais
Arquivos baixados em D:/Projetos/Entregas/AgendaClara-0.8.1-GitHub e conferidos com SHA256SUMS.txt:

| Arquivo | SHA-256 |
| --- | --- |
| AgendaClara-0.8.1-demo.zip | b9a6e8a015f4015806164c657745db698f9f59149f2361051b391c013fbcc3e2 |
| AgendaClara-0.8.1-fonte.zip | 5c830c2b28f37ff51bb264f650e3a894d09820901d95450f6369b158d5da237c |

Ensaio local: subpasta teste-pacote/AgendaClara-demo. Evidências sem credenciais: subpasta evidencias.

## Custos e limites
Nenhum recurso pago, upgrade ou tarefa recorrente foi criado. Permanecem o Render Free e o Neon Free já utilizados. O render.yaml foi validado contra o esquema oficial e restringe esta configuração a um serviço web Free, sem banco ou disco pago.

O relatório real permanece ATTENTION porque não há observações completas de consumo mensal; o backup está VERIFIED. Dados desconhecidos continuam desconhecidos. O relatório não controla cobrança nem substitui os painéis dos provedores.

Continua o limite de 64 MiB do conjunto de backup, além do envelope criptográfico de 52 bytes. O bloqueio consultivo coordena nossas ferramentas; a restauração exige destino vazio e isolado de outros clientes. As cópias no D: não protegem contra perda física desse disco; nenhuma cópia externa foi criada nesta revisão.

Não foi necessário instalar programa ou extensão neste computador. O pacote compilado exige apenas Java compatível, conforme LEIA-ME; ferramentas de desenvolvimento e Docker são opcionais para quem desejar compilar ou executar as mesmas matrizes localmente.
