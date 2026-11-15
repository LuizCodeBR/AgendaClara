# Fechamento de portfólio — Agenda Clara

15/11/2026. Referência executável **0.15.1**, código 90695a71d196dbd34270fd52c7e56809eeef8dbc. Escopo funcional concluído; manutenção corretiva. Este fechamento altera apresentação, documentação e uma rotina opcional de captura, sem mudar código funcional, esquema ou dados das instalações existentes.

## Entregas

- README reorganizado: problema, acesso online/local, funcionalidades, telas, arquitetura, evidências e limites. Removida a sequência de avisos antigos de releases da abertura; os históricos continuam em docs.
- DECISOES-TECNICAS.md: sete decisões com problema, escolha, alternativas/custos, código, testes e perguntas de entrevista.
- PORTFOLIO.md: descrição curta, destaques para currículo, roteiro de cinco minutos e três assuntos para estudar.
- DESENVOLVIMENTO.md: instruções detalhadas preservadas para compilação, navegador, API e bancos opcionais.
- docs/imagens: seis PNGs reais, galeria e manifesto com resolução/tamanho/SHA-256.
- scripts/capturar-portfolio.mjs: reprodução opcional, origem local e confirmação explícita de dados fictícios; não integra a CI de produto.

## Verificação deste fechamento

- Conferidos 92 links locais em seis documentos, sem destino inexistente ou fora do repositório.
- Seis PNGs, total de 921188 bytes, com dimensões e SHA-256 iguais ao manifesto.
- Capturas geradas com o JAR 0.15.1 já validado, banco H2 novo/separado e API real, sem mocks ou edição dos dados para encenar resultados.
- Desktop 1440 × 1000; Pixel 7 emulado 412 × 839, escala 1; páginas completas têm altura maior. Fontes carregadas, sem alertas e sem rolagem horizontal.
- As seis imagens foram inspecionadas. Uma segunda execução em banco novo removeu foco/seleção dos campos antes da captura, preservando os valores reais.
- Conta fictícia de Lucas Almeida criada para a imagem: somente seus dois atendimentos foram mostrados. Acesso revogado ao concluir. Nenhuma reserva extra ou dado online foi criado para o portfólio.
- Sintaxe da rotina aprovada. Conferida a recusa de execução sem confirmação de dados fictícios e contra a origem pública; ambas encerram antes de abrir o navegador ou alterar dados.
- README convertido para prévia HTML local e inspecionado em desktop/mobile, com todas as imagens carregadas. Essa prévia não é uma captura da renderização do GitHub.
- Confirmados pela integração GitHub a CI 43758801503 concluída com sucesso e os três arquivos da release v0.15.1.
- Não repetida a suíte de produto: aplicação, dependências e configurações não mudaram. As contagens citadas são as da validação 0.15.1, identificadas com data e link.
- git diff --check aprovado. Evidências locais ignoradas em .cache/portfolio-capturas-final.log, portfolio-servidor-final.log, portfolio-validar.cjs e portfolio-previa-*.png.

## Publicação e cópia local

Apresentação publicada no main do repositório, com commit de documentação. A tag v0.15.1 e os ZIPs oficiais permanecem como a referência executável já validada; o fechamento posterior está no main. Não exige recompilar ou republicar o site/PWA.

Cópia para guardar ou compartilhar: D:/Projetos/Entregas/AgendaClara-Portfolio-0.15.1. Inclui snapshot do código/documentação/capturas, identificação do commit e checksum. O executável oficial continua disponível na pasta da entrega 0.15.1 e no GitHub.

Todos os arquivos de trabalho, temporários e entregas no D:. Nenhum recurso pago, extensão, dependência nova ou agendamento criado. Servidor de captura encerrado ao concluir. Uso de navegador emulado não equivale a teste em aparelho físico.

## Estado para continuidade

A Agenda Clara está concluída para apresentação em portfólio. Reabrir trabalho por falha reproduzível, correção relevante de dependência ou necessidade concreta com escopo definido. Novas funcionalidades exigem uma decisão separada; não há uma lista obrigatória de funcionalidades pendentes para iniciar o próximo projeto.
