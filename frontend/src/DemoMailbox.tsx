import { useEffect, useState } from 'react';
import { api, errorMessage } from './api';
import { Message } from './components';
interface Mail { id: string; purpose: string; to: string; subject: string; text: string; actionUrl: string; expiresAt: string }
export default function DemoMailbox({ refresh = 0, authenticated = false }: { refresh?: number; authenticated?: boolean }) {
  const [items, setItems] = useState<Mail[]>([]);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setError('');
    api<Mail[]>('/api/auth/demo-mailbox', { signal: controller.signal }).then(items => { if (!controller.signal.aborted) setItems(items); })
      .catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); });
    return () => controller.abort();
  }, [refresh, retry]);
  async function prepare() {
    setPending(true); setError('');
    try { if (authenticated) await api('/api/auth/demo-mailbox/open', { method: 'POST' }); setRetry(value => value + 1); }
    catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  return <section className="demo-mailbox" aria-label="Caixa de e-mails de teste"><h3>Caixa de e-mails de teste</h3><p>Nenhum e-mail real é enviado neste modo. Esta caixa mostra apenas mensagens da conta vinculada a este navegador; não comprova acesso a um endereço de e-mail real. O acesso à caixa dura uma hora. As mensagens ficam protegidas no banco até o fim de sua validade; sua preservação depende do armazenamento desta instalação.</p>
    <button type="button" className="button secondary" disabled={pending} onClick={() => void prepare()}>{pending ? 'Atualizando caixa…' : authenticated ? 'Abrir ou atualizar caixa de teste' : 'Atualizar caixa de teste'}</button>
    {error && <Message>{error}</Message>}
    {!items.length ? <p className="field-note">Nenhuma mensagem disponível. Use o mesmo navegador do cadastro. Se a caixa expirou, entre na conta para reabri-la e solicite outro link.</p> : <ul>{items.map(mail => <li key={mail.id}><strong>{mail.subject}</strong><small>Para: {mail.to}</small><p>{mail.text}</p>{mail.actionUrl && <a className="button primary" href={mail.actionUrl}>{mail.purpose === 'VERIFY' ? 'Abrir confirmação de e-mail' : 'Abrir recuperação de senha'}</a>}</li>)}</ul>}
  </section>;
}
