import { createClient } from '@supabase/supabase-js';
import { SUPABASE_ANON_KEY, SUPABASE_URL } from './config';

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    // Herstel- en bevestigingslinks uit e-mails komen terug met een sessie in de URL.
    detectSessionInUrl: true,
    flowType: 'implicit',
  },
});

/** Fout bij een aanroep, met de HTTP-status en (indien aanwezig) de foutcode uit de body. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly body: Record<string, unknown> = {},
  ) {
    super(`${status} ${code}`);
  }
  get isNetwork() {
    return this.status === 0;
  }
}

/** Gooit een ApiError voor een Supabase-fout. Netwerkfouten krijgen status 0. */
export function check<T>(result: { data: T; error: { message: string; code?: string; status?: number } | null; status?: number }): T {
  if (result.error) {
    // PostgREST geeft status 0 als het verzoek de server niet bereikte; Storage zet de status in de fout.
    const storageStatus = Number((result.error as { statusCode?: string | number }).statusCode);
    const status = result.status ?? (Number.isFinite(storageStatus) ? storageStatus : 0);
    throw new ApiError(status, result.error.code || result.error.message || 'error');
  }
  return result.data;
}

/** Edge function aanroepen. Zonder sessie met de publieke sleutel (zoals student-login). */
export async function callFunction<T>(name: string, body: unknown, withSession = true): Promise<T> {
  let token = SUPABASE_ANON_KEY;
  if (withSession) {
    const { data } = await supabase.auth.getSession();
    if (data.session) token = data.session.access_token;
  }
  let response: Response;
  try {
    response = await fetch(`${SUPABASE_URL}/functions/v1/${name}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: SUPABASE_ANON_KEY, Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'network');
  }
  const json = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) throw new ApiError(response.status, String(json.error ?? 'error'), json);
  return json as T;
}
