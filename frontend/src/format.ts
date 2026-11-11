export const money = (cents: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
export const time = (value: string, zone: string) => new Intl.DateTimeFormat('pt-BR', { timeZone: zone, hour: '2-digit', minute: '2-digit' }).format(new Date(value));
export const dayLabel = (date: string, short = false) => new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC', weekday: short ? 'short' : 'long', day: 'numeric', month: short ? undefined : 'long' }).format(new Date(date + 'T12:00:00Z'));
export function todayInZone(zone: string): string {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  return ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)?.value).join('-');
}
export function plusDays(date: string, amount: number) {
  const result = new Date(date + 'T12:00:00Z');
  result.setUTCDate(result.getUTCDate() + amount);
  return result.toISOString().slice(0, 10);
}
export const initials = (name: string) => name.split(' ').filter(Boolean).slice(0, 2).map(part => part[0]).join('');
export function localDateTime(date: string, clock: string, zone: string): string {
  const expected = Date.parse(date + 'T' + clock + ':00Z');
  let candidate = expected;
  const formatter = new Intl.DateTimeFormat('en-US', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
  for (let i = 0; i < 4; i++) {
    const parts = formatter.formatToParts(new Date(candidate));
    const get = (type: string) => parts.find(part => part.type === type)?.value ?? '';
    const represented = Date.parse(get('year') + '-' + get('month') + '-' + get('day') + 'T' + get('hour') + ':' + get('minute') + ':' + get('second') + 'Z');
    if (represented === expected) return new Date(candidate).toISOString();
    candidate += expected - represented;
  }
  throw new Error('Esse horário não existe no fuso do estabelecimento.');
}
