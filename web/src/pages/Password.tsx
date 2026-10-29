import { useState } from 'preact/hooks';
import { auth } from '../lib/auth';
import { navigate } from '../lib/router';
import { supabase } from '../lib/supabase';
import { Button, Field, Link, Notice } from '../components/ui';
import { authErrorText } from './Login';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function submit(e: Event) {
    e.preventDefault();
    if (!email.trim()) return setError('Vul je e-mailadres in.');
    setBusy(true);
    setError(null);
    const { error: err } = await supabase.auth.resetPasswordForEmail(email.trim(), {
      redirectTo: `${location.origin}/wachtwoord-herstellen`,
    });
    setBusy(false);
    if (err) setError(authErrorText(err));
    else setSent(true);
  }

  return (
    <main id="main" class="page page-narrow stack-lg">
      <div class="stack-sm">
        <h1 class="display">Wachtwoord vergeten</h1>
        <p class="muted">Vul je e-mailadres in en we sturen een herstel-link.</p>
      </div>
      {sent ? (
        <Notice kind="success">Als er een account met dit adres bestaat, staat er nu een mail met een herstel-link in je inbox.</Notice>
      ) : (
        <form class="card stack" onSubmit={submit} noValidate>
          <Field id="f-email" label="E-mailadres">
            <input id="f-email" type="email" autocomplete="email" value={email} onInput={(e) => setEmail(e.currentTarget.value)} required />
          </Field>
          {error ? <Notice>{error}</Notice> : null}
          <Button type="submit" busy={busy} block>Herstel-mail sturen</Button>
        </form>
      )}
      <p><Link href="/inloggen">Terug naar inloggen</Link></p>
    </main>
  );
}

export function ResetPasswordPage() {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: Event) {
    e.preventDefault();
    if (password.length < 6) return setError('Wachtwoord moet minimaal 6 tekens zijn.');
    if (password !== confirm) return setError('Wachtwoorden komen niet overeen.');
    setBusy(true);
    setError(null);
    const { error: err } = await supabase.auth.updateUser({ password });
    setBusy(false);
    if (err) return setError(authErrorText(err));
    auth.set((s) => ({ ...s, recovering: false }));
    navigate('/leren', true);
  }

  return (
    <main id="main" class="page page-narrow stack-lg">
      <h1 class="display">Nieuw wachtwoord</h1>
      <form class="card stack" onSubmit={submit} noValidate>
        <Field id="n-pw" label="Nieuw wachtwoord">
          <input id="n-pw" type="password" autocomplete="new-password" value={password} onInput={(e) => setPassword(e.currentTarget.value)} />
        </Field>
        <Field id="n-pw2" label="Bevestig wachtwoord">
          <input id="n-pw2" type="password" autocomplete="new-password" value={confirm} onInput={(e) => setConfirm(e.currentTarget.value)} />
        </Field>
        {error ? <Notice>{error}</Notice> : null}
        <Button type="submit" busy={busy} block>Opslaan</Button>
      </form>
    </main>
  );
}
