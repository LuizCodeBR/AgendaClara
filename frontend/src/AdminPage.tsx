import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, CalendarDays, Check, ChevronLeft, ChevronRight, Clock3, ExternalLink, LayoutDashboard, LogOut, Plus, RefreshCw, Scissors, ShieldCheck, Users, X } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { Message, Modal } from './components';
import { dayLabel, initials, money, plusDays, time, todayInZone } from './format';
import type { Appointment, Business, Catalog, CurrentUser, DayAgenda } from './types';
import Login from './Login';
import { BlockDialog, RescheduleDialog } from './AdminDialogs';

export default function AdminPage({ user, onUser, demo, online, onBook }: { user: CurrentUser | null; onUser: (user: CurrentUser | null) => void; demo: boolean; online: boolean; onBook: (business: Business) => void }) {
  if (!user) return <Login onUser={onUser} demo={demo} online={online} />;
  return <Dashboard key={user.business.id} user={user} onUser={onUser} online={online} onBook={onBook} />;
}
function Dashboard({ user, onUser, online, onBook }: { user: CurrentUser; onUser: (user: CurrentUser | null) => void; online: boolean; onBook: (business: Business) => void }) {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [date, setDate] = useState('');
  const [agenda, setAgenda] = useState<DayAgenda | null>(null);
  const [tab, setTab] = useState<'agenda' | 'services'>('agenda');
  const [filter, setFilter] = useState('all');
  const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const [refresh, setRefresh] = useState(0); const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<Appointment | null>(null);
  const [moveTarget, setMoveTarget] = useState<Appointment | null>(null);
  const [blockOpen, setBlockOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    api<Catalog>('/api/admin/catalog', { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      setCatalog(result); setDate(current => current || result.suggestedDate);
    }).catch(error => { if (!controller.signal.aborted) { setError(errorMessage(error)); if (error instanceof ApiError && error.status === 401) onUser(null); } });
    return () => controller.abort();
  }, [user.business.id, refresh, onUser]);
  useEffect(() => {
    if (!date) return;
    const controller = new AbortController(); setLoading(true); setError('');
    api<DayAgenda>('/api/admin/agenda?date=' + date, { signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setAgenda(result); })
      .catch(error => { if (!controller.signal.aborted) { setError(errorMessage(error)); if (error instanceof ApiError && error.status === 401) onUser(null); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [date, refresh, onUser]);

  async function run(action: () => Promise<unknown>, text: string) {
    setPending(true); setError(''); setSuccess('');
    try { await action(); setSuccess(text); setRefresh(value => value + 1); setCancelTarget(null); }
    catch (error) { setError(errorMessage(error)); if (error instanceof ApiError && error.status === 401) onUser(null); }
    finally { setPending(false); }
  }
  const reload = (text: string) => { setSuccess(text); setRefresh(value => value + 1); setMoveTarget(null); setBlockOpen(false); };
  const appointments = agenda?.appointments ?? [];
  const confirmed = appointments.filter(item => item.status === 'BOOKED');
  const visible = appointments.filter(item => filter === 'all' || item.status === filter);
  const zone = user.business.zoneId;

  return <div className="admin-layout">
    <aside className="admin-sidebar"><span className="eyebrow">ESPAÇO DO NEGÓCIO</span><div className="admin-business-icon"><Scissors size={26} /></div><h2>{user.business.name}</h2><span className="business-category">{user.business.category}</span>
      <nav aria-label="Painel do negócio"><button className={tab === 'agenda' ? 'active' : ''} onClick={() => setTab('agenda')}><LayoutDashboard size={18} /> Minha agenda</button><button className={tab === 'services' ? 'active' : ''} onClick={() => setTab('services')}><Scissors size={18} /> Serviços</button></nav>
      <button className="public-link" onClick={() => onBook(user.business)}>Ver página de reservas <ExternalLink size={14} /></button>
      <div className="admin-account"><span className="initial-avatar">{initials(user.name)}</span><div><strong>{user.name}</strong><small>Responsável</small></div><button aria-label="Sair do painel" className="icon-button" disabled={pending} onClick={() => void run(async () => { await api('/api/auth/logout', { method: 'POST' }); onUser(null); }, '')}><LogOut size={18} /></button></div>
    </aside>
    <div className="admin-content"><div className="admin-page-title"><div><span className="eyebrow">OLÁ, {user.name.toUpperCase()}</span><h1>{tab === 'agenda' ? 'Seu dia, com clareza.' : 'Cuidado em cada serviço.'}</h1><p>{tab === 'agenda' ? 'Organize os atendimentos e abra espaço para o que importa.' : 'Seu catálogo aparece na página de reservas do estabelecimento.'}</p></div><button className="button secondary" onClick={() => onBook(user.business)}>Nova reserva <ArrowRight size={16} /></button></div>
      {error && <Message>{error}</Message>}{success && <Message kind="success">{success}</Message>}
      {tab === 'services' ? catalog && <ServicesPanel catalog={catalog} online={online} onSaved={() => reload('Serviço adicionado ao catálogo.')} /> : <>
        <div className="stats-grid"><div className="stat-card"><span><CalendarDays size={16} /> Agendamentos</span><strong>{confirmed.length.toString().padStart(2, '0')}</strong><small>confirmados para o dia</small></div><div className="stat-card"><span><Users size={16} /> Profissionais</span><strong>{catalog?.professionals.length.toString().padStart(2, '0') ?? '—'}</strong><small>na sua equipe</small></div><div className="stat-card revenue"><span><Check size={16} /> Valor previsto</span><strong>{money(confirmed.reduce((sum, item) => sum + item.priceCents, 0))}</strong><small>nos serviços confirmados</small></div></div>
        <section className="agenda-panel"><div className="agenda-toolbar"><div><span className="eyebrow">SUA AGENDA</span><h2>{date ? dayLabel(date) : 'Carregando…'}</h2></div><div className="agenda-date-controls"><button className="icon-button" aria-label="Dia anterior" onClick={() => setDate(plusDays(date, -1))} disabled={!date}><ChevronLeft size={18} /></button><input type="date" aria-label="Data da agenda" value={date} onChange={event => { if (event.target.value) setDate(event.target.value); }} /><button className="icon-button" aria-label="Próximo dia" onClick={() => setDate(plusDays(date, 1))} disabled={!date}><ChevronRight size={18} /></button></div></div>
          <div className="agenda-filters"><div className="filter-tabs" aria-label="Filtrar agendamentos">{[{ value: 'all', label: 'Todos' }, { value: 'BOOKED', label: 'Confirmados' }, { value: 'CANCELLED', label: 'Cancelados' }].map(item => <button key={item.value} className={filter === item.value ? 'active' : ''} aria-pressed={filter === item.value} onClick={() => setFilter(item.value)}>{item.label}</button>)}</div><button className="button quiet" onClick={() => setBlockOpen(true)} disabled={!catalog || !date || date < todayInZone(zone) || !online}><Plus size={15} /> Bloquear período</button></div>
          {loading ? <div className="slots-state" role="status">Atualizando sua agenda…</div> : !visible.length ? <div className="agenda-empty"><CalendarDays size={32} strokeWidth={1.2} /><h3>Um espaço livre por aqui.</h3><p>Nenhum agendamento neste filtro. Compartilhe sua página para receber reservas.</p></div> : <div className="appointment-list">{visible.map(item => <article className={'appointment-card ' + (item.status === 'CANCELLED' ? 'cancelled' : '')} key={item.id}><div className="appointment-time"><strong>{time(item.startsAt, zone)}</strong><span>{time(item.endsAt, zone)}</span></div><div className="appointment-customer"><span className="initial-avatar">{initials(item.customerName)}</span><div><h3>{item.customerName}</h3><p>{item.serviceName} <span>·</span> {item.professionalName}</p><small>{item.customerPhone} <span>·</span> {item.reference}</small></div></div><div className="appointment-status"><span className={'status-badge ' + (item.status === 'BOOKED' ? 'booked' : '')}>{item.status === 'BOOKED' ? 'Confirmado' : 'Cancelado'}</span><strong>{money(item.priceCents)}</strong></div>{item.status === 'BOOKED' && new Date(item.startsAt).getTime() > Date.now() && <div className="appointment-actions"><button className="icon-button" title="Remarcar" aria-label={'Remarcar ' + item.customerName} disabled={!online || pending} onClick={() => setMoveTarget(item)}><RefreshCw size={16} /></button><button className="icon-button" title="Cancelar" aria-label={'Cancelar ' + item.customerName} disabled={!online || pending} onClick={() => setCancelTarget(item)}><X size={17} /></button></div>}</article>)}</div>}
          {!!agenda?.blocks.length && <div className="blocks-list"><h3><Clock3 size={16} /> Períodos bloqueados</h3>{agenda.blocks.map(item => <div className="block-row" key={item.id}><span><strong>{time(item.startsAt, zone)} – {time(item.endsAt, zone)}</strong> {catalog?.professionals.find(person => person.id === item.professionalId)?.name}<small>{item.reason}</small></span><button className="button quiet" disabled={pending || !online} onClick={() => void run(() => api('/api/admin/blocks/' + item.id, { method: 'DELETE' }), 'Período liberado.')}>Remover bloqueio</button></div>)}</div>}
        </section><p className="admin-security-note"><ShieldCheck size={14} /> Você está visualizando apenas os dados de {user.business.name}.</p>
      </>}
    </div>
    {cancelTarget && <Modal title="Cancelar este agendamento?" onClose={() => setCancelTarget(null)}><p className="modal-description">{cancelTarget.customerName} · {cancelTarget.serviceName}<br />{time(cancelTarget.startsAt, zone)} com {cancelTarget.professionalName}. O horário será liberado para novas reservas.</p><div className="modal-actions"><button className="button secondary" onClick={() => setCancelTarget(null)}>Manter reserva</button><button className="button danger" disabled={pending || !online} onClick={() => void run(() => api('/api/admin/appointments/' + cancelTarget.id + '/cancel', { method: 'POST' }), 'Agendamento cancelado. O horário foi liberado.')}>{pending ? 'Cancelando…' : 'Sim, cancelar'}</button></div>{error && <Message>{error}</Message>}</Modal>}
    {moveTarget && catalog && <RescheduleDialog appointment={moveTarget} catalog={catalog} online={online} onClose={() => setMoveTarget(null)} onSaved={() => reload('Agendamento remarcado.')} />}
    {blockOpen && catalog && <BlockDialog catalog={catalog} date={date} online={online} onClose={() => setBlockOpen(false)} onSaved={() => reload('Período bloqueado.')} />}
  </div>;
}
function ServicesPanel({ catalog, online, onSaved }: { catalog: Catalog; online: boolean; onSaved: () => void }) {
  const [name, setName] = useState(''); const [description, setDescription] = useState('');
  const [duration, setDuration] = useState(30); const [price, setPrice] = useState('');
  const [error, setError] = useState(''); const [pending, setPending] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError('');
    try {
      await api('/api/admin/services', { method: 'POST', body: JSON.stringify({ name, description, durationMinutes: duration, priceCents: Math.round(Number(price) * 100) }) });
      setName(''); setDescription(''); setPrice(''); onSaved();
    } catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  return <div className="services-admin"><section className="agenda-panel"><h2>Seu catálogo</h2>{catalog.services.map(service => <div className="catalog-row" key={service.id}><Scissors size={18} /><div><strong>{service.name}</strong><p>{service.description}</p><small>{service.durationMinutes} minutos</small></div><strong>{money(service.priceCents)}</strong></div>)}</section><section className="agenda-panel"><h2>Adicionar serviço</h2><form className="stack-form" onSubmit={event => void submit(event)}><label>Nome do serviço<input required maxLength={100} value={name} onChange={event => setName(event.target.value)} /></label><label>Descrição<input required maxLength={240} value={description} onChange={event => setDescription(event.target.value)} /></label><div className="form-row"><label>Duração<select value={duration} onChange={event => setDuration(Number(event.target.value))}>{Array.from({ length: 16 }, (_, i) => (i + 1) * 15).map(value => <option key={value} value={value}>{value} minutos</option>)}</select></label><label>Preço (R$)<input required type="number" min="0" max="10000" step="0.01" value={price} onChange={event => setPrice(event.target.value)} /></label></div>{error && <Message>{error}</Message>}<button className="button primary full" disabled={pending || !online}>{pending ? 'Adicionando…' : 'Adicionar ao catálogo'}<Plus size={16} /></button></form></section></div>;
}
