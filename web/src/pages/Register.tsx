import { useState } from 'preact/hooks';
import { supabase } from '../lib/supabase';
import { Button, Field, Link, LinkButton, Notice } from '../components/ui';
import { authErrorText } from './Login';

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function submit(e: Event) {
    e.preventDefault();
    if (!email.trim() || !password) return setError('Vul alle verplichte velden in.');
    if (password.length < 6) return setError('Wachtwoord moet minimaal 6 tekens zijn.');
    if (password !== confirm) return setError('Wachtwoorden komen niet overeen.');
    setBusy(true);
    setError(null);
    const { data, error: err } = await supabase.auth.signUp({
      email: email.trim(),
      password,
      options: { emailRedirectTo: `${location.origin}/inloggen` },
    });
    setBusy(false);
    if (err) return setError(authErrorText(err));
    if (!data.session) setSentTo(email.trim());
    // Met een sessie (e-mailbevestiging uit) stuurt de app je vanzelf door.
  }

  if (sentTo) {
    return (
      <main id="main" class="page page-narrow stack-lg">
        <h1 class="display">Check je e-mail</h1>
        <p class="lead">
          Je account is aangemaakt. We hebben een bevestigingslink gestuurd naar <strong>{sentTo}</strong>. Klik op de link in die mail en log
          daarna hier in. Geen mail gezien? Kijk ook in je spam.
        </p>
        <LinkButton href="/inloggen">Naar inloggen</LinkButton>
      </main>
    );
  }

  return (
    <main id="main" class="page page-narrow stack-lg">
      <div class="stack-sm">
        <h1 class="display">Account aanmaken</h1>
        <p class="muted">Voor docenten en gebarentaalvakmensen. Leerlingen krijgen een klascode en pincode van hun docent.</p>
      </div>
      <form class="card stack" onSubmit={submit} noValidate>
        <Field id="r-email" label="E-mailadres">
          <input id="r-email" type="email" autocomplete="email" value={email} onInput={(e) => setEmail(e.currentTarget.value)} required />
        </Field>
        <Field id="r-pw" label="Wachtwoord (min. 6 tekens)">
          <input id="r-pw" type="password" autocomplete="new-password" value={password} onInput={(e) => setPassword(e.currentTarget.value)} required />
        </Field>
        <Field id="r-pw2" label="Bevestig wachtwoord">
          <input id="r-pw2" type="password" autocomplete="new-password" value={confirm} onInput={(e) => setConfirm(e.currentTarget.value)} required />
        </Field>
        {error ? <Notice>{error}</Notice> : null}
        <Button type="submit" busy={busy} block>Account aanmaken</Button>
        <p class="caption">Na het aanmaken koppelt SignCoach je account aan je school.</p>
      </form>
      <p>Al een account? <Link href="/inloggen">Inloggen</Link></p>
    </main>
  );
}
