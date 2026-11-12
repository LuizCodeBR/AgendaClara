import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, CalendarDays, Check, CheckCircle2, ChevronDown, Clock3, MapPin, Scissors, ShieldCheck, Sparkles } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { dateInZone, dayLabel, initials, money, plusDays, time, todayInZone } from './format';
import type { Business, Catalog, Receipt, Slot } from './types';
import { Message } from './components';
import { DatePicker, TimeSlots, useAvailability } from './availability';

export default function BookingPage({ slug, businesses, onSelectBusiness, online }: { slug: string; businesses: Business[]; onSelectBusiness: (slug: string) => void; online: boolean }) {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [serviceId, setServiceId] = useState('');
  const [professionalId, setProfessionalId] = useState('');
  const [date, setDate] = useState('');
  const [slot, setSlot] = useState<Slot | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => { if (receipt) document.getElementById('booking-receipt')?.focus(); }, [receipt]);
  const request = useRef({ signature: '', key: '' });
  const availability = useAvailability(slug, professionalId, serviceId, date, refresh);

  useEffect(() => {
    const controller = new AbortController(); setLoading(true);
    api<Catalog>('/api/public/' + encodeURIComponent(slug) + '/catalog', { signal: controller.signal })
      .then(result => {
        if (controller.signal.aborted) return;
        setCatalog(result); setServiceId(result.services[0]?.id ?? '');
        setProfessionalId(result.professionals[0]?.id ?? ''); setDate(result.suggestedDate);
      }).catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [slug]);
  useEffect(() => { setSlot(null); }, [serviceId, professionalId, date, refresh]);

  const service = catalog?.services.find(item => item.id === serviceId);
  const professional = catalog?.professionals.find(item => item.id === professionalId);
  const zone = catalog?.business.zoneId ?? 'America/Sao_Paulo';

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!slot || !service || !professional || !online || pending) return;
    const data = { professionalId, serviceId, startsAt: slot.startsAt, customerName: name.trim(), customerPhone: phone.trim() };
    const signature = JSON.stringify(data);
    if (request.current.signature !== signature) request.current = { signature, key: crypto.randomUUID() };
    setPending(true); setError('');
    try {
      const result = await api<Receipt>('/api/public/' + encodeURIComponent(slug) + '/bookings', {
        method: 'POST', body: JSON.stringify({ ...data, requestKey: request.current.key }),
      });
      setReceipt(result); setRefresh(value => value + 1);
    } catch (error) {
      setError(errorMessage(error));
      if (error instanceof ApiError && error.status === 409) { request.current = { signature: '', key: '' }; setRefresh(value => value + 1); }
    } finally { setPending(false); }
  }

  if (loading) return <div className="page-state" role="status">Carregando o estabelecimento…</div>;
  if (!catalog) return <div className="page-state"><Message>{error || 'Não foi possível abrir este estabelecimento.'}</Message></div>;
  const today = todayInZone(zone);
  return <div className="booking-layout">
    <aside className="business-sidebar">
      <div className="eyebrow">UM TEMPO PARA VOCÊ</div>
      <div className="business-avatar"><Scissors size={33} strokeWidth={1.3} /><span className="avatar-spark"><Sparkles size={13} /></span></div>
      <div className="business-category">{catalog.business.category}</div>
      <h2>{catalog.business.name}</h2>
      <p className="business-address"><MapPin size={15} />{catalog.business.address}</p>
      <div className="business-note"><span className="status-dot" /><span>Reserve direto, sem complicação.</span></div>
      {businesses.length > 1 && <label className="business-select-label">Escolha o estabelecimento<div className="select-wrap"><select value={slug} onChange={event => onSelectBusiness(event.target.value)}>{businesses.map(item => <option value={item.slug} key={item.id}>{item.name}</option>)}</select><ChevronDown size={15} /></div></label>}
      <div className="sidebar-divider" />
      <div className="sidebar-how"><span className="eyebrow">DO SEU JEITO</span><p><span>01</span> Escolha seu cuidado</p><p><span>02</span> Encontre o melhor horário</p><p><span>03</span> Pronto. É só chegar.</p></div>
      <div className="sidebar-bottom"><ShieldCheck size={19} /><p>Seus dados ficam disponíveis apenas para o estabelecimento escolhido.</p></div>
    </aside>
    <div className="booking-content">
      <div className="page-heading"><span className="eyebrow"><span className="tiny-line" /> AGENDAMENTO ONLINE</span><h1>Um horário que<br />cabe no seu dia<span className="serif-dot">.</span></h1><p>Escolha seu serviço. A gente cuida do espaço na agenda.</p></div>
      <div className="booking-columns">
        <div className="booking-steps">
          <section className="booking-section" aria-labelledby="service-title"><div className="section-title"><span className="step-number">01</span><h2 id="service-title">Qual vai ser o cuidado?</h2><span className="section-hint">{catalog.services.length} serviços</span></div>
            <div className="services-list">{catalog.services.map(item => <button type="button" key={item.id} className={'service-card ' + (serviceId === item.id ? 'selected' : '')} aria-pressed={serviceId === item.id} onClick={() => { setServiceId(item.id); setReceipt(null); }}>
              <span className="service-symbol"><Scissors size={21} strokeWidth={1.4} /></span><span className="service-copy"><strong>{item.name}</strong><span><Clock3 size={12} /> {item.durationMinutes} min <span className="dot-separator">·</span> {item.description}</span></span><span className="service-price">{money(item.priceCents)}</span><span className="radio-indicator">{serviceId === item.id && <Check size={13} />}</span>
            </button>)}</div>
          </section>
          <section className="booking-section" aria-labelledby="schedule-title"><div className="section-title"><span className="step-number">02</span><h2 id="schedule-title">Com quem e quando?</h2></div>
            <div className="professional-list" aria-label="Profissionais">{catalog.professionals.map((item, index) => <button type="button" key={item.id} aria-pressed={professionalId === item.id} className={'professional-chip ' + (professionalId === item.id ? 'selected' : '')} onClick={() => { setProfessionalId(item.id); setReceipt(null); }}><span className={'initial-avatar tone-' + index}>{initials(item.name)}</span><span><strong>{item.name}</strong><small>Profissional</small></span>{professionalId === item.id && <CheckCircle2 size={17} />}</button>)}</div>
            <DatePicker date={date} today={today} max={plusDays(today, catalog.horizonDays)} onChange={value => { setDate(value); setReceipt(null); }} />
            <TimeSlots {...availability} selected={slot?.startsAt ?? ''} onSelect={value => { setSlot(value); setReceipt(null); }} zone={zone} />
            <p className="timezone-note"><Clock3 size={12} /> Todos os horários seguem o fuso do estabelecimento: {zone === 'America/Sao_Paulo' ? 'Brasília' : zone}.</p>
          </section>
          <section className="booking-section details-section" aria-labelledby="details-title"><div className="section-title"><span className="step-number">03</span><h2 id="details-title">Como podemos chamar você?</h2></div>
            <form id="booking-form" onSubmit={event => void submit(event)} className="customer-form">
              <label>Seu nome<input name="customerName" autoComplete="name" placeholder="Nome e sobrenome" required maxLength={100} value={name} onChange={event => setName(event.target.value)} /></label>
              <label>Telefone de contato<input name="customerPhone" type="tel" autoComplete="tel" placeholder="(11) 99999-9999" required minLength={8} maxLength={25} value={phone} onChange={event => setPhone(event.target.value)} /></label>
              <p className="field-note">O estabelecimento poderá usar esse contato para falar sobre seu atendimento.</p>
            </form>
          </section>
        </div>
        <aside className="booking-summary" aria-label="Resumo do agendamento">
          {receipt ? <div className="receipt" id="booking-receipt" tabIndex={-1}><span className="receipt-icon"><Check size={27} /></span><span className="eyebrow">TUDO CERTO</span><h2>Seu horário<br />está reservado.</h2><p>{receipt.serviceName} com {receipt.professionalName}</p><div className="receipt-date"><strong>{dayLabel(dateInZone(receipt.startsAt, zone))}</strong><span>{time(receipt.startsAt, zone)} – {time(receipt.endsAt, zone)}</span></div><span className="reference-label">Código da reserva</span><code>{receipt.reference}</code><p className="field-note">Para cancelar ou remarcar, entre em contato com o estabelecimento e informe este código.</p><button type="button" className="button secondary full" onClick={() => { setReceipt(null); setSlot(null); setName(''); setPhone(''); request.current = { signature: '', key: '' }; }}>Fazer outro agendamento</button></div> : <>
            <div className="summary-top"><CalendarDays size={19} /><span>SEU AGENDAMENTO</span></div><h2>Seu próximo<br />momento de cuidado.</h2>
            <div className="summary-line"><Scissors size={16} /><div><span>Serviço</span><strong>{service?.name ?? 'Escolha um serviço'}</strong><small>{service?.durationMinutes ?? '—'} minutos</small></div></div>
            <div className="summary-line"><span className="summary-avatar">{professional ? initials(professional.name) : '?'}</span><div><span>Profissional</span><strong>{professional?.name ?? 'Escolha um profissional'}</strong></div></div>
            <div className="summary-line"><CalendarDays size={16} /><div><span>Data e horário</span><strong>{date ? dayLabel(date) : 'Escolha uma data'}</strong><small className={slot ? 'chosen-time' : ''}>{slot ? time(slot.startsAt, zone) + ' – ' + time(slot.endsAt, zone) : 'Selecione um horário disponível'}</small></div></div>
            <div className="summary-total"><span>Total do serviço</span><strong>{money(service?.priceCents ?? 0)}</strong></div><p className="payment-note">Pagamento diretamente no estabelecimento.</p>
            {error && <Message>{error}</Message>}
            <button type="submit" form="booking-form" className="button primary full confirm-button" disabled={!slot || !name.trim() || phone.replace(/\D/g, '').length < 8 || !online || pending} aria-busy={pending}>{pending ? 'Confirmando…' : 'Confirmar agendamento'}{!pending && <ArrowRight size={17} />}</button>
            <p className="summary-assurance"><ShieldCheck size={13} /> Seu horário é conferido antes da confirmação.</p>
          </>}
          <div className="summary-footnote"><Sparkles size={15} /><span>Menos espera.<br /><strong>Mais tempo para você.</strong></span></div>
        </aside>
      </div>
    </div>
  </div>;
}
