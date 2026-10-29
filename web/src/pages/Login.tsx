import { useState } from 'preact/hooks';
import { signInWithPassword } from '../lib/auth';
import { Button, Field, Icon, Link, LinkButton, Notice } from '../components/ui';

export function authErrorText(e: unknown): string {
  const err = e as { status?: number; message?: string; code?: string } | null;
  const msg = (err?.message ?? '').toLowerCase();
  if (!err?.status || err.status === 0 || msg.includes('fetch')) return 'Geen internetverbinding. Controleer je verbinding.';
  if (msg.includes('email not confirmed') || err.code === 'email_not_confirmed') return 'Bevestig eerst je e-mailadres via de link in je mail.';
  if (msg.includes('already registered') || err.code === 'user_already_exists') return 'Er bestaat al een account met dit e-mailadres. Log in of vraag een nieuw wachtwoord aan.';
  if (err.status === 429) return 'Te veel pogingen. Wacht even en probeer het opnieuw.';
  if (err.status === 400 || err.status === 422) return 'Inloggen mislukt. Controleer je e-mail en wachtwoord.';
  return 'Er ging iets mis. Probeer het later opnieuw.';
}

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: Event) {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError('Vul je e-mailadres en wachtwoord in.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await signInWithPassword(email, password);
    } catch (err) {
      setError(authErrorText(err));
      setBusy(false);
    }
  }

  return (
    <div class="page" style={{ maxWidth: '1000px' }}>
      <main id="main" class="stack-lg">
        <div class="stack-sm">
          <span class="wordmark" style={{ color: 'var(--night)' }}>SignCoach</span>
          <h1 class="display">Inloggen</h1>
          <p class="lead muted">Leer Nederlandse Gebarentaal, ook gewoon in de browser.</p>
        </div>
        <div class="login-choice">
          <section class="student-card" aria-labelledby="leerling-kop">
            <span aria-hidden="true" style={{ color: 'var(--gold)' }}><Icon name="hand" size={40} /></span>
            <h2 id="leerling-kop" class="title">Ben je een leerling?</h2>
            <p class="muted">Log in met de klascode en de pincode van je juf of meester.</p>
            <LinkButton href="/leerling" size="large" block>Ik ben een leerling</LinkButton>
          </section>

          <section class="card stack" aria-labelledby="docent-kop">
            <h2 id="docent-kop" class="heading">Docent of vakman</h2>
            <form class="stack" onSubmit={submit} noValidate>
              <Field id="email" label="E-mailadres">
                <input id="email" type="email" autocomplete="username" value={email} onInput={(e) => setEmail(e.currentTarget.value)} required />
              </Field>
              <Field id="password" label="Wachtwoord">
                <div style={{ position: 'relative' }}>
                  <input id="password" type={show ? 'text' : 'password'} autocomplete="current-password" value={password}
                    onInput={(e) => setPassword(e.currentTarget.value)} required style={{ paddingRight: '88px' }} />
                  <button type="button" class="btn btn-ghost btn-small" style={{ position: 'absolute', right: '4px', top: '4px' }}
                    onClick={() => setShow(!show)} aria-pressed={show}>
                    {show ? 'Verberg' : 'Toon'}
                  </button>
                </div>
              </Field>
              {error ? <Notice>{error}</Notice> : null}
              <Button type="submit" busy={busy} block>Inloggen</Button>
            </form>
            <div class="row-between">
              <Link href="/wachtwoord-vergeten">Wachtwoord vergeten?</Link>
              <span>Nog geen account? <Link href="/aanmelden">Aanmelden</Link></span>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
