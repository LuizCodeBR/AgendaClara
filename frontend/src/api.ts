export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) { super(message); this.status = status; }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal: options.signal });
    if (!csrf.ok) throw new ApiError('Não foi possível preparar a operação. Atualize a página.', csrf.status);
    const token = await csrf.json() as { token: string; headerName: string };
    headers.set(token.headerName, token.token);
  }
  if (typeof options.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  let response: Response;
  try {
    response = await fetch(path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError('Não foi possível conectar. Confira sua conexão e se o servidor está em execução.', 0);
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; errors?: string[] };
    throw new ApiError(problem.errors?.join(' · ') ?? problem.detail ?? 'Não foi possível concluir a operação.', response.status);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
export const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'Ocorreu um erro inesperado.';
