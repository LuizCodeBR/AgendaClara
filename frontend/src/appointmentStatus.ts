import type {AppointmentStatus} from './types';
export const statusLabel:Record<AppointmentStatus,string>={BOOKED:'Confirmado',CANCELLED:'Cancelado',COMPLETED:'Concluído',NO_SHOW:'Não compareceu'};
export const customerStatusLabel:Record<AppointmentStatus,string>={BOOKED:'Confirmada',CANCELLED:'Cancelada',COMPLETED:'Atendimento concluído',NO_SHOW:'Não compareceu'};
