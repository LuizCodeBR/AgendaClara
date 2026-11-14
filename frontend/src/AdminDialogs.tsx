import { useState } from 'react';
import type { FormEvent } from 'react';
import { CalendarDays, Clock3 } from 'lucide-react';
import { api, errorMessage } from './api';
import { Message, Modal } from './components';
import { DatePicker, TimeSlots, useAvailability } from './availability';
import { dateInZone, dayLabel, localDateTime, plusDays, time, todayInZone } from './format';
import type { Appointment, Catalog, Slot } from './types';

export function RescheduleDialog({ appointment, catalog, onClose, onSaved, online }: { appointment: Appointment; catalog: Catalog; onClose: () => void; onSaved: () => void; online: boolean }) {
  const zone = catalog.business.zoneId;
  const [date, setDate] = useState(catalog.suggestedDate);
  const [slot, setSlot] = useState<Slot | null>(null); const [error, setError] = useState(''); const [pending, setPending] = useState(false);
  const availability = useAvailability(catalog.business.slug, appointment.professionalId, appointment.serviceId, date);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (!slot || pending) return; setPending(true); setError('');
    try {
      await api('/api/admin/appointments/' + appointment.id + '/reschedule', { method: 'PATCH', body: JSON.stringify({ startsAt: slot.startsAt, version:appointment.version }) });
      onSaved();
    } catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  return <Modal title="Encontrar um novo horário" busy={pending} onClose={onClose}><p className="modal-description">{appointment.customerName} · {appointment.serviceName}<br /><CalendarDays size={13} /> Atual: {dayLabel(dateInZone(appointment.startsAt, zone))}, {time(appointment.startsAt, zone)}</p><form onSubmit={event => void submit(event)}><fieldset disabled={pending} className="dialog-fields"><DatePicker date={date} onChange={value => { setDate(value); setSlot(null); }} today={todayInZone(zone)} max={plusDays(todayInZone(zone), catalog.horizonDays)} /><TimeSlots {...availability} selected={slot?.startsAt ?? ''} onSelect={setSlot} zone={zone} />{error && <Message>{error}</Message>}<button className="button primary full" disabled={!slot || pending || !online}>{pending ? 'Salvando…' : 'Confirmar remarcação'}</button></fieldset></form></Modal>;
}

export function BlockDialog({ catalog, date, onClose, onSaved, online }: { catalog: Catalog; date: string; onClose: () => void; onSaved: () => void; online: boolean }) {
  const [professionalId, setProfessionalId] = useState(catalog.professionals[0]?.id ?? '');
  const [start, setStart] = useState('13:00'); const [end, setEnd] = useState('14:00'); const [reason, setReason] = useState('');
  const [error, setError] = useState(''); const [pending, setPending] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (pending) return; setPending(true); setError('');
    try {
      await api('/api/admin/blocks', { method: 'POST', body: JSON.stringify({ professionalId, startsAt: localDateTime(date, start, catalog.business.zoneId), endsAt: localDateTime(date, end, catalog.business.zoneId), reason }) });
      onSaved();
    } catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  return <Modal title="Reservar um período para você" busy={pending} onClose={onClose}><p className="modal-description"><Clock3 size={14} /> {dayLabel(date)}. Esse período ficará indisponível para reservas.</p><form className="stack-form" onSubmit={event => void submit(event)}><fieldset disabled={pending} className="dialog-fields stack-form"><label>Profissional<select value={professionalId} onChange={event => setProfessionalId(event.target.value)}>{catalog.professionals.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><div className="form-row"><label>Início<input type="time" required step={900} value={start} onChange={event => setStart(event.target.value)} /></label><label>Fim<input type="time" required step={900} value={end} onChange={event => setEnd(event.target.value)} /></label></div><label>Motivo<input required maxLength={160} placeholder="Ex.: reunião, compromisso pessoal" value={reason} onChange={event => setReason(event.target.value)} /></label>{error && <Message>{error}</Message>}<button className="button primary full" disabled={pending || !online}>{pending ? 'Salvando…' : 'Bloquear período'}</button></fieldset></form></Modal>;
}
