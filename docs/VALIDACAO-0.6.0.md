# Validação — Agenda Clara 0.6.0

## Escopo e ambiente
Fila transacional persistente, criptografia do conteúdo, recuperação de tarefas interrompidas e acompanhamento privado por empresa. Mesma compilação para web, PWA e execução local. Dados exclusivamente fictícios, modo demo e nenhum envio externo.

Código, dependências, temporários e evidências desta validação foram mantidos em D:/Projetos/AgendaClara. Não foi necessário instalar biblioteca ou extensão adicional.

## Verificações locais concluídas
- Compilação completa: 154 testes Java, zero falhas/erros/ignorados; 61 testes de interface aprovados; TypeScript e Vite aprovados.
- 18 cenários Playwright aprovados em desktop e Pixel 7 emulado.
- Cinco roteiros HTTP aprovados: smoke, date-hours-smoke, registration-smoke, account-access-smoke e mail-queue-smoke.
- Migração real de arquivo H2 V5 para V6, preservando contas, credenciais e links.
- Reinício real do processo Java preservando banco e chave: mesma mensagem recuperada na caixa privada e no histórico.
- Layout desktop/celular e ausência de exposição de endereço ou link no painel.

## Cobertura específica
Testes verificam rollback conjunto da fila e recuperação, chave persistida, chave ausente/incorreta, criptografia aleatória e detecção de adulteração. Também cobrem aquisição concorrente por quatro processos simulados, concessão expirada, recusa de conclusão atrasada, limite de tentativas, repetição manual, Retry-After, recusa definitiva, link utilizado/substituído, validade e limpeza.

A autorização inclui histórico privado, filtro válido, login obrigatório, CSRF e recusa de repetição por outra empresa. A interface cobre reconexão, sessão expirada, atualização, filtro e reagendamento.

## Publicação
A confirmação final de CI, arquivos oficiais e implantação será acrescentada ao ramo principal após a publicação. O ZIP fonte conserva o registro disponível no commit da entrega.

## Limites
Os testes de transporte classificam respostas simuladas; nenhum recebimento real foi validado sem remetente/serviço configurado. Não há webhooks de entrega nem alertas externos. Pixel 7 é emulação de navegador, não teste em aparelho físico. A persistência não sobrevive à remoção do armazenamento efêmero do Render gratuito. Banco e chave original são necessários para recuperar conteúdo protegido. Veja [FILA-EMAILS.md](FILA-EMAILS.md).
