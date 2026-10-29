// Leerling-login in drie stappen, zoals StudentLoginViewModel: klascode -> naam kiezen -> pincode.
import { useEffect, useRef, useState } from 'preact/hooks';
import { classRoster, studentLogin } from '../lib/api';
import { completeStudentLogin, lastClassCode } from '../lib/auth';
import { navigate } from '../lib/router';
import { ApiError } from '../lib/supabase';
import { Button, Icon, Notice, Spinner } from '../components/ui';

type Step = 'code' | 'name' | 'pin';
type Entry = { user_id: string; first_name: string };

export function StudentLoginPage() {
  const [step, setStep] = useState<Step>('code');
  const [code, setCode] = useState('');
  const [roster, setRoster] = useState<Entry[]>([]);
  const [selected, setSelected] = useState<Entry | null>(null);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [checkingRemembered, setCheckingRemembered] = useState(!!lastClassCode.get());
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    const remembered = lastClassCode.get();
    if (remembered) void submitCode(remembered, true);
  }, []);

  useEffect(() => {
    headingRef.current?.focus();
  }, [step]);

  async function submitCode(raw: string, remembered = false) {
    const clean = raw.trim().toUpperCase();
    if (clean.length !== 6) {
      setError('Een klascode heeft 6 tekens.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const list = await classRoster(clean);
      if (list.length === 0) {
        if (remembered) lastClassCode.set(null);
        else setError('Deze klascode kennen we niet. Kijk nog eens goed.');
      } else {
        setCode(clean);
        setRoster(list);
        setStep('name');
      }
    } catch {
      if (!remembered) setError('Geen verbinding. Probeer het later nog eens.');
    } finally {
      setBusy(false);
      setCheckingRemembered(false);
    }
  }

  async function login(fullPin: string) {
    if (!selected) return;
    setBusy(true);
    try {
      const session = await studentLogin(code, selected.user_id, fullPin);
      await completeStudentLogin(session, code);
    } catch (e) {
      const status = e instanceof ApiError ? e.status : 0;
      setError(
        status === 401 ? 'Die pincode klopt niet. Probeer het nog eens.'
          : status === 423 ? 'Te vaak geprobeerd. Wacht 15 minuten of vraag je juf of meester om een nieuwe pincode.'
            : 'Geen verbinding. Probeer het later nog eens.',
      );
      setPin('');
      setBusy(false);
    }
  }

  function digit(d: string) {
    if (busy || pin.length >= 4) return;
    const next = pin + d;
    setPin(next);
    setError(null);
    if (next.length === 4) void login(next);
  }

  useEffect(() => {
    if (step !== 'pin') return;
    const onKey = (e: KeyboardEvent) => {
      if (/^[0-9]$/.test(e.key)) digit(e.key);
      else if (e.key === 'Backspace') setPin((p) => p.slice(0, -1));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  function back() {
    setError(null);
    if (step === 'pin') {
      setPin('');
      setStep('name');
    } else if (step === 'name') {
      setRoster([]);
      setStep('code');
    } else navigate('/inloggen');
  }

  return (
    <div class="login-hero">
      <header class="lesson-top">
        <button type="button" class="icon-btn" onClick={back} aria-label="Terug">
          <Icon name="back" />
        </button>
        <span class="label">Inloggen als leerling</span>
        <span style={{ width: '44px' }} />
      </header>
      <main id="main" class="login-panel">
        {checkingRemembered ? <Spinner /> : null}

        {!checkingRemembered && step === 'code' ? (
          <form class="stack-lg" onSubmit={(e) => { e.preventDefault(); void submitCode(code); }}>
            <div class="stack-sm">
              <h1 class="title" tabIndex={-1} ref={headingRef}>Wat is je klascode?</h1>
              <p class="muted" style={{ color: 'var(--night-muted)' }}>De code van 6 tekens die je van je juf of meester kreeg.</p>
            </div>
            <label class="visually-hidden" for="klascode">Klascode</label>
            <input id="klascode" class="code-input" value={code} maxLength={6} autocomplete="off" autocapitalize="characters" spellcheck={false}
              inputMode="text" onInput={(e) => setCode(e.currentTarget.value.replace(/[^a-z0-9]/gi, '').toUpperCase())} />
            {error ? <Notice>{error}</Notice> : null}
            <Button type="submit" size="large" block busy={busy} disabled={code.length !== 6}>Verder</Button>
          </form>
        ) : null}

        {step === 'name' ? (
          <div class="stack-lg">
            <h1 class="title" tabIndex={-1} ref={headingRef}>Wie ben jij?</h1>
            <ul class="names">
              {roster.map((r) => (
                <li key={r.user_id}>
                  <button type="button" class="name-btn" onClick={() => { setSelected(r); setPin(''); setError(null); setStep('pin'); }}>
                    {r.first_name}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        {step === 'pin' && selected ? (
          <div class="stack-lg" style={{ alignItems: 'center', textAlign: 'center' }}>
            <h1 class="title" tabIndex={-1} ref={headingRef}>Hoi {selected.first_name}! Wat is je pincode?</h1>
            <div class="pin-dots" role="img" aria-label={`${pin.length} van 4 cijfers ingevuld`}>
              {[0, 1, 2, 3].map((i) => <span key={i} class={i < pin.length ? 'on' : ''} />)}
            </div>
            {error ? <Notice>{error}</Notice> : null}
            {busy ? <Spinner label="Inloggen…" /> : null}
            <div class="pinpad">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
                <button key={d} type="button" onClick={() => digit(d)} disabled={busy}>{d}</button>
              ))}
              <span />
              <button type="button" onClick={() => digit('0')} disabled={busy}>0</button>
              <button type="button" class="wis" onClick={() => setPin(pin.slice(0, -1))} disabled={busy || pin.length === 0}>Wis</button>
            </div>
          </div>
        ) : null}
      </main>
    </div>
  );
}
