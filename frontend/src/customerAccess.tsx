import { useRef, useState } from 'react';
import { readRoute } from './routing';

export function reservationLink(token: string) {
  return window.location.origin + '/#/minha-reserva?token=' + encodeURIComponent(token);
}
export function reservationToken(input: string) {
  const value=input.trim();
  if (/^[A-Za-z0-9_-]{43}$/.test(value)) return value;
  try {
    const url=new URL(value);
    if (url.origin!==window.location.origin || url.pathname!=='/' || url.search) return '';
    const route=readRoute(url.hash);
    return route.mode==='customer' && /^[A-Za-z0-9_-]{43}$/.test(route.token??'') ? route.token! : '';
  } catch { return ''; }
}
export function PrivateReservationLink({ token }: { token: string }) {
  const input=useRef<HTMLInputElement>(null); const [notice,setNotice]=useState('');
  const link=reservationLink(token);
  async function copy() {
    try { if (!navigator.clipboard) throw new Error(); await navigator.clipboard.writeText(link); setNotice('Link privado copiado.'); }
    catch { input.current?.focus(); input.current?.select(); setNotice('Link selecionado. Use a opção Copiar do seu aparelho.'); }
  }
  return <div className="private-reservation-link"><label>Link privado da reserva<input ref={input} readOnly autoComplete="off" value={link} onFocus={event=>event.currentTarget.select()} /></label>
    <p className="field-note">Quem tiver este link poderá consultar, cancelar ou remarcar esta reserva. Guarde-o em local privado.</p>
    <div className="customer-actions"><button type="button" className="button secondary" onClick={()=>void copy()}>Copiar link privado</button><a className="button secondary" href={'#/minha-reserva?token='+encodeURIComponent(token)}>Gerenciar minha reserva</a></div>
    <span role="status">{notice}</span>
  </div>;
}
