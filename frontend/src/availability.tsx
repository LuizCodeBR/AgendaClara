import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Clock3, Sunrise, Sun } from 'lucide-react';
import { api, errorMessage } from './api';
import { dayLabel, plusDays, time } from './format';
import type { Availability, Slot } from './types';
import { Message } from './components';

export function useAvailability(slug: string, professionalId: string, serviceId: string, date: string, refresh = 0) {
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    setSlots([]); setError('');
    if (!slug || !professionalId || !serviceId || !date) return;
    const controller = new AbortController(); setLoading(true);
    const query = new URLSearchParams({ professionalId, serviceId, date });
    api<Availability>('/api/public/' + encodeURIComponent(slug) + '/availability?' + query, { signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setSlots(result.slots); })
      .catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [slug, professionalId, serviceId, date, refresh]);
  return { slots, loading, error };
}
export function DatePicker({ date, onChange, today, max }: { date: string; onChange: (date: string) => void; today: string; max: string }) {
  const [anchor, setAnchor] = useState(today);
  const dates = Array.from({ length: 6 }, (_, i) => plusDays(anchor, i)).filter(item => item <= max);
  return <div className="date-picker">
    <div className="date-picker-heading"><span className="subtle-label">Escolha o dia</span><label className="date-input-label">Outra data<input aria-label="Data do agendamento" type="date" min={today} max={max} value={date} onChange={event => { if (event.target.value) { onChange(event.target.value); setAnchor(event.target.value); } }} /></label></div>
    <div className="date-strip"><button className="icon-button date-arrow" type="button" aria-label="Dias anteriores" disabled={anchor <= today} onClick={() => setAnchor(plusDays(anchor, -6) < today ? today : plusDays(anchor, -6))}><ChevronLeft size={18} /></button>
      {dates.map(item => <button key={item} type="button" aria-pressed={date === item} className={'date-cell ' + (date === item ? 'selected' : '')} onClick={() => onChange(item)}><span>{dayLabel(item, true).split(',')[0].replace('.', '')}</span><strong>{item.slice(8)}</strong><span className="date-dot" /></button>)}
      <button className="icon-button date-arrow" type="button" aria-label="Próximos dias" disabled={plusDays(anchor, 6) > max} onClick={() => setAnchor(plusDays(anchor, 6))}><ChevronRight size={18} /></button>
    </div>
  </div>;
}
export function TimeSlots({ slots, loading, error, selected, onSelect, zone }: { slots: Slot[]; loading: boolean; error: string; selected: string; onSelect: (slot: Slot) => void; zone: string }) {
  if (loading) return <div className="slots-state" role="status"><Clock3 size={18} /> Consultando horários…</div>;
  if (error) return <Message>{error}</Message>;
  if (!slots.length) return <div className="slots-state empty"><CalendarEmpty /><strong>Sem horários disponíveis neste dia.</strong><span>Tente outra data ou outro profissional.</span></div>;
  const morning = slots.filter(slot => Number(time(slot.startsAt, zone).slice(0, 2)) < 12);
  const afternoon = slots.filter(slot => Number(time(slot.startsAt, zone).slice(0, 2)) >= 12);
  return <div className="time-slots">{[{ title: 'Manhã', icon: <Sunrise size={16} />, values: morning }, { title: 'Tarde', icon: <Sun size={16} />, values: afternoon }].filter(group => group.values.length).map(group => <div className="time-group" key={group.title}>
    <div className="time-group-title">{group.icon}{group.title}</div><div className="time-grid">{group.values.map(slot => <button type="button" key={slot.startsAt} className={'time-cell ' + (selected === slot.startsAt ? 'selected' : '')} aria-pressed={selected === slot.startsAt} onClick={() => onSelect(slot)}>{time(slot.startsAt, zone)}</button>)}</div>
  </div>)}</div>;
}
function CalendarEmpty() { return <Clock3 size={23} strokeWidth={1.3} />; }
