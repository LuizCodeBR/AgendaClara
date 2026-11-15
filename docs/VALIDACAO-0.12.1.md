# Validação 0.12.1 — revisão dos resultados e histórico

14/11/2026, Windows, código em D:/Projetos/AgendaClara. [Escopo e achados](REVISAO-RESULTADOS-0.12.1.md).

## Resultados locais confirmados

- Backend Java 26/H2: 317 casos, 314 aprovados, três condicionais desativados, zero falhas/erros. Maven verify concluiu e gerou o JAR 0.12.1.
- Interface: 108 testes aprovados; TypeScript e Vite compilados.
- Navegador: 28 cenários aprovados (14 desktop e 14 Pixel 7 emulado).
- HTTP real: resultado antes do término recusado; entradas numéricas e caractere nulo recusados com 400; histórico, autor, remarcação, cancelamento e acesso privado aprovados.
- render.yaml validado contra o esquema oficial atualizado: um serviço web Free, sem banco/disco pago; alterado somente o link do download.

## Novos testes

18 casos de backend: 12 em AppointmentOutcomeReviewTest e seis em AppointmentOutcomeConcurrencyReviewTest. Cobrem entrada malformada, retrocesso do relógio, exigência de transação, rollback de correção com reutilização da chave, paginação durante novas alterações, colisão simultânea de chave em profissionais diferentes, disputa com rotação/revogação, espera por remarcação com versão antiga ou adivinhada e leitura durante transação não confirmada.

Três casos novos de interface verificam resposta antiga após troca de atendimento, cancelamento de paginação ao fechar e texto HTML tratado como texto. Antes das correções, os testes reproduziram aceitação de quatro resultados numéricos, caractere nulo, dois cancelamentos após retrocesso do relógio e ausência dos guardas de transação. Dois testes de concorrência de respostas da interface também falharam na implementação anterior.

As duas jornadas de resultados da suíte de navegador usam respostas controladas; as regras e disputas reais são exercitadas nos testes Java/banco e HTTP. Nenhum aparelho físico foi usado.

## Compatibilidade e publicação

Sem migração nova: esquema V10 e formato ACBK0001. Nenhuma instalação, dependência, extensão, serviço pago ou tarefa recorrente. Backups e limites continuam sob demanda. Código, caches, provas privadas e entregas ficam no D:.

A execução em Linux/PostgreSQL, os downloads oficiais e a atualização do site serão registrados após a conclusão dos respectivos processos.
