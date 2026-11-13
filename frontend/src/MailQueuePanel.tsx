import { useEffect, useRef, useState } from 'react';
import { api, ApiError, errorMessage } from './api';
import { Message } from './components';
import './mail-queue.css';

interface MailItem { id: string; purpose: string; mode: string; status: string; attempts: number; manualRetries: number; createdAt: string; expiresAt: string; nextAttemptAt: string | null; acceptedAt: string | null; lastError: string | null; canRetry: boolean }
export interface MailMonitor { mode: string; counts: Record<string, number>; oldestPendingAt: string | null; lastCheckAt: string | null; health: string; messages: MailItem[] }
const purposes: Record<string, string> = { VERIFY: 'Confirmação de e-mail', RESET: 'Recuperação de senha', CHANGED: 'Aviso de senha alterada' };
const states: Record<string, string> = { PENDING: 'Aguardando tentativa', PROCESSING: 'Em processamento', ACCEPTED: 'Aceito pelo serviço', FAILED: 'Falha no envio', EXPIRED: 'Prazo encerrado', CANCELLED: 'Link substituído ou utilizado' };
const reasons: Record<string, string> = { NETWORK: 'Falha de conexão. Uma nova tentativa pode ser agendada.', TEMPORARY: 'O serviço de envio está temporariamente indisponível.', RATE_LIMIT: 'O serviço pediu um intervalo antes da próxima tentativa.', AUTH: 'O remetente ou a autorização de envio precisa ser conferido.', CONFLICT: 'O serviço não aceitou repetir esta solicitação. Solicite um novo link.', REJECTED: 'O serviço recusou a mensagem. Confira a configuração do envio.', INTERRUPTED: 'O processamento anterior foi interrompido antes da confirmação.', ENCRYPTION: 'O conteúdo protegido não pôde ser validado. O responsável pela instalação precisa conferir o armazenamento.', SUPERSEDED: 'Este link já foi substituído ou utilizado.' };

export default function MailQueuePanel({ online, onDirty, onUnauthorized, zoneId = 'America/Sao_Paulo' }: { zoneId?: string; online: boolean; onDirty: (dirty: boolean) => void; onUnauthorized: () => void }) {
  const timestamp = (value: string | null) => value ? new Date(value).toLocaleString('pt-BR', { timeZone: zoneId }) : '—';
  const [data, setData] = useState<MailMonitor | null>(null);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const unauthorized = useRef(onUnauthorized); unauthorized.current = onUnauthorized;
  useEffect(() => { onDirty(pending); return () => onDirty(false); }, [pending, onDirty]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('');
    if (!online) { setData(null); setLoading(false); return; }
    api<MailMonitor>('/api/admin/emails' + (status ? '?status=' + status : ''), { signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(error => { if (!controller.signal.aborted) { setData(null); setError(errorMessage(error)); if (error instanceof ApiError && error.status === 401) unauthorized.current(); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [status, refresh, online]);
  useEffect(() => {
    if (!online || pending) return;
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') setRefresh(value => value + 1); }, 10000);
    return () => window.clearInterval(timer);
  }, [online, pending]);
  async function retry(id: string) {
    setPending(true); setError(''); setSuccess('');
    try { await api('/api/admin/emails/' + id + '/retry', { method: 'POST' }); setSuccess('Nova tentativa agendada.'); setRefresh(value => value + 1); }
    catch (error) { setError(errorMessage(error)); if (error instanceof ApiError && error.status === 401) unauthorized.current(); }
    finally { setPending(false); }
  }
  return <section className="agenda-panel mail-monitor"><div className="mail-monitor-heading"><div><h2>E-mails e envios</h2><p>Acompanhe as mensagens de acesso da sua empresa nos últimos sete dias.</p></div><button className="button secondary" disabled={!online || loading || pending} onClick={() => setRefresh(value => value + 1)}>Atualizar envios</button></div>
    {!online && <Message>Reconecte para consultar o acompanhamento dos e-mails.</Message>}
    {error && <Message>{error}</Message>}{success && <Message kind="success">{success}</Message>}
    {loading && <p role="status">Consultando os envios…</p>}
    {data && <><div className="mail-health" role="status"><strong>{data.health === 'ATTENTION' ? 'O envio precisa de atenção' : data.mode === 'demo' ? 'Caixa de teste ativa' : data.mode === 'disabled' ? 'Envio desativado nesta instalação' : 'Processamento em funcionamento'}</strong><span>Última verificação: {timestamp(data.lastCheckAt)}</span></div>
      {data.health === 'ATTENTION' && <Message>Há falhas, atraso ou falta de atualização do processamento. Confira os registros abaixo e a configuração da instalação.</Message>}
      {data.mode === 'demo' && <p className="field-note">Esta instalação usa mensagens simuladas. Nenhum e-mail real é enviado.</p>}
      <div className="mail-stats">{[['PENDING', 'Aguardando'], ['PROCESSING', 'Processando'], ['ACCEPTED', data.mode === 'demo' ? 'Na caixa de teste' : 'Aceitos'], ['FAILED', 'Com falha']].map(([key, label]) => <div key={key}><strong>{data.counts[key] ?? 0}</strong><span>{label}</span></div>)}</div>
      <div className="mail-filter"><label htmlFor="mail-status">Situação dos envios</label><select id="mail-status" value={status} disabled={pending} onChange={event => { setData(null); setStatus(event.target.value); }}>{[['', 'Todas as situações'], ...Object.entries(states)].map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div>
      {data.oldestPendingAt && <p className="field-note">Pendente mais antigo: {timestamp(data.oldestPendingAt)}</p>}
      {!data.messages.length ? <p>Nenhum envio nesta situação.</p> : <ul className="mail-history">{data.messages.map(item => <li key={item.id}><div className="mail-row-title"><h3>{purposes[item.purpose] ?? 'Mensagem de acesso'}</h3><span className={'mail-state mail-state-' + item.status.toLowerCase()}>{item.mode === 'demo' && item.status === 'ACCEPTED' ? 'Disponível na caixa de teste' : states[item.status] ?? item.status}</span></div>
        <dl><dt>Criado em</dt><dd>{timestamp(item.createdAt)}</dd><dt>Tentativas do ciclo</dt><dd>{item.attempts}</dd>{item.nextAttemptAt && <><dt>{item.status === 'FAILED' ? 'Espera do serviço até' : 'Próxima tentativa'}</dt><dd>{timestamp(item.nextAttemptAt)}</dd></>}{item.acceptedAt && <><dt>{item.mode === 'demo' ? 'Disponível desde' : 'Aceito em'}</dt><dd>{timestamp(item.acceptedAt)}</dd></>}{item.manualRetries > 0 && <><dt>Reagendamentos</dt><dd>{item.manualRetries}</dd></>}</dl>
        {item.lastError && <p>{reasons[item.lastError] ?? 'O envio não foi concluído. Confira a configuração ou solicite outro link.'}</p>}
        {item.canRetry && <button className="button secondary" disabled={!online || pending} onClick={() => void retry(item.id)}>{pending ? 'Agendando tentativa…' : 'Tentar este envio novamente'}</button>}
      </li>)}</ul>}
      <p className="field-note">São exibidos até 50 registros. Aceito pelo serviço significa que ele recebeu a mensagem para envio; a chegada à caixa de entrada depende do provedor.</p>
    </>}
  </section>;
}
