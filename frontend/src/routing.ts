export type PageMode = 'home' | 'admin' | 'booking' | 'register' | 'verify-request' | 'verify' | 'forgot' | 'reset' | 'customer' | 'not-found';
export function readRoute(hash: string): { mode: PageMode; slug: string; token?: string; reservationKey?: string } {
  if (['', '#', '#/', '#/inicio'].includes(hash)) return { mode: 'home', slug: '' };
  const [path, query = ''] = hash.split('?');
  const access = { '#/confirmar-email': 'verify', '#/redefinir-senha': 'reset', '#/verificar-email': 'verify-request', '#/recuperar-senha': 'forgot' } as const;
  if (path === '#/minha-reserva') return { mode: 'customer', slug: '', token: new URLSearchParams(query).get('token') ?? '', reservationKey: new URLSearchParams(query).get('reserva') ?? '' };
  if (path in access) return { mode: access[path as keyof typeof access], slug: '', token: new URLSearchParams(query).get('token') ?? '' };
  if (hash === '#/cadastro') return { mode: 'register', slug: '' };
  if (hash === '#/painel') return { mode: 'admin', slug: '' };
  if (hash.startsWith('#/agendar/')) {
    try {
      const slug = decodeURIComponent(hash.slice(10));
      if (slug && !slug.includes('/')) return { mode: 'booking', slug };
    } catch { /* A malformed shared URL must not crash the application. */ }
  }
  return { mode: 'not-found', slug: '' };
}
