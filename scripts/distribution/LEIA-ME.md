# Agenda Clara — demonstração 0.4.0

1. Instale Java 21 ou mais recente até a versão 27, caso ainda não possua.
2. Extraia todos os arquivos deste ZIP em uma pasta gravável.
3. No Windows, execute iniciar-demo.cmd. No Linux/macOS, execute bash iniciar-demo.sh.
4. Abra http://localhost:8080 no Chrome ou Edge.
5. Mantenha o servidor aberto enquanto usa o sistema. Use Ctrl+C para encerrar.

Não é necessário instalar Node.js, Maven, Docker ou banco de dados. O banco H2 é criado na pasta data e guarda as reservas entre reinícios. Arquivos temporários ficam na pasta tmp.

Para experimentar o painel, clique em Meu negócio e escolha uma conta fictícia:

- Barbearia Horizonte: marina@demo.agendaclara.dev / Demo@12345
- Estúdio Aurora: rafael@demo.agendaclara.dev / Demo@12345

Todos os dados são exemplos. Use somente informações fictícias nesta demonstração.

Para instalar como aplicativo, use a opção de instalação no Chrome ou Edge. Essa instalação cria um acesso em janela própria; o servidor Java continua necessário.

Se a porta 8080 estiver ocupada:
- Windows: iniciar-demo.cmd --server.port=8081
- Linux/macOS: bash iniciar-demo.sh --server.port=8081

Depois abra http://localhost:8081.

O sistema local escuta apenas neste computador. Para instalar em outro aparelho, use o site publicado: https://agenda-clara-luizcodebr.onrender.com

Esta versão inclui reservas, bloqueios, remarcação, cancelamento, serviços, cadastro e edição de profissionais e expediente semanal, com isolamento entre estabelecimentos. Para configurar a equipe, entre em Meu negócio e abra Equipe e horários. Novos profissionais precisam de horários configurados antes de receber reservas. Para criar uma empresa e conta próprias, escolha Cadastrar meu negócio. Configure serviços, equipe e horários e publique em Página de reservas. A cópia local preserva esses cadastros na pasta data; a demonstração online tem contas temporárias. Verificação de e-mail, mensagens e recuperação de acesso são etapas futuras. Use senha exclusiva para testes.
