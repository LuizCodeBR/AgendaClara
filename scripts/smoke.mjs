import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const base = process.argv[2] ?? 'http://127.0.0.1:8080';
const cookies = new Map();
async function request(path, method = 'GET', body, form = false) {
  const headers = {};
  if (!['GET', 'HEAD'].includes(method)) {
    const csrf = await request('/api/auth/csrf');
    assert.equal(csrf.status, 200);
    headers[csrf.body.headerName] = csrf.body.token;
  }
  headers.Cookie = [...cookies].map(([name, value]) => name + '=' + value).join('; ');
  if (body) headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json';
  const response = await fetch(base + path, { method, headers, body: body ? (form ? new URLSearchParams(body).toString() : JSON.stringify(body)) : undefined, redirect: 'manual' });
  for (const cookie of response.headers.getSetCookie()) {
    const [name, ...value] = cookie.split(';')[0].split('=');
    if (value.join('=')) {
      if (name === 'JSESSIONID') {
        assert.match(cookie, /HttpOnly/i);
        assert.match(cookie, /SameSite=Lax/i);
        if (base.startsWith('https://')) assert.match(cookie, /; Secure/i);
      }
      cookies.set(name, value.join('='));
    } else cookies.delete(name);
  }
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null, headers: response.headers };
}
const index = await fetch(base + '/');
assert.equal(index.status, 200);
assert.match(await index.text(), /Agenda Clara/);
for (const size of [192, 512]) {
  const icon = await fetch(base + '/icons/icon-' + size + '.png');
  assert.equal(icon.status, 200);
  const bytes = Buffer.from(await icon.arrayBuffer());
  assert.equal(bytes.readUInt32BE(16), size);
  assert.equal(bytes.readUInt32BE(20), size);
}
const manifest = await (await fetch(base + '/manifest.webmanifest')).json();
assert.equal(manifest.display, 'standalone');
assert.equal((await request('/api/admin/catalog')).status, 401);
const oversize = await fetch(base + '/api/public/barbearia-horizonte/bookings', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: 'x'.repeat(16385),
});
assert.equal(oversize.status, 413);
assert.match(oversize.headers.get('cache-control'), /no-store/);
assert.match(oversize.headers.get('content-security-policy'), /frame-ancestors 'none'/);
await oversize.text();
const withoutCsrf = await fetch(base + '/api/public/barbearia-horizonte/bookings', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
});
assert.equal(withoutCsrf.status, 403);
await withoutCsrf.text();
const catalog = (await request('/api/public/barbearia-horizonte/catalog')).body;
const professionalId = catalog.professionals[0].id;
const serviceId = catalog.services[0].id;
const query = new URLSearchParams({ professionalId, serviceId, date: catalog.suggestedDate });
const slots = (await request('/api/public/barbearia-horizonte/availability?' + query)).body.slots;
assert.ok(slots.length > 0, 'A demonstração deve oferecer um horário.');
const booking = { requestKey: randomUUID(), professionalId, serviceId, startsAt: slots[0].startsAt,
  customerName: 'Verificação automática', customerPhone: '11999990000' };
const first = await request('/api/public/barbearia-horizonte/bookings', 'POST', booking);
assert.equal(first.status, 201);
const retry = await request('/api/public/barbearia-horizonte/bookings', 'POST', booking);
assert.equal(retry.status, 200);
assert.equal(retry.body.reference, first.body.reference);
const sessionBeforeLogin = cookies.get('JSESSIONID');
assert.equal((await request('/api/auth/login', 'POST', { username: 'marina@demo.agendaclara.dev', password: 'Demo@12345' }, true)).status, 204);
assert.notEqual(cookies.get('JSESSIONID'), sessionBeforeLogin, 'Login deve trocar o identificador da sessão.');
const agenda = await request('/api/admin/agenda?date=' + catalog.suggestedDate);
const appointment = agenda.body.appointments.find(item => item.reference === first.body.reference);
assert.ok(appointment);
assert.match(agenda.headers.get('cache-control'), /no-store/);
assert.equal((await request('/api/admin/appointments/' + appointment.id + '/cancel', 'POST')).status, 200);
assert.equal((await request('/api/auth/logout', 'POST')).status, 204);
assert.equal((await request('/api/auth/me')).status, 401);
console.log('OK: interface, ícones, manifesto, reserva real, repetição segura, login com rotação de sessão, cookies, CSRF, limite de corpo, cancelamento e logout.');
