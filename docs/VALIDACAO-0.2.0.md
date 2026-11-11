# Validação da entrega 0.2.0

Data: 11/11/2026. Código da aplicação: `78363ae22865521f29e8228b587732548cb7557e`. Marca: `v0.2.0`. Alterações posteriores somente na documentação não mudam o código validado.

## Resultados e evidências

| Verificação | Resultado |
| --- | --- |
| API: Java 21 + H2 | 58 casos aprovados |
| API: Java 21 + PostgreSQL 17 | 58 casos aprovados |
| API: Java 26 + H2 | 58 casos aprovados |
| API: Java 26 + PostgreSQL 17 | 58 casos aprovados |
| Interface (Vitest) | 15 casos aprovados |
| Navegador (Playwright) | 6 cenários aprovados: 3 desktop e 3 celular emulado |
| Docker + PostgreSQL | Compilação e fluxo real de reserva/cancelamento aprovados |
| Pacote publicado | Gerado no GitHub com Java 21, verificado e baixado novamente |
| Windows local | Compilação pelo script PowerShell, iniciador portátil e fluxo da equipe aprovados |
| Site público | Versão, arquivos PWA, reserva, equipe, horários, acesso e download verificados |

Os 58 testes da API foram repetidos nas quatro combinações: são 232 execuções de 58 casos, e não 232 casos diferentes.

- [Verificações aprovadas](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43122874779)
- [Geração e publicação dos downloads](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43123244200)
- [Entrega 0.2.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.2.0)
- [Site atualizado](https://agenda-clara-luizcodebr.onrender.com)

## Proteções exercitadas

Os 26 novos casos de integração incluem cadastro sem expediente, atualização atômica, validação de dias/intervalos, união de horários adjacentes, isolamento entre estabelecimentos, sessão e CSRF, edição desatualizada, manutenção dos bloqueios, ativação/inativação e compromissos existentes.

Duas corridas verificam reserva simultânea com fechamento de expediente e reserva simultânea com inativação. A configuração não pode terminar incompatível com uma reserva confirmada.

Os 6 novos casos de interface verificam o envio completo de perfil e semana, abertura/fechamento de dias, cópia de dias úteis, limite de intervalos, preservação do rascunho no conflito, modo sem conexão, troca de profissional com alterações pendentes e cadastro inicial sem horários.

O novo cenário completo foi executado em desktop e celular emulado: cadastro, abertura de expediente, reserva pública, recusa de redução incompatível, preservação do rascunho, cancelamento e inativação. A inspeção visual local também usou largura de 390 pixels, sem rolagem horizontal. Isso não equivale a uma certificação em aparelhos Android/iOS físicos.

## Distribuição local

Arquivos oficiais baixados do GitHub e conferidos com SHA-256:

- `AgendaClara-0.2.0-demo.zip`: `81edbd2552b202825448282706b23a71fa48525ccd67a68fe68dde33c0d0484f`
- `AgendaClara-0.2.0-fonte.zip`: `0a31c3516405563022bd6b8a0975faec74b37801eeb97921536ca085684c8681`

Cópias locais desta verificação: `D:\Projetos\Entregas\AgendaClara-0.2.0-GitHub`.

O ZIP compilado foi extraído em uma pasta com espaços no nome e iniciado por `iniciar-demo.cmd`. A verificação confirmou reserva, repetição segura, cancelamento, cadastro de profissional, edição do expediente, disponibilidade pública, recusa de versão antiga, inativação e isolamento entre as duas empresas.

Para usar o pacote pronto, Java 21 a 27 basta. Node, Maven, Docker e instalação separada de banco não são necessários. Para desenvolver, consulte os requisitos do README. Não foi adicionada dependência de execução ou extensão do VS Code nesta etapa.

## Publicação no Render

Serviço existente: `agenda-clara-luizcodebr`, no mesmo espaço e plano gratuito. Implantação `dep-dbqg0ktbedkc73dg6kqg`, estado final `live`, concluída às 23:57:08 UTC de 11/11/2026.

Apenas o endereço de download foi atualizado por mesclagem de variáveis, preservando as demais configurações. Essa operação iniciou uma única implantação com o código validado.

Após a implantação:
- A API pública respondeu `version=0.2.0` e `publicDemo=true`.
- O link de download apontou para o ZIP compilado da versão 0.2.0.
- Os testes HTTP de reserva e gestão de equipe passaram pela URL HTTPS.
- O profissional fictício criado para verificação terminou inativo.
- A consulta de logs de erro no intervalo da inicialização/verificação retornou vazia.

## Limites

A hospedagem continua sendo uma demonstração pública com contas compartilhadas e armazenamento temporário. Cadastros, horários e reservas podem ser reiniciados após suspensão, reinício ou nova publicação. A cópia local guarda os dados na pasta data.

O mobile é uma PWA com a mesma interface e API do site. Alterações exigem conexão; a PWA não guarda operações para enviar depois. Não foi publicada uma versão nativa em lojas.

O cadastro de profissionais não cria contas de acesso. Nesta versão todos os profissionais ativos podem atender os serviços do estabelecimento; feriados, exceções por data e associação individual de serviços continuam como evoluções futuras.
