import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, Save, Trash2, Users } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { Message, Modal } from './components';
import type { ProfessionalConfiguration, WeeklyWindow } from './types';

export const weekdays = ['Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado', 'Domingo'];
const endpoint = '/api/admin/professionals';
const defaultHours = (weekday: number): WeeklyWindow[] => [{ weekday, opensAt: '09:00', closesAt: '12:00' }, { weekday, opensAt: '13:00', closesAt: '18:00' }];
const editable = (person: ProfessionalConfiguration) => ({ name: person.name, specialty: person.specialty, active: person.active, version: person.version, hours: person.hours.map(item => ({ ...item, opensAt: item.opensAt.slice(0, 5), closesAt: item.closesAt.slice(0, 5) })) });

export default function ProfessionalsPanel({ online, zoneId, onUpdated, onDirty }: { online: boolean; zoneId: string; onUpdated: () => void; onDirty: (dirty: boolean) => void }) {
  const [people, setPeople] = useState<ProfessionalConfiguration[]>([]);
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [creating, setCreating] = useState(false); const [dirty, setDirty] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('');
    api<ProfessionalConfiguration[]>(endpoint, { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      setPeople(result); setSelected(current => result.some(person => person.id === current) ? current : result[0]?.id ?? '');
    }).catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);
  useEffect(() => { onDirty(dirty); return () => onDirty(false); }, [dirty, onDirty]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  const person = people.find(item => item.id === selected);
  function saved(result: ProfessionalConfiguration) {
    setPeople(current => [...current.filter(item => item.id !== result.id), result].sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name)));
    setSelected(result.id); setDirty(false); setCreating(false); onUpdated();
  }
  return <section className="team-section" aria-label="Cadastro de profissionais">
    <div className="team-heading"><div><h2>Equipe e horários</h2><p>Defina o expediente de cada profissional no fuso {zoneId}.</p></div><button className="button primary" disabled={!online || dirty || loading} onClick={() => setCreating(true)}><Plus size={16} /> Cadastrar profissional</button></div>
    {error && <><Message>{error}</Message><button className="button secondary" onClick={() => setRevision(value => value + 1)}>Tentar novamente</button></>}
    {loading ? <p role="status">Carregando a equipe…</p> : <div className="team-grid">
      <div className="team-list" aria-label="Profissionais cadastrados">{people.map(item => <button key={item.id} className={'team-person ' + (item.id === selected ? 'selected' : '')} aria-pressed={item.id === selected} disabled={dirty && item.id !== selected} onClick={() => setSelected(item.id)}><Users size={18} /><span><strong>{item.name}</strong><small>{item.specialty}</small><span className={'status-badge ' + (item.active ? 'booked' : '')}>{item.active ? 'Ativo' : 'Inativo'}</span></span></button>)}{!people.length && <p>Nenhum profissional cadastrado. Comece adicionando alguém à equipe.</p>}</div>
      {person && <ProfessionalEditor key={person.id + ':' + person.version} person={person} online={online} onDirty={setDirty} onSaved={saved} onReload={() => { setDirty(false); setRevision(value => value + 1); }} />}
    </div>}
    {creating && <CreateProfessional online={online} onClose={() => setCreating(false)} onSaved={saved} />}
  </section>;
}

