# Validação da entrega 0.1.0

Data: 11/11/2026.

## Verificações locais concluídas

Ambiente: Windows, Java 26.0.2 e Node.js 24.18.0. O código Java é compilado com alvo 21.

- Compilação completa pelo iniciador de desenvolvimento: aprovada.
- Java/API: **32 testes**, sem falhas ou testes ignorados.
- Interface: **9 testes**, sem falhas.
- Verificação TypeScript e compilação Vite: aprovadas.
- Navegador: reserva e remarcação reais na demonstração, login dos dois estabelecimentos e conferência do painel mobile.
- Painel em viewport de 390 px: sem rolagem horizontal observada.
- Dependências de execução da interface: auditoria npm sem avisos no momento da verificação. Isso não representa certificação de segurança do sistema.
- Configuração Playwright: quatro cenários reconhecidos, somando desktop e celular.

Os testes de concorrência executam oito tentativas simultâneas no mesmo horário, repetição concorrente da mesma chave e disputa entre bloqueio e reserva.

## Verificações externas

O fluxo GitHub Actions foi preparado para Java 21/26, H2/PostgreSQL, interface/PWA em navegador e execução Docker. A primeira execução remota e a publicação no Render serão registradas após a conclusão.

A instalação real da PWA em Android/iOS não foi realizada nesta máquina. A demonstração possui manifesto, ícones e service worker; o suporte de instalação depende do navegador.

A configuração Render usa apenas um serviço web no plano gratuito, com dados temporários. A persistência da versão local é independente da hospedagem.
