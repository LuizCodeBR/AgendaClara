import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
afterEach(() => vi.unstubAllGlobals());
describe('comunicação com o servidor', () => {
  it('busca CSRF antes de uma alteração e mantém a sessão no cookie', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: 'safe-token', headerName: 'X-CSRF-TOKEN' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetch);
    await api('/api/admin/blocks/x', { method: 'DELETE' });
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls[0][0]).toBe('/api/auth/csrf');
    expect(fetch.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('safe-token');
    expect(fetch.mock.calls[1][1].credentials).toBe('same-origin');
    expect(fetch.mock.calls[1][1].cache).toBe('no-store');
  });
  it('não envia alteração quando a preparação CSRF falha', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 403 }));
    vi.stubGlobal('fetch', fetch);
    await expect(api('/api/admin/services', { method: 'POST', body: '{}' })).rejects.toBeInstanceOf(ApiError);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('mostra o conflito informado pela API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: 'Horário ocupado.' }), { status: 409 })));
    await expect(api('/api/public/test/availability')).rejects.toMatchObject({ status: 409, message: 'Horário ocupado.' });
  });
});
