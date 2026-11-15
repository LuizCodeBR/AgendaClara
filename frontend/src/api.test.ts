import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, apiBlob, ApiError } from './api';
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

describe('falhas antes de enviar uma alteração', () => {
  it('mostra a orientação de espera se a preparação atingir o limite, sem repetir automaticamente', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: 'Aguarde 30 segundos e tente novamente.' }), { status: 429 }));
    vi.stubGlobal('fetch', fetch);
    await expect(api('/api/admin/professionals', { method: 'POST', body: '{}' }))
      .rejects.toMatchObject({ status: 429, message: 'Aguarde 30 segundos e tente novamente.' });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('trata a perda de conexão também durante a preparação', async () => {
    const fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    vi.stubGlobal('fetch', fetch);
    await expect(api('/api/admin/professionals', { method: 'POST', body: '{}' }))
      .rejects.toMatchObject({ status: 0, message: expect.stringContaining('Confira sua conexão') });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('preserva o cancelamento do navegador durante a preparação', async () => {
    const aborted = new DOMException('aborted', 'AbortError');
    const fetch = vi.fn().mockRejectedValue(aborted);
    vi.stubGlobal('fetch', fetch);
    await expect(api('/api/admin/professionals', { method: 'POST', body: '{}' })).rejects.toBe(aborted);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('mostra limite de tamanho da alteração sem reenviar o conteúdo', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: 'token', headerName: 'X-CSRF-TOKEN' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'A solicitação excede o tamanho permitido.' }), { status: 413 }));
    vi.stubGlobal('fetch', fetch);
    await expect(api('/api/admin/professionals', { method: 'POST', body: '{}' }))
      .rejects.toMatchObject({ status: 413, message: 'A solicitação excede o tamanho permitido.' });
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});

it('download privado mantém cookie, cancela requisição e não usa cache',async()=>{const fetch=vi.fn().mockResolvedValue(new Response('csv',{headers:{'Content-Type':'text/csv'}}));vi.stubGlobal('fetch',fetch);const controller=new AbortController();const result=await apiBlob('/api/admin/reports/export',{signal:controller.signal,headers:{'X-Agenda-Business':'company-test'}});expect(await result.text()).toBe('csv');expect(fetch).toHaveBeenCalledWith('/api/admin/reports/export',expect.objectContaining({method:'GET',credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:{'X-Agenda-Business':'company-test'}}));});
it('erro de sessão no CSV não vira arquivo baixado',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({detail:'Entre novamente.'}),{status:401})));await expect(apiBlob('/api/admin/reports/export')).rejects.toMatchObject({status:401,message:'Entre novamente.'});});
