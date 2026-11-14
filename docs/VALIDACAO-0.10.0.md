# Validação 0.10.0 — Minha reserva

Data: 14/11/2026, horário de São Paulo. Projeto, temporários, provas privadas, backups e pacotes em D:/Projetos.

## Entrega e testes

Implementação: 9b98002aace8c47ea4539fa089d58e6faa7d191a, tag v0.10.0. A correção posterior 676895ff7e5cb4a6096c9993501986ed3bfd9035 acrescenta somente uma espera/assertiva ao teste de navegador; não altera o aplicativo compilado nem os arquivos oficiais já publicados.

| Verificação | Resultado |
| --- | --- |
| Windows / Java 26 / H2 | 241 casos: 238 aprovados, três condicionais desativados, zero falhas |
| Linux / Java 21 e 26 / H2 | 241 casos por execução: 239 aprovados, dois condicionais desativados |
| Linux / Java 21 e 26 / PostgreSQL 17 | 241 casos por execução: 240 aprovados, um condicional desativado |
| Interface | 80 testes aprovados |
| TypeScript / Vite | Compilação aprovada |
| Navegador / JAR local | 24 cenários aprovados: 12 desktop e 12 Pixel 7 emulado |
| HTTP / JAR local | Sete rotinas aprovadas |
| HTTP / PostgreSQL Neon de ensaio | Área do cliente aprovada |
| Docker / PostgreSQL | Construção, execução e recriação aprovadas no GitHub |
| Site HTTPS | Sete rotinas aprovadas; quatro cenários novos de navegador aprovados |
| Melhoria do encerramento dos testes | Dois cenários de revogação repetidos por HTTPS, aprovados com espera explícita do cancelamento |

No Windows ficam desativados os casos condicionais de PostgreSQL por Docker, recurso especial Linux e privilégio de link de diretório. No Linux/H2 são desativados o caso PostgreSQL por Docker e o link específico Windows. No Linux/PostgreSQL fica desativado somente o caso específico Windows. O teste de backup nativo PostgreSQL foi executado e aprovado nos dois jobs PostgreSQL.

CI da entrega: [43605771610](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43605771610), seis jobs aprovados.
Publicação: [43606035169](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43606035169), aprovada.
CI da melhoria do teste: [43607043606](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43607043606), seis jobs aprovados novamente.

## Casos de segurança e concorrência

Cobertos isolamento entre reservas e estabelecimentos, UUID/referência pública insuficientes, sessão sem permissão de responsável, CSRF, limite de tentativas, acesso revogado/expirado, oito sessões no máximo, chaves por finalidade, versão antiga, disputa com revogação/rotação durante a espera pelo bloqueio e emissão idempotente.

A remarcação preserva preço e duração originais mesmo depois de alteração do catálogo. Rejeita horários ocupados, bloqueados, fora do expediente e fora do horizonte. Repetições seguras não duplicam a alteração. A repetição da reserva pública não revela um link substituído ou revogado.

A interface cobre confirmação de cancelamento, conflito seguido de atualização manual, sessão por caminho de reserva, duas abas independentes, encerramento de apenas um acesso, remoção do token da URL e ausência do segredo em localStorage, sessionStorage e endereços HTTP.

## Migração do banco online

Ensaio em branch temporária Neon Free com compute fixo 0,25 CU: esquema 7 → 8, preservando 22 empresas, 22 contas, 19 reservas, 28 mensagens e 26 vínculos de serviços. A migração não criou acessos para reservas antigas. O servidor de ensaio foi encerrado; a branch br-fragrant-credit-bc41k93u expirou e deixou de constar entre as branches ativas.

No banco principal, imediatamente antes/depois da migração, as mesmas contagens foram preservadas: 22 empresas, 22 contas, 19 reservas, 28 mensagens e 26 vínculos. Foram criadas as novas tabelas vazias e a versão inicial das reservas permaneceu zero. As contagens antecedem os testes posteriores com outros dados fictícios.

Implantação Render dep-dbs76aek1f9s738fe7f0: live em 2026-11-14T14:44:48.087883Z, a partir de 9b98002aace8c47ea4539fa089d58e6faa7d191a. HTTPS confirmou versão 0.10.0, storageMode persistent, mailMode demo e endereço do download 0.10.0.

