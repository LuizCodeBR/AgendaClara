# Agenda Clara — demonstração 0.9.0

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

Esta versão inclui reservas, bloqueios, remarcação, cancelamento, serviços, cadastro e edição de profissionais e expediente semanal, com isolamento entre estabelecimentos. Para configurar a equipe, entre em Meu negócio e abra Equipe e horários. Novos profissionais precisam de horários configurados antes de receber reservas. Para criar uma empresa e conta próprias, escolha Cadastrar meu negócio. Configure serviços, equipe e horários e publique em Página de reservas. A cópia local preserva esses cadastros na pasta data; a demonstração online atual usa PostgreSQL persistente no Neon Free, dentro das cotas do plano gratuito. Confirmação de e-mail e recuperação de senha podem ser testadas pela caixa de e-mails privada do navegador. Nenhuma mensagem real é enviada no modo de demonstração. Configure um provedor para envio real; instruções em EMAIL-ACESSO.md no repositório. Use senha exclusiva para testes.

Abra E-mails e envios no painel para acompanhar as mensagens de acesso da sua empresa. No modo demo, elas ficam na caixa simulada e nenhum e-mail real é enviado.

Banco e mensagens protegidas são preservados na pasta data, que inclui a chave mail-outbox.key. Para backup, encerre o servidor e copie a pasta data completa para um local protegido. Não apague nem substitua a chave: sem a original, a aplicação não consegue recuperar os conteúdos. Ao atualizar de uma versão anterior, preserve data. Consulte docs/FILA-EMAILS.md no repositório para detalhes.

Para manter arquivos desta cópia no D:, extraia o pacote nesse disco. Não é necessária extensão do VS Code.

A versão 0.8.1 acrescenta a rotina operacional sob demanda. Execute operacao.cmd (ou bash operacao.sh) para gerar um relatório sem nova cópia; use --operations=backup para criar e verificar o backup com o H2 encerrado. O relatório operations/latest.html não publica dados e não se atualiza sozinho. Consumos não informados exigem conferência no provedor. Nenhuma tarefa recorrente é criada. Encerre a cópia local anterior antes de atualizar e preserve data. O arquivo mail-outbox.key.lock é gerenciado automaticamente para coordenar inicializações; não o remova enquanto o aplicativo estiver em execução.

Backups: com o Java encerrado, abra um terminal nesta pasta e execute backup.cmd, ou bash backup.sh. Informe e confirme uma senha exclusiva. O arquivo criptografado .acbackup é criado em backups e reúne banco e chave. Limite do conjunto: 64 MiB; o processo reserva até 512 MiB de memória.

Para verificar: backup.cmd --backup=verify --input="D:\Backups\copia.acbackup"
Para restaurar: backup.cmd --backup=restore --input="D:\Backups\copia.acbackup" --target-dir="D:\AgendaRestaurada\data"
O destino deve ser uma pasta nova. Preserve a senha fora do arquivo e mantenha outra cópia protegida fora do disco do banco. As instruções completas estão em docs/ARMAZENAMENTO-BACKUPS.md no repositório.

Na versão 0.9.0, escolha os Serviços atendidos de cada profissional em Equipe e horários. Novos profissionais começam sem vínculos; novos serviços precisam ser vinculados à equipe. A migração preserva as combinações anteriores e as reservas existentes. Retirar um serviço com atendimento confirmado futuro ou em andamento exige resolver esse atendimento primeiro. Preço e duração continuam definidos no catálogo.
