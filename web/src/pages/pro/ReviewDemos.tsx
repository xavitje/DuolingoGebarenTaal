// Demovideo's van collega's beoordelen. Nooit je eigen video: dat weigert de edge function ook.
import { useEffect, useState } from 'preact/hooks';
import { type DemoVideo, demoSignedUrl, pendingDemoVideos, reviewDemoVideo } from '../../lib/api';
import { auth, signLanguage } from '../../lib/auth';
import { learning } from '../../lib/learning';
import { navigate } from '../../lib/router';
import { useStore } from '../../lib/store';
import { ApiError } from '../../lib/supabase';
import { findGesture } from '../../domain/curriculum';
import { Button, Field, Icon, Notice, Spinner, formatDate } from '../../components/ui';

export function ReviewDemosPage() {
  const a = useStore(auth);
  const l = useStore(learning);
  const lang = signLanguage(a);
  const [queue, setQueue] = useState<DemoVideo[] | null>(null);
  const [own, setOwn] = useState(0);
  const [current, setCurrent] = useState<DemoVideo | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<'approved' | 'rejected' | null>(null);

  const nameOf = (id: string) => findGesture(l.units, id)?.name ?? id;

  async function load() {
    setError(null);
    try {
      const all = await pendingDemoVideos(lang);
      setQueue(all.filter((v) => v.recorded_by !== a.userId));
      setOwn(all.filter((v) => v.recorded_by === a.userId).length);
    } catch {
      setError('Geen verbinding. Probeer het nog eens.');
      setQueue([]);
    }
  }

  useEffect(() => {
    void load();
  }, [lang]);

  async function open(v: DemoVideo) {
    setCurrent(v);
    setUrl(null);
    setNote('');
    setResult(null);
    setError(null);
    try {
      setUrl(await demoSignedUrl(v.storage_path));
    } catch {
      setError('De video kon niet worden geladen.');
    }
  }

  async function decide(approve: boolean) {
    if (!current) return;
    if (!approve && !note.trim()) {
      setError('Schrijf bij afkeuren een opmerking voor de maker.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await reviewDemoVideo(current.id, approve, note);
      setResult(approve ? 'approved' : 'rejected');
      setQueue((q) => q?.filter((v) => v.id !== current.id) ?? null);
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'own_video'
        ? 'Je kunt je eigen video niet beoordelen. Een collega moet dat doen.'
        : e instanceof ApiError && e.code === 'already_reviewed' ? 'Deze video is al door iemand anders beoordeeld.'
          : 'Er ging iets mis. Probeer het nog eens.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div class="lesson">
      <header class="lesson-top">
        <button type="button" class="icon-btn" aria-label={current ? 'Terug naar de lijst' : 'Terug naar bijdragen'}
          onClick={() => (current ? setCurrent(null) : navigate('/bijdragen'))}>
          <Icon name={current ? 'back' : 'close'} />
        </button>
        <span class="label">Demovideo's beoordelen</span>
        <span style={{ width: '44px' }} />
      </header>
      <main id="main" class="lesson-main">
        {!current ? (
          <div class="stack-lg" style={{ maxWidth: '640px', width: '100%', margin: '0 auto' }}>
            <h1 class="title">Demovideo's beoordelen</h1>
            {own > 0 ? <Notice kind="info">{own} van jouw video's wachten op een collega.</Notice> : null}
            {error ? <Notice>{error}</Notice> : null}
            {queue == null ? <Spinner /> : queue.length === 0 ? <p class="muted">Er wachten geen video's van collega's op beoordeling.</p> : (
              <ul class="list">
                {queue.map((v) => (
                  <li key={v.id}>
                    <button type="button" class="name-btn row-between" style={{ textAlign: 'left', padding: '14px 18px' }} onClick={() => void open(v)}>
                      <span class="heading">{nameOf(v.gesture_id)}</span>
                      <span class="caption">Opgenomen op {formatDate(v.created_at)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : (
          <div class="stack-lg" style={{ maxWidth: '640px', width: '100%', margin: '0 auto' }}>
            {result ? (
              <>
                <h1 class="title">{result === 'approved' ? 'Goedgekeurd' : 'Afgekeurd'}</h1>
                <Notice kind={result === 'approved' ? 'success' : 'info'}>
                  {result === 'approved' ? 'Goedgekeurd! Leerlingen zien deze video nu bij het gebaar.' : 'Afgekeurd. De video is verwijderd.'}
                </Notice>
                <div class="row">
                  {queue && queue.length > 0 ? <Button onClick={() => void open(queue[0])}>Volgende video</Button> : null}
                  <Button variant="on-night" onClick={() => setCurrent(null)}>Terug naar de lijst</Button>
                </div>
              </>
            ) : (
              <>
                <h1 class="title">Is dit een goed en correct voorbeeld van {nameOf(current.gesture_id)}?</h1>
                <div class="demo-frame" style={{ aspectRatio: '4 / 3', width: '100%' }}>
                  {url ? <video src={url} controls playsInline loop autoplay muted aria-label="Demovideo ter beoordeling" /> : <span class="spinner" aria-hidden="true" />}
                </div>
                <Field id="opmerking" label="Opmerking (verplicht bij afkeuren)">
                  <textarea id="opmerking" value={note} maxLength={500} onInput={(e) => setNote(e.currentTarget.value)} />
                </Field>
                <p class="caption">Bij afkeuren ziet de maker je opmerking. Je kunt je eigen video's niet beoordelen.</p>
                {error ? <Notice>{error}</Notice> : null}
                <div class="row">
                  <Button busy={busy} onClick={() => void decide(true)}>Goedkeuren</Button>
                  <Button variant="on-night" disabled={busy} onClick={() => void decide(false)}>Afkeuren</Button>
                </div>
              </>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
