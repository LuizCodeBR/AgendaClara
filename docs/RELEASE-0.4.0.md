# Agenda Clara 0.4.0 — empresas e contas próprias

Crie uma empresa e a conta do responsável pela interface, configure catálogo/equipe/horários e publique sua própria página de reservas. O painel mantém os dados separados por empresa.

- Cadastro atômico, e-mail normalizado e único, senha BCrypt e repetição segura após falha de rede.
- Empresa começa vazia e em rascunho privado. Publicação exige configuração mínima e pode ser ocultada sem apagar atendimentos confirmados.
- Web responsiva, PWA instalável e pacote local usam a mesma aplicação.
- Migração V4 preserva empresas, publicação e contas anteriores.
- Novos testes de cadastro, concorrência, sessões, isolamento, publicação e atualização do banco; roteiro completo para desktop e celular emulado.

**Demonstração pública:** use apenas dados fictícios e senha exclusiva de teste. Contas e dados podem ser apagados quando o servidor gratuito reiniciar. Verificação de e-mail, recuperação de acesso e uso comercial ainda são etapas futuras.

**Pacote demo:** requer somente Java 21 a 27. Extraia em pasta gravável e execute iniciar-demo.cmd no Windows ou bash iniciar-demo.sh no Linux/macOS. O banco fica em data; mantenha o servidor aberto.

**Pacote fonte:** inclui documentação, testes, Maven Wrapper e iniciadores. Requer Java e Node compatíveis, descritos no README.

Consulte CADASTRO-EMPRESAS.md e VALIDACAO-0.4.0.md no repositório para escopo e resultados. O ZIP de código contém a documentação existente no commit da entrega; o relatório no ramo principal poderá receber os resultados finais da publicação.
