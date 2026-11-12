import { describe, expect, it } from 'vitest';
import { dateInZone, dayLabel, localDateTime, money, plusDays, time } from './format';
describe('datas e valores apresentados ao usuário', () => {
  it('converte o relógio de Brasília sem depender do fuso do computador', () => {
    expect(localDateTime('2030-06-04', '09:00', 'America/Sao_Paulo')).toBe('2030-06-04T12:00:00.000Z');
    expect(time('2030-06-04T12:00:00Z', 'America/Sao_Paulo')).toBe('09:00');
  });
  it('atravessa mês e ano sem interpretar o dia no fuso do computador', () => {
    expect(plusDays('2030-12-31', 1)).toBe('2031-01-01');
    expect(dayLabel('2030-06-04')).toContain('4 de junho');
  });
  it('apresenta centavos como reais', () => {
    expect(money(5500).replace(/\s/g, ' ')).toBe('R$ 55,00');
  });
});

it('determina o dia do estabelecimento quando o instante UTC atravessa a meia-noite', () => {
  expect(dateInZone('2030-06-05T01:00:00Z', 'America/Sao_Paulo')).toBe('2030-06-04');
  expect(dateInZone('2030-06-04T23:30:00Z', 'Asia/Tokyo')).toBe('2030-06-05');
});
