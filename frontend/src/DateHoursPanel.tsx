import { useCallback, useEffect, useId, useState } from 'react';
import type { FormEvent } from 'react';
import { CalendarDays, Plus, Save, Trash2 } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { Message, Modal } from './components';
import { dayLabel } from './format';
import type { DateCalendar, DateException, DayWindow, ProfessionalConfiguration } from './types';

const endpoint = (id: string) => '/api/admin/professionals/' + id + '/exceptions';
const cleanHours = (hours: DayWindow[]) => hours.map(item => ({ opensAt: item.opensAt.slice(0, 5), closesAt: item.closesAt.slice(0, 5) }));
const weekday = (date: string) => (new Date(date + 'T12:00:00Z').getUTCDay() + 6) % 7 + 1;
const weeklyFor = (calendar: DateCalendar, date: string) => cleanHours(calendar.weeklyHours.filter(item => item.weekday === weekday(date)));
type Draft = { reason: string; closed: boolean; hours: DayWindow[] };
function draftFor(calendar: DateCalendar, date: string): Draft {
  const existing = calendar.exceptions.find(item => item.date === date);
  const weekly = weeklyFor(calendar, date);
  return {
    reason: existing?.reason ?? '', closed: !existing?.hours.length,
    hours: cleanHours(existing?.hours.length ? existing.hours : weekly.length ? weekly : [{ opensAt: '09:00', closesAt: '12:00' }]),
  };
}
const contents = (draft: Draft) => ({ reason: draft.reason, hours: draft.closed ? [] : draft.hours });

