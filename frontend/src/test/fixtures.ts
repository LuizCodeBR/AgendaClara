import type { Appointment, Catalog, CurrentUser, DayAgenda } from '../types';
export const catalog: Catalog = {
  business: { id: 'business', slug: 'teste', name: 'Negócio Teste', category: 'Barbearia', zoneId: 'America/Sao_Paulo', address: 'Rua Teste' },
  services: [{ id: 'service', name: 'Corte', description: 'Atendimento', durationMinutes: 30, priceCents: 5000 }],
  professionals: [{ id: 'professional', name: 'Ana Exemplo', specialty: 'Corte', serviceIds: ['service'] }],
  suggestedDate: '2030-06-04', horizonDays: 60, bookingPolicy:{bookingNoticeMinutes:0,cancellationNoticeMinutes:0,rescheduleNoticeMinutes:0,version:0},
};
export const owner: CurrentUser = { name: 'Marina', email: 'marina@example.com', business: catalog.business };
export const appointment: Appointment = {
  id: 'appointment', professionalId: 'professional', serviceId: 'service', serviceName: 'Corte',
  professionalName: 'Ana Exemplo', customerName: 'Cliente do dia anterior', customerPhone: '11999991234',
  startsAt: '2030-06-04T12:00:00Z', endsAt: '2030-06-04T12:30:00Z',
  reference: 'ABC123', priceCents: 5000, status: 'BOOKED', version: 0,
};
export const agenda: DayAgenda = {
  date: '2030-06-04', zoneId: catalog.business.zoneId, appointments: [appointment],
  blocks: [{ id: 'block', professionalId: 'professional', startsAt: '2030-06-04T16:00:00Z', endsAt: '2030-06-04T17:00:00Z', reason: 'Bloqueio do dia anterior' }],
};
