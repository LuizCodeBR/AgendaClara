export type PageMode = 'home' | 'admin' | 'booking' | 'not-found';
export function readRoute(hash: string): { mode: PageMode; slug: string } {
  if (['', '#', '#/', '#/inicio'].includes(hash)) return { mode: 'home', slug: '' };
  if (hash === '#/painel') return { mode: 'admin', slug: '' };
  if (hash.startsWith('#/agendar/')) {
    try {
      const slug = decodeURIComponent(hash.slice(10));
      if (slug && !slug.includes('/')) return { mode: 'booking', slug };
    } catch { /* A malformed shared URL must not crash the application. */ }
  }
  return { mode: 'not-found', slug: '' };
}
