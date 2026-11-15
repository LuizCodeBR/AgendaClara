# Validação — Agenda Clara 0.15.1

15/11/2026. Revisão de acessos profissionais, convites, revogação e recuperação. Arquivos e evidências no D:; nenhum recurso pago ou agendamento recorrente.

## Verificações locais concluídas
- Backend: 411 casos, 408 aprovados, três condicionais não aplicáveis no Windows/H2, zero falhas/erros. Dez casos novos, incluindo precisão do prazo.
- Interface: 181 testes aprovados em 23 arquivos, cinco novos; TypeScript/Vite aprovados.
- Navegador: 40 cenários aprovados em 54,5 segundos, desktop e Pixel 7 emulado. Fluxo profissional ampliado para recuperação revogada, reativação da mesma conta, caixa renovada e senha antiga recusada.
- Cópia isolada do banco 0.15.0 atualizada para 0.15.1: dois exemplos fictícios e históricos completos preservados; conta profissional manteve senha/identidade/vínculo, convite pendente continuou utilizável. Nenhuma migração necessária, V13.
- Antes de corrigir, dois testes novos de backend e dois de interface reproduziram os achados. O ensaio inicial do navegador identificou precisão diferente entre Instant e banco; prazo normalizado para microssegundos e coberto por teste. Um teste antigo de e-mails pressupunha empresa vazia: agora verifica que identificadores de mensagens de uma empresa não aparecem na outra. O primeiro ensaio foi interrompido para recompilar, causando recusas de conexão nos casos restantes. A execução completa posterior ficou verde.
- render.yaml validado; mesmo serviço Free. Sem dependência nova, extensão, serviço contratado ou tarefa recorrente.

Evidências: .cache/access-review-red-backend.log, access-review-red-ui.log, access-review-backend-final.log, access-review-ui-full.log, access-review-ui-build.log, access-review-e2e-green.log. Provas privadas em .cache/neon-private. A primeira execução não aprovada está em access-review-e2e-full.log.

Limites: mobile emulado, sem aparelho físico; revisão dedicada não equivale a auditoria externa. Backups recuperam o estado do momento da cópia, incluindo acessos que eram válidos. Publicação/CI, pacote oficial e ensaios online concluídos, conforme os registros abaixo.

## GitHub e recuperação
Código/tag v0.15.1: 90695a71d196dbd34270fd52c7e56809eeef8dbc.
[CI 43758801503](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43758801503): seis grupos aprovados, Java 21/26 em H2/PostgreSQL 17, Docker e interface/navegador. Em PostgreSQL: 411 casos, 410 aprovados e um condicional por versão de Java. Interface 181; navegador 40 cenários em 1,1 minuto. Doze rotinas HTTP e sete provas após restauração aprovadas, incluindo conta profissional, reserva e convite pendente; a rotina profissional também comprova recuperação antiga recusada e caixa vazia após revogar.

[Publicação 43759109256](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43759109256): sucesso; repetiu doze rotinas HTTP e sete provas de restauração antes de empacotar. [Release oficial](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.15.1).

## Backup anterior à publicação
Cópia sob demanda em D:/Projetos/AgendaClara/.cache/neon-operations/backups:
- AgendaClara-20261115T203251Z-c8610729-2504-4543-8eb9-28c126ef5321.acbackup.
- 61737 bytes; SHA-256 b6c2738a4a5c5abadb8b2483ec12f8bb7b1814e7f1fd93d4a547c88f3eddb33f.
- VERIFIED/fileMatches=true/sourceBound=true, verificado em 15/11/2026 às 20:33:09 UTC.
- Código de saída 2 indica cotas não informadas; backup concluído. Nenhuma rotina recorrente criada.
- Render confirmou plano free; organização Neon confirmou plan=free. Nenhum novo recurso, branch ou contratação.

## Pacotes oficiais e Windows
Arquivos baixados diretamente das URLs oficiais (a integração GitHub não transfere binários), verificados pelos hashes GitHub e SHA256SUMS.txt e extraídos em D:/Projetos/Entregas/AgendaClara-0.15.1-GitHub:

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| AgendaClara-0.15.1-demo.zip | 29212475 | b4c2395aea4f31e2078157bfd637e3ab32fca15873b8e762b1d5702854573ae0 |
| AgendaClara-0.15.1-fonte.zip | 728533 | fe0ea53051b73d8dcf76441b9168a72324a3c4e4a41e91cbb1243fedb6922a56 |

O iniciar-demo.cmd oficial iniciou a cópia isolada do banco em .cache/neon-private/access-review-upgrade/data. Conta, senha, vínculo, convite pendente, exemplos e históricos preservados após encerrar o servidor compilado localmente e iniciar o pacote oficial. Quatro cenários de navegador passaram em 7,7 segundos: profissional/reativação/recuperação revogada e relatórios/CSV, desktop/mobile emulado. Servidor local de ensaio encerrado. Instalações originais preservadas.

Registros: .cache/access-review-e2e-official.log, access-review-official-server.log. Pacote utiliza Java 21–27; nenhuma instalação adicional necessária neste computador.

## Site, mobile/PWA e banco principal
Deploy Render dep-dbt1hebbc2fs73crnhig, commit 90695a71d196dbd34270fd52c7e56809eeef8dbc: live em 15/11/2026 às 20:42:55 UTC (17:42:55 em São Paulo). Mesmo serviço Free e banco Neon Free. Atualizada somente DEMO_DOWNLOAD_URL por mesclagem; segredos e demais valores preservados.

Após publicação:
- Configuração pública confirmou 0.15.1, mailMode=demo, storageMode=persistent e download oficial da 0.15.1.
- Dois exemplos fictícios e respectivos históricos completos idênticos à prova anterior.
- Rotina HTTP profissional aprovada: convite, ativação, isolamento, dados mínimos, prazo, revogação, caixa demo vazia e recuperação antiga recusada.
- Quatro cenários reais no site aprovados em 57,3 segundos: convite/ativação/login/revogação/reativação/recuperação e relatórios/CSV, desktop e Pixel 7 emulado.
- A reativação manteve a identidade e mudou a prova da caixa; a senha anterior foi recusada. Dados fictícios dos ensaios foram revogados/cancelados/inativados pelos respectivos fluxos de limpeza.
- Consulta aos logs ERROR entre 20:40:25 UTC e 20:45:21 UTC não retornou registros. Essa observação se limita ao intervalo consultado.
- Servidores locais de ensaio encerrados; nenhuma porta 8090/8091 em escuta na conferência final.
- Nenhum recurso pago, extensão nova ou agendamento recorrente criado.

O primeiro acesso à versão anterior acordou o serviço gratuito, ultrapassando o prazo da solicitação inicial. Após concluir a inicialização, a prova anterior foi coletada normalmente. A publicação da nova versão e os ensaios finais tiveram êxito.

Registro adicional: .cache/access-review-e2e-live.log. Guia atualizado em ACESSO-PROFISSIONAIS.md; relatório em REVISAO-ACESSOS-0.15.1.md. Instalações originais e backups preservados no D:.