Empresa, conta, senha e mesmo registro histórico de e-mail de prova da versão anterior permaneceram acessíveis. A prévia expirada não foi reaberta; o histórico foi consultado após autenticação.

Logs de erro do Render entre 14:41:40Z e 14:52:42Z: nenhum registro retornado. Essa é uma janela observada, não garantia permanente de ausência de erros.

## Pacote oficial e restauração

Os dois ZIPs oficiais foram baixados no D: e conferidos com SHA256SUMS.txt.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.10.0-demo.zip | 29109000 | e17f8406941fa17b7ff81b4a1bf2062a7940d943473cf0273a3e0a27064568c9 |
| AgendaClara-0.10.0-fonte.zip | 495570 | 3094142b927c059e8f4d04a0e3688b0a902dcccbc3f7261b759ca58aebe1a2bd |

Arquivos: D:/Projetos/Entregas/AgendaClara-0.10.0-GitHub.
Entrega: https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.10.0
Site: https://agenda-clara-luizcodebr.onrender.com

Uma cópia privada da instalação 0.9.0 foi aberta com o iniciador Windows do pacote oficial 0.10.0. Conta, senha e histórico foram preservados. O teste HTTP da área do cliente passou. Uma nova reserva com sessão privada foi preparada; operacao.cmd criou e verificou o backup e backup.cmd restaurou em outra pasta. O pacote restaurado reconheceu a conta antiga, a mesma sessão e o mesmo link privado.

O JAR local também preservou reserva, sessão e link em restauração separada. A investigação inicial distinguiu encerramento forçado do processo pelo ambiente de testes de encerramento normal com Ctrl+C; a validação final utilizou encerramento normal e confirmou gravação no disco antes de criar a cópia. Não se certificou recuperação após falha física de disco ou encerramento abrupto.

Os testes GitHub da interface e da distribuição criam, verificam e restauram cópias reais, conferindo conta, histórico, sessão e link depois da restauração. O backup nativo PostgreSQL testa também as novas tabelas, a chave derivada e a sequência de sessões.

## Ajustes na própria validação

Durante a conferência HTTPS, o teste de revogação fechava o navegador antes de aguardar a conclusão do cancelamento do responsável. Foi acrescentada a assertiva do status Cancelado antes de encerrar. Os dois cenários foram repetidos e aprovados. Somente as reservas fictícias identificadas pelos nomes dos testes foram canceladas na limpeza; a proteção contra desativar profissional com reservas futuras permaneceu ativa.

As capturas ficaram nos diretórios privados de teste ignorados pelo Git. A revisão automática recusou copiá-las para pasta publicável por possível exposição de links privados; foram publicados somente resultados textuais. Credenciais, cookies, chaves e provas de acesso não entraram nos arquivos distribuídos nem no Git.

## Backups e custo

Backup manual antes da implantação: 35.201 bytes, SHA-256 6d0403b615b74c750368fa7369157ddf3852bfbfc1e1b12f02f6fe77be7aed03, esquema 7.
Backup manual posterior: 44.203 bytes, SHA-256 91360415b70d4627b3658133495c85d06fc07978056a3df792e0b7c71c9995ab, esquema 8.

O relatório operacional marca backup VERIFIED. O conjunto permanece ATTENTION quando o consumo dos provedores não está informado; isso não foi apresentado como consumo zero nem como cota comprovadamente livre.

Mantidos Render Free e Neon Free, sem upgrade, recurso pago, provedor de mensagem ou tarefa recorrente. Backups e verificação de limites continuam sob demanda. O arquivo render.yaml foi validado com o esquema oficial atual; continua com um único serviço web Free, sem banco ou disco pago.

Não foi necessário instalar programa ou extensão. Testes mobile usam Pixel 7 emulado; não houve teste em aparelho físico nem distribuição em loja. Web e PWA compartilham o banco online; a instalação local tem banco independente. Recarregue a PWA depois de atualizar. Preserve banco e chave juntos e use a ferramenta nova para esquemas 6, 7 e 8.
