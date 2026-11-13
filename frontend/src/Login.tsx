import { useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, LockKeyhole, ShieldCheck } from 'lucide-react';
import { api, errorMessage } from './api';
import type { CurrentUser } from './types';
import { Message } from './components';

export default function Login({ onUser, demo, online, onRegister, initialEmail = '' }: { onRegister?: () => void; initialEmail?: string; onUser: (user: CurrentUser) => void; demo: boolean; online: boolean }) {
  const [email, setEmail] = useState(initialEmail); const [password, setPassword] = useState('');
  const [error, setError] = useState(''); const [pending, setPending] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setPending(true);
    try {
      await api<void>('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ username: email.trim().toLowerCase(), password }).toString() });
      const user = await api<CurrentUser>('/api/auth/me'); setPassword(''); onUser(user);
    } catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  const fill = (name: string) => { setEmail(name + '@demo.agendaclara.dev'); setPassword('Demo@12345'); setError(''); };
  return <div className="login-layout"><div className="login-intro"><span className="eyebrow">PARA QUEM CUIDA DO NEGÓCIO</span><h1>Sua agenda organizada.<br />Seu dia mais leve<span className="serif-dot">.</span></h1><p>Uma visão clara dos atendimentos, dos horários livres e do que vem a seguir.</p><div className="login-feature"><ShieldCheck size={22} /><span>Cada estabelecimento tem seu próprio espaço.</span></div></div>
    <section className="login-card"><span className="login-icon"><LockKeyhole size={23} /></span><h2>Bem-vindo de volta.</h2><p>Acesse o painel do seu negócio.</p>
      <form onSubmit={event => void submit(event)}><label>E-mail<input type="email" autoComplete="username" name="email" placeholder="voce@seunegocio.com" required value={email} onChange={event => setEmail(event.target.value)} /></label><label>Senha<input type="password" autoComplete="current-password" name="password" required value={password} onChange={event => setPassword(event.target.value)} /></label>{error && <Message>{error}</Message>}<button className="button primary full" disabled={pending || !online} aria-busy={pending}>{pending ? 'Entrando…' : 'Entrar no painel'}<ArrowRight size={17} /></button></form>
      {onRegister && <button className="button secondary full" disabled={pending || !online} onClick={onRegister}>Cadastrar meu negócio</button>}
      {demo && <div className="demo-accounts"><span className="eyebrow">EXPERIMENTE A DEMONSTRAÇÃO</span><p>Escolha uma conta fictícia para preencher o acesso.</p><button className="demo-account" onClick={() => fill('marina')}><strong>Barbearia Horizonte</strong><span>Marina · responsável <ArrowRight size={14} /></span></button><button className="demo-account" onClick={() => fill('rafael')}><strong>Estúdio Aurora</strong><span>Rafael · responsável <ArrowRight size={14} /></span></button><small>Senha de demonstração: <code>Demo@12345</code></small></div>}
    </section>
  </div>;
}
