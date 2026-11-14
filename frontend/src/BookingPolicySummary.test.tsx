import {render,screen} from '@testing-library/react';
import {expect,it} from 'vitest';
import BookingPolicySummary,{noticeLabel} from './BookingPolicySummary';
it('apresenta intervalos em horas e minutos sem arredondar',()=>{
  expect(noticeLabel(0)).toBe('Sem antecedência adicional');expect(noticeLabel(1)).toBe('1 minuto');
  expect(noticeLabel(90)).toBe('1 hora e 30 minutos');expect(noticeLabel(1440)).toBe('24 horas');
});
it('mostra regras distintas e datas no fuso do estabelecimento',()=>{
  render(<BookingPolicySummary policy={{bookingNoticeMinutes:120,cancellationNoticeMinutes:1440,rescheduleNoticeMinutes:720,version:3}} startsAt="2030-06-04T12:00:00Z" zone="America/Sao_Paulo" confirmed/>);
  expect(screen.getByRole('heading',{name:'Regras desta reserva'})).toBeVisible();
  expect(screen.getByText('24 horas antes')).toBeVisible();expect(screen.getByText('12 horas antes')).toBeVisible();
  expect(screen.getByText(/segunda-feira, 3 de junho às 09:00/)).toBeVisible();
  expect(screen.getByText(/segunda-feira, 3 de junho às 21:00/)).toBeVisible();
});
it('mostra o comportamento de zero e as regras antes de escolher horário',()=>{
  render(<BookingPolicySummary/>);expect(screen.getByRole('heading',{name:'Regras do agendamento'})).toBeVisible();
  expect(screen.getAllByText('até antes de começar')).toHaveLength(2);
});
