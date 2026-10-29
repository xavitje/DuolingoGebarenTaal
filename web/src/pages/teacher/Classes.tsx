import { useEffect, useState } from 'preact/hooks';
import { type SchoolClass, type WeekStats, createClass, feedbackDue, myClasses, schoolStats } from '../../lib/api';
import { auth } from '../../lib/auth';
import { navigate, onLinkClick } from '../../lib/router';
import { useStore } from '../../lib/store';
import { Button, Dialog, Field, Icon, Notice, Spinner, formatDate } from '../../components/ui';
import { FeedbackForm } from './Feedback';

export function ClassesPage() {
  const a = useStore(auth);
  const school = a.school && !a.school.is_student ? a.school : null;
  const [classes, setClasses] = useState<SchoolClass[] | null>(null);
  const [stats, setStats] = useState<WeekStats | null>(null);
  const [due, setDue] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [thanks, setThanks] = useState(false);

  async function load() {
    if (!school) return;
    setError(null);
    try {
      const list = await myClasses();
      setClasses(list.filter((c) => c.school_id === school.school_id));
    } catch {
      setError('Geen verbinding. Probeer het nog eens.');
      setClasses([]);
    }
    schoolStats(school.school_id).then((w) => setStats(w[0] ?? null)).catch(() => undefined);
    feedbackDue().then(setDue).catch(() => undefined);
  }

  useEffect(() => {
    void load();
  }, [school?.school_id]);

  async function submitClass(e: Event) {
    e.preventDefault();
    if (!school || !name.trim()) return;
    setBusy(true);
    try {
      const created = await createClass(school.school_id, name);
      setCreating(false);
      setName('');
      navigate(`/klas/${created.id}`);
    } catch {
      setError('Er ging iets mis. Probeer het nog eens.');
    } finally {
      setBusy(false);
    }
  }

  if (!school) {
    return (
      <div class="page page-narrow stack">
        <h1 class="display">Klas</h1>
        <div class="card stack-sm">
          <h2 class="heading">Nog niet gekoppeld aan een school</h2>
          <p>Vraag de beheerder van SignCoach om je account aan je school te koppelen. Log daarna opnieuw in.</p>
        </div>
      </div>
    );
  }

  return (
    <div class="page stack-lg">
      <div class="stack-sm">
        <h1 class="display">Klassen</h1>
        <p class="lead muted">{school.school_name}</p>
      </div>

      {!school.license_active ? (
        <Notice kind="warning">De licentie van {school.school_name} is verlopen op {formatDate(school.license_end)}. Leerlingen kunnen niet inloggen.</Notice>
      ) : null}
      {thanks ? <Notice kind="success">Bedankt voor je feedback!</Notice> : null}

      {stats ? (
        <section class="card stack-sm" aria-labelledby="week-kop">
          <h2 id="week-kop" class="heading">Deze week</h2>
          <div class="week">
            <p><strong>{stats.active_students}</strong> van de {stats.total_students} leerlingen hebben geoefend</p>
            <p><strong>{stats.successes}</strong> gebaren gehaald met de camera</p>
          </div>
        </section>
      ) : null}

      {due && !thanks ? (
        <section class="card-flat row-between" aria-labelledby="fb-kop">
          <div class="stack-sm">
            <h2 id="fb-kop" class="heading">Hoe gaat het tot nu toe?</h2>
            <p>Laat ons in 2 minuten weten wat goed gaat en wat beter kan.</p>
          </div>
          <Button variant="secondary" onClick={() => setFeedbackOpen(true)}>Feedback geven</Button>
        </section>
      ) : null}

      {error ? <Notice>{error}</Notice> : null}

      <section class="stack" aria-labelledby="klassen-kop">
        <div class="row-between">
          <h2 id="klassen-kop" class="title">Jouw klassen</h2>
          <Button onClick={() => setCreating(true)}><Icon name="plus" size={20} /> Nieuwe klas</Button>
        </div>
        {classes == null ? <Spinner /> : classes.length === 0 ? (
          <p class="muted">Nog geen klassen. Maak je eerste klas aan.</p>
        ) : (
          <ul class="list">
            {classes.map((c) => (
              <li key={c.id}>
                <a class="list-item" href={`/klas/${c.id}`} onClick={onLinkClick}>
                  <strong class="heading">{c.name}</strong>
                  <span class="code-chip" aria-label={`Klascode ${c.code.split('').join(' ')}`}>{c.code}</span>
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Dialog open={creating} title="Nieuwe klas" onClose={() => setCreating(false)}>
        <form class="stack" onSubmit={submitClass}>
          <Field id="klasnaam" label="Naam van de klas (bv. Groep 7b)">
            <input id="klasnaam" value={name} onInput={(e) => setName(e.currentTarget.value)} maxLength={60} required />
          </Field>
          <div class="row">
            <Button type="submit" busy={busy} disabled={!name.trim()}>Aanmaken</Button>
            <Button variant="secondary" onClick={() => setCreating(false)}>Annuleren</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={feedbackOpen} title="Hoe gaat het met SignCoach?" onClose={() => setFeedbackOpen(false)} wide>
        <FeedbackForm onSent={() => { setFeedbackOpen(false); setDue(false); setThanks(true); }} onLater={() => setFeedbackOpen(false)} />
      </Dialog>
    </div>
  );
}
