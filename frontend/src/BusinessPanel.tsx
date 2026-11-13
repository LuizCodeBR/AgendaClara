import { useState } from 'react';
import { api, errorMessage } from './api';
import { Message } from './components';
import type { BusinessSetup } from './types';
import './registration.css';

export default function BusinessPanel({ state, online, onUpdated, onConfigure }: {
  state: BusinessSetup; online: boolean; onUpdated: (state: BusinessSetup) => void;
  onConfigure: (tab: 'services' | 'team' | 'dates') => void;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  async function publish() {
    setPending(true); setError('');
    try { onUpdated(await api<BusinessSetup>('/api/admin/business/publication', { method: 'PUT', body: JSON.stringify({ published: !state.published, version: state.version }) })); }
    catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  return <section className="agenda-panel"><span className="eyebrow">{state.published ? 'PÁGINA PUBLICADA' : 'RASCUNHO PRIVADO'}</span><h2>Página de reservas</h2><p>{state.published ? 'Sua empresa está na lista pública e pode receber reservas pelo link.' : 'Sua empresa ainda não aparece na lista pública. Prepare o atendimento e publique quando estiver pronto.'}</p>
    <dl className="business-details"><dt>Empresa</dt><dd>{state.business.name}</dd><dt>Área</dt><dd>{state.business.category}</dd><dt>Local público</dt><dd>{state.business.address}</dd><dt>Fuso horário</dt><dd>{state.business.zoneId}</dd></dl>
    <ul className="business-checklist">
      <li><span>{state.services > 0 ? '✓' : '○'} Serviços cadastrados: {state.services}</span><button className="button quiet" disabled={pending} onClick={() => onConfigure('services')}>Configurar serviços</button></li>
      <li><span>{state.professionals > 0 ? '✓' : '○'} Profissionais ativos: {state.professionals}</span><button className="button quiet" disabled={pending} onClick={() => onConfigure('team')}>Configurar equipe</button></li>
      <li><span>{state.hasHours ? '✓ Horários de atendimento configurados' : '○ Falta configurar horários de atendimento'}</span><button className="button quiet" disabled={pending} onClick={() => onConfigure('team')}>Configurar expediente</button></li>
    </ul>
    <p className="field-note">Para publicar, cadastre um serviço, um profissional ativo e um expediente semanal ou um horário especial nos próximos 60 dias. A disponibilidade real também considera reservas, folgas e bloqueios.</p>
    {state.published && <div className="business-share"><label htmlFor="business-share-url">Link para seus clientes</label><input id="business-share-url" readOnly value={window.location.origin + '/#/agendar/' + encodeURIComponent(state.business.slug)} onFocus={event => event.currentTarget.select()} /><small>Copie e compartilhe este link. Ele usa o endereço desta instalação.</small></div>}
    {!state.published && !state.ready && <Message>Conclua os três itens acima para liberar a publicação.</Message>}
    {error && <Message>{error}</Message>}
    <button className="button primary" disabled={pending || !online || (!state.published && !state.ready)} onClick={() => void publish()}>{pending ? 'Atualizando publicação…' : state.published ? 'Ocultar página de reservas' : 'Publicar página de reservas'}</button>
    <p className="field-note">Ocultar a página impede novas reservas públicas. Os atendimentos já confirmados continuam no painel.</p>
  </section>;
}
