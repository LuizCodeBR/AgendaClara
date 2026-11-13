import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api, errorMessage } from './api';
import { Message } from './components';
import DemoMailbox from './DemoMailbox';
import './registration.css';

export type AccessMode = 'verify-request' | 'verify' | 'forgot' | 'reset';
export default function AccountAccessPage({ mode, token, initialEmail, mailMode, online, authenticated, onLogin, onRequestVerification, onReset, onNavigationBlocked }: {
  mode: AccessMode; token: string; initialEmail: string; mailMode: string; online: boolean; authenticated: boolean;
  onLogin: (email: string) => void; onRequestVerification: () => void; onReset: () => void;
  onNavigationBlocked: (blocked: boolean) => void;
}) {
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [refresh, setRefresh] = useState(0);
  const requesting = mode === 'forgot' || mode === 'verify-request';
  const dirty = mode === 'reset' && !!(password || confirmation);
  useEffect(() => {
    const blocked = pending || dirty; onNavigationBlocked(blocked);
    const prevent = (event: BeforeUnloadEvent) => { if (blocked) event.preventDefault(); };
    window.addEventListener('beforeunload', prevent);
    return () => { onNavigationBlocked(false); window.removeEventListener('beforeunload', prevent); };
  }, [pending, dirty, onNavigationBlocked]);
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setSuccess('');
    if (mode === 'reset') {
      if ([...password].length < 15 || new TextEncoder().encode(password).length > 72) { setError('Use pelo menos 15 caracteres e até 72 bytes na senha. Acentos e emojis ocupam mais espaço.'); return; }
      if (password !== confirmation) { setError('As senhas precisam ser iguais.'); return; }
    }
    setPending(true);
    try {
      if (requesting && mailMode === 'demo' && authenticated) await api('/api/auth/demo-mailbox/open', { method: 'POST' });
      const path = mode === 'forgot' ? '/api/auth/password/request' : mode === 'verify-request' ? '/api/auth/email/request' : mode === 'verify' ? '/api/auth/email/confirm' : '/api/auth/password/reset';
      const result = await api<{ message: string }>(path, { method: 'POST', body: JSON.stringify(requesting ? { email } : mode === 'verify' ? { token } : { token, password }) });
      setPassword(''); setConfirmation(''); setSuccess(result.message); setRefresh(value => value + 1);
      if (mode === 'reset') onReset();
    } catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  const title = mode === 'forgot' ? 'Recuperar minha senha' : mode === 'verify-request' ? 'Confirmar meu e-mail' : mode === 'verify' ? 'Confirme seu e-mail' : 'Escolha uma nova senha';
  return <section className="access-card-page agenda-panel"><h1>{title}</h1>
    {requesting ? <><p>{mode === 'forgot' ? 'Informe o e-mail da conta para solicitar um link de recuperação. A solicitação não altera sua senha.' : 'Confirme o e-mail da sua conta para liberar a publicação da página de reservas. Você pode preparar o atendimento enquanto aguarda.'}</p>
      {mailMode === 'disabled' && <Message>O envio de e-mails ainda não foi configurado nesta instalação. O responsável pelo sistema precisa habilitá-lo para continuar.</Message>}
      <form className="stack-form" onSubmit={event => void submit(event)}><label htmlFor="access-email">E-mail da conta</label><input id="access-email" type="email" required maxLength={180} autoComplete="username" value={email} disabled={pending} onChange={event => setEmail(event.target.value)} />
        <p className="field-note">Por segurança, a resposta não informa se este e-mail está cadastrado. Aguarde um minuto entre solicitações. Confira também o spam quando o envio real estiver habilitado.</p><button className="button primary" disabled={pending || !online || mailMode === 'disabled'}>{pending ? 'Solicitando link…' : mode === 'forgot' ? 'Solicitar recuperação de senha' : 'Solicitar confirmação de e-mail'}</button></form></> : success ? <Message kind="success">{success}</Message> : !validToken ? <Message>Abra o link completo recebido na mensagem. Ele pode ter expirado ou já ter sido utilizado.</Message> : <><p>{mode === 'verify' ? 'Confirme somente se você solicitou este cadastro. Abrir o link não confirma o e-mail automaticamente.' : 'O link vale por 15 minutos e só pode ser usado uma vez. Depois da troca, será necessário entrar novamente em todos os aparelhos.'}</p>
      <form className="stack-form" onSubmit={event => void submit(event)}>{mode === 'reset' && <fieldset disabled={pending}><legend>Nova senha</legend><label htmlFor="access-password">Nova senha</label><input id="access-password" type="password" required maxLength={144} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} /><small>Pelo menos 15 caracteres e até 72 bytes. Prefira uma frase longa e exclusiva.</small><label htmlFor="access-confirmation">Confirme a nova senha</label><input id="access-confirmation" type="password" required maxLength={144} autoComplete="new-password" value={confirmation} onChange={event => setConfirmation(event.target.value)} /></fieldset>}
        <button className="button primary" disabled={pending || !online}>{pending ? 'Concluindo…' : mode === 'verify' ? 'Confirmar este e-mail' : 'Salvar nova senha'}</button></form></>}
    {error && <Message>{error}</Message>}{requesting && success && <Message kind="success">{success}</Message>}
    <div className="registration-actions"><button className="button quiet" disabled={pending} onClick={() => { setPassword(''); setConfirmation(''); onLogin(email); }}>{dirty ? 'Descartar nova senha e voltar' : 'Voltar ao painel'}</button>{!requesting && !success && <button className="button quiet" disabled={pending || dirty} onClick={onRequestVerification}>{mode === 'reset' ? 'Solicitar outro link de recuperação' : 'Solicitar outro link de confirmação'}</button>}</div>
    {requesting && mailMode === 'demo' && <DemoMailbox refresh={refresh} authenticated={authenticated} />}
  </section>;
}
