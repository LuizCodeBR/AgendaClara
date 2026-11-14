# Minha reserva — 0.11.0

O cliente pode consultar, cancelar ou remarcar seu próprio atendimento sem criar uma conta. Web, PWA e cópia local usam a mesma funcionalidade.

## Cliente

1. Faça uma reserva e guarde o **Link privado da reserva** exibido no comprovante.
2. Use **Gerenciar minha reserva** ou abra **Minha reserva** e cole o link.
3. Confira empresa, cliente, serviço, profissional, data, horário e valor.
4. Para remarcar, escolha **Remarcar minha reserva**, uma data e um horário disponível e confirme.
5. Para cancelar, escolha **Cancelar minha reserva** e confirme na janela.
6. **Encerrar acesso neste navegador** encerra somente a sessão daquela reserva.

O link é uma credencial: quem o possui pode realizar essas ações. Compartilhe somente com o cliente correspondente. O código público do comprovante, nome e telefone não permitem abrir a reserva. Se perder o link, peça outro ao estabelecimento.

O acesso vale até sete dias após o término do atendimento; acompanha o término atualizado em uma remarcação. Reservas canceladas ou já iniciadas ficam somente para consulta. A sessão dura no máximo uma hora; reabra o link se expirar. Não há renovação silenciosa nem acesso offline aos dados privados.

Desde a versão 0.11.0, a empresa pode definir prazos independentes de cancelamento e remarcação. Minha reserva mostra as condições registradas na confirmação e disponibiliza cada ação enquanto o respectivo prazo permitir. Alterar as regras da empresa não modifica reservas anteriores. Consulte [Regras de agendamento](REGRAS-AGENDAMENTO.md).

A remarcação preserva serviço, profissional, duração, preço e regras contratados. O novo início deve respeitar a antecedência mínima registrada; os prazos de cancelamento e remarcação são recalculados a partir dele. Valida expediente, folgas, bloqueios, vínculos e conflitos de horário. O estabelecimento precisa ter um profissional ativo com o serviço vinculado. Cliente e responsável devem atualizar a tela após um conflito de versão.

## Responsável

Na agenda, use **Acesso do cliente** no atendimento. A janela mostra se há acesso ativo.

- **Gerar novo link privado** fornece um novo link, invalida o anterior e encerra as sessões abertas com ele.
- **Revogar acesso do cliente** desativa o link e suas sessões.
- Reservas anteriores à versão 0.10.0 precisam que o responsável gere o primeiro link.
- O sistema não envia automaticamente esse link por e-mail, SMS ou WhatsApp. A entrega é manual nesta etapa.
- Para compartilhar com um celular, abra o site online e gere o link ali. Um link de localhost aponta para o próprio dispositivo; a cópia local tem banco independente.

A alteração de acesso também incrementa a versão do atendimento. Uma tela antiga não pode sobrescrever a ação seguinte inadvertidamente. Repetir a emissão com a mesma chave de tentativa recupera o mesmo link enquanto aquela emissão continua vigente.

## Proteção e concorrência

- O segredo inicial tem 256 bits, com HMAC-SHA256 e chave derivada por HKDF-SHA256, separada por finalidade da chave de criptografia da fila. O banco guarda apenas hashes dos segredos.
- O fragmento do link não é enviado no endereço HTTP. A interface o remove do endereço e envia o segredo no corpo de um POST com CSRF.
- Após abrir, o endereço contém somente o UUID não secreto da reserva. A autorização exige também o cookie; conhecer o UUID não concede acesso.
- O cookie é HttpOnly, SameSite=Strict e Secure no site HTTPS. Seu caminho fica restrito àquela reserva, permitindo abas independentes para reservas diferentes.
- Respostas privadas usam no-store. Não se grava segredo em localStorage ou sessionStorage; a PWA não armazena respostas da API.
- Uma reserva admite até oito sessões guardadas; novas aberturas removem sessões expiradas e substituem a mais antiga quando necessário.
- Escritas usam o mesmo bloqueio de profissional já aplicado a reservas, horários, bloqueios e vínculos. A credencial é conferida novamente depois de adquirir o bloqueio.
- A versão esperada é obrigatória nas alterações do cliente; conflito retorna 409. Cancelamento repetido e repetição imediata da mesma remarcação são seguros.
- A repetição da reserva pública nunca recupera um link que o responsável tenha substituído ou revogado.
- O acesso não autentica o usuário como responsável e não permite consultar outras reservas ou relatórios da empresa.
- Links permanecem secretos na memória da tela e no campo de cópia enquanto exibidos. Proteja o dispositivo e evite capturas que mostrem o campo.

## Atualização, chave e backups

A migração V8 acrescenta controle de versão e as tabelas appointment_access e appointment_customer_session. Preserva reservas e não inventa credenciais para registros antigos.

Encerre o servidor local e faça backup antes de atualizar. Mantenha banco e mail-outbox.key originais juntos. Instale o JAR novo em uma cópia de teste antes de substituir a instalação em uso. A ferramenta 0.11.0 aceita backups dos esquemas 6, 7, 8 e 9; o formato ACBK0001 permanece. Não execute uma versão antiga sobre o banco migrado.

A chave estável existente é suficiente no modo demo, no site e na cópia local. Em uma instalação própria com MAIL_MODE=disabled e sem chave, o agendamento continua funcionando, porém não oferece acesso privado; configure uma chave estável antes de usar essa função.

Não foi contratado serviço, adicionado provedor de mensagem ou programada rotina. Backups e limites continuam sob demanda.

## Verificação

- CustomerPortalTest, CustomerPortalConcurrencyTest e CustomerAccessLimitTest: sigilo, permissões, validade, revogação, versões, repetição e disputas.
- CustomerAccessMigrationTest: atualização do esquema 7 para 8 sem perda de histórico.
- BackupTest e PostgresBackupTest: vínculo, sessões, chave e sequência após restauração.
- Testes da interface: entrada, consulta, confirmação, remarcação, conflito, encerramento e emissão/revogação no painel.
- customer-reservation.spec.ts: navegador de computador e Pixel 7 emulado, incluindo duas reservas em abas independentes.
- scripts/customer-portal-smoke.mjs: teste HTTP completo com dados fictícios e limpeza de reservas e profissional de teste.
- O mesmo script aceita before/after com arquivo privado para conferir restauração e atualização; nunca publique esse arquivo.

Referências: [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html) e [OWASP — sessões](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
