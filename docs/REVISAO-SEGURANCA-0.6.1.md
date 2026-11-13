# Revisão de segurança e concorrência — 0.6.1

## Escopo e método
Revisão adicional do estado publicado 0.6.0, commit 4a205c9350cd62bb12a11fa5f7db3cb907e08ac1. Foram lidos os fluxos de autenticação, recuperação, isolamento entre empresas, reserva e configuração de horários, fila, criptografia e interface. A análise foi acompanhada de testes de reprodução antes das correções.

Esta revisão foi realizada no desenvolvimento do projeto. Não representa certificação, auditoria por uma empresa externa ou garantia de ausência de vulnerabilidades. Testes usam contas e dados fictícios; não houve envio real nem ataque contra serviços de terceiros.

## Achados corrigidos
| Achado | Condição anterior | Impacto | Correção e prova |
| --- | --- | --- | --- |
| Espera do provedor ignorada no ciclo manual | Uma resposta 429 na terceira tentativa ou com espera além da validade encerrava o ciclo e apagava o horário de espera | O botão e a API permitiam tentar imediatamente, contrariando Retry-After | O prazo continua persistido quando o estado fica FAILED; canRetry e a API recusam antecipação. Dois testes falharam antes e passaram após a correção |
| Verificação do processador atrasada em lote lento | A verificação ocorria após até dez chamadas HTTP de dez segundos | O monitor podia indicar falta de atividade aos 90 segundos enquanto o processador ainda estava trabalhando | A verificação e a manutenção são conferidas antes e durante o lote. O teste avança o relógio durante dez envios e confirma que o sinal permanece recente |
| Leitura de chave parcialmente gravada na primeira inicialização | Duas instâncias podiam abrir o mesmo arquivo entre sua criação e o fim da escrita | Uma inicialização falhava ao ler uma chave vazia/incompleta | Coordenação dentro da JVM e bloqueio de arquivo entre processos; permissões restritas antes de escrever o segredo e sincronização da escrita. Reprodução concorrente falhou antes; 12 inicializações em três rodadas e quatro processos Java passaram após a correção |

Esses achados afetam a aplicação das regras de espera, precisão do monitor e disponibilidade na inicialização. Não foi demonstrada leitura de dados de outra empresa ou quebra da criptografia nesses cenários.

## Detalhes das alterações
### Espera e repetição
next_attempt_at também conserva o prazo de espera solicitado pelo provedor quando uma falha encerra o ciclo automático. A tentativa manual exige prazo já atingido, conteúdo disponível, validade e janela de envio vigentes e ausência de ciclo manual anterior.

Se a espera ultrapassar a validade, o botão não é liberado e a API não permite contornar o limite. Não se prolonga a validade do link. Na interface, um registro FAILED usa **Espera do serviço até**, em vez de indicar que existe uma tentativa automática agendada.

Duas chamadas manuais simultâneas disputam a mesma linha no banco: uma agenda o ciclo e a outra recebe conflito. O teste confirma um único incremento de manual_retries.

### Monitoramento em lote
O processador confere manutenção e sinal de atividade ao iniciar e entre mensagens, com referência ao horário atual. Isso evita reutilizar a hora capturada antes de um lote demorado. O monitor continua sendo interno à aplicação; uma chamada bloqueada pode impedir atualizações enquanto estiver em execução. As requisições ao provedor permanecem limitadas a dez segundos.

### Arquivo da chave
Na demonstração, mail-outbox.key.lock coordena a leitura/criação de mail-outbox.key. O arquivo de coordenação não contém a chave, é gerenciado automaticamente e não deve ser removido enquanto existirem instâncias em funcionamento. Todas as instâncias que geram ou leem a chave no modo demo precisam usar a versão corrigida; faça a atualização com os processos locais encerrados.

A chave existente é preservada. Uma chave ausente quando o banco já está vinculado continua sendo recusada. Uma chave nova recebe permissões do proprietário antes da escrita e é sincronizada no disco antes de sua utilização. Um arquivo vazio/inválido decorrente de uma interrupção continua causando recusa segura; ele não é substituído silenciosamente.

O bloqueio exige uma pasta gravável na demonstração, como a pasta data do pacote local. Para instâncias reais, continua recomendada a mesma MAIL_ENCRYPTION_KEY privada em todas elas; arquivos de chave preconfigurados no modo real continuam disponíveis sem geração automática. Sistemas de arquivos de rede precisam oferecer bloqueio de arquivo confiável; esse cenário não foi validado.

## Proteções conferidas
- Consultas administrativas usam a empresa da sessão; relações compostas no banco impedem associar profissionais, usuários e mensagens a outra empresa.
- Reservas, bloqueios e alterações de expediente compartilham o bloqueio do profissional; versões recusam configurações desatualizadas.
- Oito reservas simultâneas produzem uma confirmação, e repetições idênticas preservam a referência.
- Confirmação e recuperação usam segredo aleatório, hash persistido, finalidade, validade e consumo único serializado pelo usuário.
- Duas recuperações com o mesmo link têm um vencedor; a troca revoga sessões anteriores na próxima chamada à API.
- Confirmações simuladas e reais permanecem separadas. A caixa privada não é disponibilizada no modo real.
- Cookie HttpOnly/SameSite, proteção CSRF, rotação da sessão, CSP, no-store e limites de tamanho/frequência são verificados.
- AES-GCM autentica conteúdo e identificadores; tarefa recuperada usa nova concessão e uma conclusão antiga não a sobrescreve.
- A PWA mantém somente recursos públicos de reconexão/ícones; não armazena nem repete chamadas privadas ou reservas.

Os limites de frequência permanecem por processo, conforme o desenho atual. Antes de ampliar para várias instâncias públicas, será necessário controle compartilhado de abuso. Pedidos que já passaram pela autorização podem estar em execução quando uma senha é alterada; a revogação é verificada na próxima chamada.

## Compatibilidade e operação
Não houve alteração no esquema do banco, dependências de execução ou instalação de nova extensão. O mesmo pacote React/Java atende web, mobile/PWA e execução local.

Uma cópia do banco e da chave da versão 0.6.0 foi aberta pela 0.6.1: a mensagem anterior foi recuperada sem gerar outra chave. Preserve o backup consistente de banco e chave. A hospedagem gratuita continua com armazenamento efêmero, sem contratação de recursos pagos.

O npm audit consultado em 13/11/2026 não reportou vulnerabilidades conhecidas na árvore JavaScript. Esse resultado é específico do catálogo consultado; não equivale a análise completa das dependências Java nem a garantia de segurança.

## Validação e referências
Veja [VALIDACAO-0.6.1.md](VALIDACAO-0.6.1.md) para testes, publicação e limites.

- [OWASP: recuperação de senha](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
- [PostgreSQL 17: isolamento e concorrência](https://www.postgresql.org/docs/17/transaction-iso.html).
- [Resend: deduplicação](https://resend.com/docs/dashboard/emails/idempotency-keys).