function CreateProfessional({ online, onClose, onSaved }: { online: boolean; onClose: () => void; onSaved: (person: ProfessionalConfiguration) => void }) {
  const [name, setName] = useState(''); const [specialty, setSpecialty] = useState('');
  const [pending, setPending] = useState(false); const [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError('');
    try { onSaved(await api<ProfessionalConfiguration>(endpoint, { method: 'POST', body: JSON.stringify({ name, specialty }) })); }
    catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  return <Modal title="Cadastrar profissional" busy={pending} onClose={() => { if (!pending) onClose(); }}><p className="modal-description">Este é um cadastro para receber agendamentos. O acesso ao painel continua sendo do responsável. Depois de cadastrar, configure os horários para liberar reservas.</p><form className="stack-form" onSubmit={event => void submit(event)}><fieldset disabled={pending}>
    <label>Nome do novo profissional<input required maxLength={100} value={name} onChange={event => setName(event.target.value)} /></label>
    <label>Especialidade do novo profissional<input required maxLength={160} value={specialty} onChange={event => setSpecialty(event.target.value)} /></label>
    {error && <Message>{error}</Message>}<div className="modal-actions"><button type="button" className="button secondary" onClick={onClose}>Voltar</button><button className="button primary" disabled={!online}>{pending ? 'Cadastrando…' : 'Cadastrar e configurar horários'}</button></div>
  </fieldset></form></Modal>;
}

export function ProfessionalEditor({ person, online, onDirty, onSaved, onReload }: { person: ProfessionalConfiguration; online: boolean; onDirty: (dirty: boolean) => void; onSaved: (person: ProfessionalConfiguration) => void; onReload: () => void }) {
  const [draft, setDraft] = useState(() => editable(person));
  const [pending, setPending] = useState(false); const [error, setError] = useState('');
  const [conflict, setConflict] = useState(false); const [success, setSuccess] = useState('');
  const dirty = JSON.stringify(draft) !== JSON.stringify(editable(person));
  useEffect(() => { onDirty(dirty); }, [dirty, onDirty]);
  function setDay(weekday: number, windows: WeeklyWindow[]) {
    setDraft(current => ({ ...current, hours: [...current.hours.filter(item => item.weekday !== weekday), ...windows].sort((a, b) => a.weekday - b.weekday) }));
    setSuccess('');
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError(''); setSuccess(''); setConflict(false);
    try {
      const result = await api<ProfessionalConfiguration>(endpoint + '/' + person.id, { method: 'PUT', body: JSON.stringify(draft) });
      setDraft(editable(result)); onSaved(result); setSuccess('Cadastro e expediente salvos. A página de reservas já usa os novos horários.');
    } catch (error) { setError(errorMessage(error)); setConflict(error instanceof ApiError && error.status === 409); }
    finally { setPending(false); }
  }
  return <form className="agenda-panel professional-editor stack-form" aria-label="Editar profissional e expediente" onSubmit={event => void submit(event)}>
    <h3>{person.name}</h3><p className="team-help">Reservas confirmadas serão preservadas. Para reduzir o expediente ou inativar alguém com atendimentos futuros, primeiro remarque ou cancele esses atendimentos na agenda.</p>
    <fieldset disabled={pending}><div className="form-row"><label>Nome do profissional<input required maxLength={100} value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })} /></label><label>Especialidade<input required maxLength={160} value={draft.specialty} onChange={event => setDraft({ ...draft, specialty: event.target.value })} /></label></div>
    <label className="checkbox-label"><input type="checkbox" checked={draft.active} onChange={event => setDraft({ ...draft, active: event.target.checked })} /> Profissional ativo para reservas</label>
    <div className="hours-heading"><h3>Horários semanais</h3><button type="button" className="button quiet" disabled={!draft.hours.some(item => item.weekday === 1)} onClick={() => {
      const monday = draft.hours.filter(item => item.weekday === 1);
      setDraft({ ...draft, hours: [...[1, 2, 3, 4, 5].flatMap(weekday => monday.map(item => ({ ...item, weekday }))), ...draft.hours.filter(item => item.weekday > 5)] });
    }}>Copiar segunda para terça a sexta</button></div>
    <p className="team-help">Até 4 intervalos por dia, em passos de 15 minutos. Dias sem intervalos ficam fechados. Horários contíguos são unidos ao salvar. Os bloqueios da agenda continuam valendo.</p>
    <div className="weekly-hours">{weekdays.map((day, index) => {
      const weekday = index + 1; const windows = draft.hours.filter(item => item.weekday === weekday);
      return <fieldset className="working-day" key={weekday}><legend>{day}</legend><label className="checkbox-label"><input type="checkbox" aria-label={'Atende ' + day} checked={windows.length > 0} onChange={event => setDay(weekday, event.target.checked ? defaultHours(weekday) : [])} />{windows.length ? 'Atendimento aberto' : 'Fechado'}</label>
        {windows.map((window, position) => <div className="working-window" key={position}><label>Início<input type="time" required step={900} aria-label={day + ' início ' + (position + 1)} value={window.opensAt} onChange={event => setDay(weekday, windows.map((item, i) => i === position ? { ...item, opensAt: event.target.value } : item))} /></label><span aria-hidden="true">até</span><label>Fim<input type="time" required step={900} aria-label={day + ' fim ' + (position + 1)} value={window.closesAt} onChange={event => setDay(weekday, windows.map((item, i) => i === position ? { ...item, closesAt: event.target.value } : item))} /></label><button type="button" className="icon-button" aria-label={'Remover intervalo ' + (position + 1) + ' de ' + day} onClick={() => setDay(weekday, windows.filter((_, i) => i !== position))}><Trash2 size={17} /></button></div>)}
        {windows.length > 0 && windows.length < 4 && <button type="button" className="button quiet" aria-label={'Adicionar intervalo em ' + day} onClick={() => setDay(weekday, [...windows, { weekday, opensAt: '18:00', closesAt: '19:00' }])}><Plus size={15} /> Adicionar intervalo</button>}
      </fieldset>;
    })}</div>
    </fieldset>
    {error && <Message>{error}</Message>}{success && <Message kind="success">{success}</Message>}
    {conflict && <button type="button" className="button secondary" onClick={onReload}>Descartar rascunho e recarregar dados</button>}
    <div className="team-save"><span role="status">{dirty ? 'Alterações ainda não salvas' : 'Cadastro atualizado'}</span><button type="button" className="button secondary" disabled={!dirty || pending} onClick={() => { setDraft(editable(person)); setError(''); setConflict(false); }}>Descartar alterações</button><button className="button primary" disabled={!dirty || !online || pending}><Save size={16} />{pending ? 'Salvando…' : 'Salvar cadastro e horários'}</button></div>
  </form>;
}
