// Feedbackformulier voor docenten, dezelfde vragen als FeedbackDialog in de app.
import { useState } from 'preact/hooks';
import { submitFeedback } from '../../lib/api';
import { Button, Field, Notice } from '../../components/ui';

function Scale({ name, legend, from, to, value, onChange, hint }: {
  name: string; legend: string; from: number; to: number; value: number | null; onChange: (v: number) => void; hint?: string;
}) {
  return (
    <fieldset>
      <legend>{legend}</legend>
      {hint ? <p class="caption" style={{ marginBottom: '8px' }}>{hint}</p> : null}
      <div class="choice-row">
        {Array.from({ length: to - from + 1 }, (_, i) => from + i).map((n) => (
          <label class="choice" key={n}>
            <input type="radio" name={name} value={n} checked={value === n} onChange={() => onChange(n)} />
            <span>{n}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

export function FeedbackForm({ onSent, onLater }: { onSent: () => void; onLater: () => void }) {
  const [usage, setUsage] = useState<string | null>(null);
  const [enjoy, setEnjoy] = useState<number | null>(null);
  const [recognition, setRecognition] = useState<number | null>(null);
  const [ease, setEase] = useState<number | null>(null);
  const [recommend, setRecommend] = useState<number | null>(null);
  const [best, setBest] = useState('');
  const [improve, setImprove] = useState('');
  const [quote, setQuote] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const complete = usage && enjoy && recognition && ease && recommend != null;

  async function send(e: Event) {
    e.preventDefault();
    if (!complete) return setError('Beantwoord de vragen met een keuze.');
    setBusy(true);
    setError(null);
    try {
      await submitFeedback({
        p_usage: usage!, p_pupils_enjoy: enjoy!, p_recognition: recognition!, p_ease_of_use: ease!, p_recommend: recommend!,
        p_improve: improve.slice(0, 2000), p_best: best.slice(0, 2000), p_may_quote: quote,
      });
      onSent();
    } catch {
      setError('Er ging iets mis. Probeer het nog eens.');
      setBusy(false);
    }
  }

  return (
    <form class="stack-lg" onSubmit={send}>
      <p>Twee minuten van je tijd helpt ons de app beter te maken voor jouw klas.</p>
      <fieldset>
        <legend>Hoe vaak gebruikte je SignCoach de afgelopen weken?</legend>
        <div class="choice-row">
          {[['niet', 'Niet'], ['soms', 'Soms'], ['wekelijks', 'Wekelijks'], ['vaker', 'Vaker']].map(([v, label]) => (
            <label class="choice" key={v}>
              <input type="radio" name="usage" value={v} checked={usage === v} onChange={() => setUsage(v)} />
              <span>{label}</span>
            </label>
          ))}
        </div>
      </fieldset>
      <Scale name="enjoy" legend="Hoe leuk vinden je leerlingen het? (1 = niet, 5 = heel leuk)" from={1} to={5} value={enjoy} onChange={setEnjoy} />
      <Scale name="recognition" legend="Hoe goed werkt de camera-herkenning? (1 = slecht, 5 = goed)" from={1} to={5} value={recognition} onChange={setRecognition} />
      <Scale name="ease" legend="Hoe makkelijk is de app voor jou? (1 = lastig, 5 = makkelijk)" from={1} to={5} value={ease} onChange={setEase} />
      <Scale name="recommend" legend="Hoe waarschijnlijk is het dat je SignCoach aanraadt aan een collega?" hint="0 = zeker niet, 10 = zeker wel"
        from={0} to={10} value={recommend} onChange={setRecommend} />
      <Field id="fb-best" label="Wat werkt goed?">
        <textarea id="fb-best" value={best} maxLength={2000} onInput={(e) => setBest(e.currentTarget.value)} />
      </Field>
      <Field id="fb-improve" label="Wat moet er beter?">
        <textarea id="fb-improve" value={improve} maxLength={2000} onInput={(e) => setImprove(e.currentTarget.value)} />
      </Field>
      <label class="check-row">
        <input type="checkbox" checked={quote} onChange={(e) => setQuote(e.currentTarget.checked)} />
        <span>SignCoach mag mijn antwoord (zonder mijn naam) gebruiken als ervaring van een docent.</span>
      </label>
      {error ? <Notice>{error}</Notice> : null}
      <div class="row">
        <Button type="submit" busy={busy}>Versturen</Button>
        <Button variant="secondary" onClick={onLater}>Later</Button>
      </div>
    </form>
  );
}
