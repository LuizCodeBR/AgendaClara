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

Limites: mobile emulado, sem aparelho físico; revisão dedicada não equivale a auditoria externa. Backups recuperam o estado do momento da cópia, incluindo acessos que eram válidos. Publicação/CI, pacote oficial e ensaios online serão registrados abaixo quando confirmados.