export default function DateHoursPanel({ online, zoneId, onDirty, onUpdated }: {
  online: boolean; zoneId: string; onDirty: (dirty: boolean) => void; onUpdated: () => void;
}) {
  const professionalSelectId = useId();
  const [people, setPeople] = useState<ProfessionalConfiguration[]>([]);
  const [selected, setSelected] = useState('');
  const [locked, setLocked] = useState(false);
  const [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const lock = useCallback((value: boolean) => { setLocked(value); onDirty(value); }, [onDirty]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('');
    api<ProfessionalConfiguration[]>('/api/admin/professionals', { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      setPeople(result); setSelected(current => result.some(person => person.id === current) ? current : result[0]?.id ?? '');
    }).catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);
  if (loading) return <p role="status">Carregando profissionais…</p>;
  if (error) return <><Message>{error}</Message><button className="button secondary" onClick={() => setRevision(value => value + 1)}>Tentar novamente</button></>;
  if (!people.length) return <Message kind="info">Cadastre um profissional em Equipe e horários para configurar folgas e horários especiais.</Message>;
  return <section className="date-hours-section" aria-label="Folgas e horários especiais">
    <div className="team-heading"><div><h2>Folgas e horários especiais</h2><p>Feche uma data ou defina um expediente diferente. Cada exceção vale apenas para o profissional escolhido, no fuso {zoneId}.</p></div></div>
    <div className="date-person-label"><label htmlFor={professionalSelectId}>Profissional da exceção</label><select id={professionalSelectId} value={selected} disabled={locked} onChange={event => setSelected(event.target.value)}>{people.map(person => <option key={person.id} value={person.id}>{person.name}{person.active ? '' : ' · inativo'}</option>)}</select></div>
    {selected && <DateHoursEditor key={selected} professionalId={selected} online={online} onDirty={lock} onUpdated={onUpdated} />}
  </section>;
}

export function DateHoursEditor({ professionalId, online, onDirty, onUpdated }: {
  professionalId: string; online: boolean; onDirty: (dirty: boolean) => void; onUpdated: () => void;
}) {
  const [calendar, setCalendar] = useState<DateCalendar | null>(null);
  const [date, setDate] = useState('');
  const [draft, setDraft] = useState<Draft | null>(null);
  const [loading, setLoading] = useState(true), [pending, setPending] = useState(false);
  const [error, setError] = useState(''), [conflict, setConflict] = useState(false);
  const [revision, setRevision] = useState(0), [confirmRemove, setConfirmRemove] = useState(false);
  const existing = calendar?.exceptions.find(item => item.date === date);
  const dirty = !!draft && JSON.stringify(contents(draft)) !== JSON.stringify({
    reason: existing?.reason ?? '', hours: cleanHours(existing?.hours ?? []),
  });
  useEffect(() => { onDirty(dirty || pending); return () => onDirty(false); }, [dirty, pending, onDirty]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(''); setConflict(false); setCalendar(null); setDraft(null);
    api<DateCalendar>(endpoint(professionalId), { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      const target = date >= result.firstDate && date <= result.lastDate ? date : result.firstDate;
      setCalendar(result); setDate(target); setDraft(draftFor(result, target));
    }).catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [professionalId, revision]);

  function selectDate(value: string) {
    if (!calendar || dirty || pending || !value) return;
    setDate(value); setDraft(draftFor(calendar, value)); setError(''); setConflict(false);
  }
  function saved(result: DateCalendar) {
    setCalendar(result); setDraft(draftFor(result, date)); setConfirmRemove(false); setError(''); setConflict(false); onUpdated();
  }
  function failed(error: unknown) {
    setError(errorMessage(error)); setConflict(error instanceof ApiError && error.status === 409);
  }
  async function save(event: FormEvent) {
    event.preventDefault(); if (!calendar || !draft || !dirty || pending || !online) return;
    setPending(true); setError(''); setConflict(false);
    try { saved(await api<DateCalendar>(endpoint(professionalId) + '/' + date, {
      method: 'PUT', body: JSON.stringify({ version: calendar.version, ...contents(draft) }),
    })); } catch (error) { failed(error); } finally { setPending(false); }
  }
  async function remove() {
    if (!calendar || pending || !online) return;
    setPending(true); setError(''); setConflict(false);
    try { saved(await api<DateCalendar>(endpoint(professionalId) + '/' + date + '?version=' + calendar.version, { method: 'DELETE' })); }
    catch (error) { setConfirmRemove(false); failed(error); } finally { setPending(false); }
  }

  if (loading) return <p role="status">Carregando exceções de expediente…</p>;
  if (!calendar || !draft) return <><Message>{error || 'Não foi possível abrir os horários.'}</Message><button className="button secondary" onClick={() => setRevision(value => value + 1)}>Tentar novamente</button></>;
  const weekly = weeklyFor(calendar, date);
  const describe = (entry: DateException) => entry.hours.length ? cleanHours(entry.hours).map(item => item.opensAt + '–' + item.closesAt).join(' · ') : 'Dia fechado';
  return <div className="date-hours-layout">
    <aside className="date-hours-list"><h3>Datas configuradas</h3>
      {!calendar.exceptions.length && <p className="team-help">Nenhuma exceção cadastrada. Escolha uma data para começar.</p>}
      {calendar.exceptions.map(entry => <button className={'date-hours-card ' + (entry.date === date ? 'selected' : '')} type="button" key={entry.date}
        disabled={dirty || pending} aria-pressed={entry.date === date} onClick={() => selectDate(entry.date)}>
        <strong>{dayLabel(entry.date)}</strong><span>{entry.reason}</span><small>{describe(entry)}</small>
      </button>)}
    </aside>
    <form className="agenda-panel date-hours-editor stack-form" aria-label="Editar exceção de expediente" onSubmit={event => void save(event)}>
      <h3><CalendarDays size={19} /> Uma data, um expediente</h3>
      <label>Data da exceção<input type="date" required min={calendar.firstDate} max={calendar.lastDate} value={date} disabled={dirty || pending} onChange={event => selectDate(event.target.value)} /></label>
      <p className="team-help">Expediente semanal nesta data: <strong>{weekly.length ? weekly.map(item => item.opensAt + '–' + item.closesAt).join(' · ') : 'fechado'}</strong>. Uma exceção substitui esses intervalos por completo. Os bloqueios da agenda continuam valendo.</p>
      <fieldset disabled={pending} className="stack-form dialog-fields">
        <label>Motivo da exceção<input required maxLength={160} placeholder="Ex.: feriado, folga ou atendimento especial" value={draft.reason} onChange={event => setDraft({ ...draft, reason: event.target.value })} /></label>
        <p className="team-help">O motivo fica no painel do estabelecimento.</p>
        <label className="checkbox-label"><input type="checkbox" checked={draft.closed} onChange={event => setDraft({ ...draft, closed: event.target.checked })} />Não atender nesta data</label>
        {!draft.closed && <div className="date-windows"><h4>Intervalos de atendimento</h4>{draft.hours.map((window, index) => <div className="working-window" key={index}>
          <label>Início<input type="time" required step={900} aria-label={'Exceção início ' + (index + 1)} value={window.opensAt} onChange={event => setDraft({ ...draft, hours: draft.hours.map((item, position) => position === index ? { ...item, opensAt: event.target.value } : item) })} /></label>
          <span aria-hidden="true">até</span><label>Fim<input type="time" required step={900} aria-label={'Exceção fim ' + (index + 1)} value={window.closesAt} onChange={event => setDraft({ ...draft, hours: draft.hours.map((item, position) => position === index ? { ...item, closesAt: event.target.value } : item) })} /></label>
          <button className="icon-button" type="button" aria-label={'Remover intervalo da exceção ' + (index + 1)} disabled={draft.hours.length === 1} onClick={() => setDraft({ ...draft, hours: draft.hours.filter((_, position) => position !== index) })}><Trash2 size={17} /></button>
        </div>)}
          <button className="button quiet" type="button" disabled={draft.hours.length >= 4} onClick={() => setDraft({ ...draft, hours: [...draft.hours, { opensAt: '18:00', closesAt: '19:00' }] })}><Plus size={15} />Adicionar intervalo da exceção</button>
          <p className="team-help">Use até quatro intervalos, em passos de 15 minutos, sem sobreposição.</p>
        </div>}
      </fieldset>
      {error && <Message>{error}</Message>}
      {conflict && <button type="button" className="button secondary" disabled={pending} onClick={() => setRevision(value => value + 1)}>Descartar rascunho e recarregar exceções</button>}
      <p className="team-help">Reservas confirmadas serão preservadas. Remarque ou cancele os atendimentos que ficariam fora do novo expediente antes de salvar ou remover uma exceção.</p>
      <div className="team-save"><span role="status">{dirty ? 'Alterações da exceção ainda não salvas' : existing ? 'Exceção cadastrada' : 'Esta data segue o expediente semanal'}</span>
        <button className="button secondary" type="button" disabled={!dirty || pending} onClick={() => { setDraft(draftFor(calendar, date)); setError(''); setConflict(false); }}>Descartar alterações da exceção</button>
        <button className="button primary" disabled={!dirty || !draft.reason.trim() || pending || !online}><Save size={16} />{pending ? 'Salvando…' : 'Salvar exceção'}</button>
      </div>
      {existing && <button className="button quiet" type="button" disabled={dirty || pending || !online} onClick={() => setConfirmRemove(true)}><Trash2 size={16} />Remover exceção e usar expediente semanal</button>}
    </form>
    {confirmRemove && <Modal title="Remover esta exceção?" busy={pending} onClose={() => setConfirmRemove(false)}>
      <p className="modal-description">O expediente semanal voltará a valer em {dayLabel(date)}. A remoção será recusada se deixar algum atendimento confirmado fora dos horários.</p>
      <div className="modal-actions"><button className="button secondary" disabled={pending} onClick={() => setConfirmRemove(false)}>Manter exceção</button><button className="button danger" disabled={pending || !online} onClick={() => void remove()}>{pending ? 'Removendo…' : 'Confirmar remoção da exceção'}</button></div>
    </Modal>}
  </div>;
}
