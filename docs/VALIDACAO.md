# Validação da entrega 0.1.0

Este documento registra a primeira versão. A validação atual está em [VALIDACAO-0.2.0.md](VALIDACAO-0.2.0.md).

Data: 11/11/2026.

## Resultado

- [Site publicado](https://agenda-clara-luizcodebr.onrender.com)
- [Downloads da versão 0.1.0](https://github.com/LuizCodeBR/AgendaClara/releases/tag/v0.1.0)
- [Verificações automáticas aprovadas](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43117290731)
- [Compilação e publicação dos downloads aprovadas](https://github.com/LuizCodeBR/AgendaClara/actions/runs/43117290665)

## Aplicação e regras

**32 testes Java**, executados nas quatro combinações de Java 21/26 e H2/PostgreSQL, sem falhas.

Cobertura de comportamento: login e sessão, CSRF, dados por estabelecimento, disponibilidade, duração/preço calculados no servidor, intervalos adjacentes e sobrepostos, bloqueios, cancelamento, remarcação e repetição segura.

Os testes de concorrência executam oito tentativas simultâneas para o mesmo horário, repetição concorrente de uma confirmação e disputa entre reserva e bloqueio. Apenas uma operação incompatível pode ocupar o intervalo.

## Interface e distribuição

- **9 testes da interface**, aprovados.
- Verificação TypeScript e compilação Vite, aprovadas.
- **4 cenários Playwright**, aprovados: reserva/cancelamento/isolamento e layout/PWA, em desktop e celular.
- Docker com PostgreSQL: compilação, inicialização e fluxo real de reserva/login/cancelamento aprovados.
- Fluxo de reconexão PWA verificado, sem cache de respostas da API.
- Pacote local extraído no Windows e iniciado pelo iniciador que acompanha o ZIP.
- Site HTTPS verificado com manifesto, ícones, reserva fictícia, repetição, login, cancelamento e logout.
- ZIPs públicos baixados novamente no D: e conferidos com os SHA-256 publicados pelo GitHub.

Ambiente local: Windows, Java 26.0.2 e Node.js 24.18.0. O alvo de compilação Java é 21.

A primeira execução automática identificou um erro no caminho do log durante o encerramento do teste de navegador. Os quatro cenários haviam passado; o caminho foi corrigido e a execução completa seguinte foi aprovada.

## Limites verificados e pendências

A instalação física da PWA em Android/iOS não foi executada nesta máquina. O manifesto, os ícones, o service worker e o comportamento no navegador foram verificados. A instalação depende do navegador e do sistema do aparelho.

O site usa um único serviço Render no plano gratuito e dados temporários, com aviso de demonstração pública. Reinícios e suspensões podem reinicializar os exemplos e as reservas fictícias. O banco da cópia local é independente e persiste na pasta data.

Cadastro de empresas/contas reais, edição de profissionais e expediente, comunicação ao cliente e operação com persistência são próximas etapas. A versão é destinada à demonstração com dados fictícios.
