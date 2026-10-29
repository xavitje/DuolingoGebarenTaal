// Bijdragen voor vakmensen: per gebaar zien wat er nog nodig is en het opnemen. Zoals de
// content-checklist in ContributionScreen van de app.
import { useEffect, useState } from 'preact/hooks';
import { type ContentStatus, contentStatus, pendingDemoVideos } from '../../lib/api';
import { auth, signLanguage } from '../../lib/auth';
import { useStore } from '../../lib/store';
import { recordingSupported } from '../../camera/recorder';
import { cameraSupported } from '../../camera/handCamera';
import { LinkButton, Notice, Spinner } from '../../components/ui';

export const TARGET_APPROVED = 6;

export function ContributePage() {
  const a = useStore(auth);
  const lang = signLanguage(a);
  const [items, setItems] = useState<ContentStatus[] | null>(null);
  const [toReview, setToReview] = useState<number | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    contentStatus(lang).then(setItems).catch(() => { setError(true); setItems([]); });
    pendingDemoVideos(lang)
      .then((list) => setToReview(list.filter((v) => v.recorded_by !== a.userId).length))
      .catch(() => undefined);
  }, [lang]);

  const complete = items?.filter((i) => i.has_demo_video && i.approved >= TARGET_APPROVED).length ?? 0;

  return (
    <div class="page stack-lg">
      <div class="stack-sm">
        <h1 class="display">Bijdragen</h1>
        <p class="lead muted">Neem voorbeeldvideo's en herkenningsopnames op, en keur de video's van collega's.</p>
      </div>

      {!cameraSupported() ? <Notice kind="warning">Deze browser kan de camera niet gebruiken. Opnemen kan in Chrome, Edge of Safari.</Notice> : null}
      {cameraSupported() && !recordingSupported() ? <Notice kind="warning">Deze browser kan geen video opnemen. Herkenningsopnames lukken wel.</Notice> : null}

      <div class="card row-between">
        <div class="stack-sm">
          <h2 class="heading">Demovideo's van collega's</h2>
          <p class="muted">Elke video wordt door een tweede vakman gecontroleerd voordat leerlingen hem zien.</p>
        </div>
        <LinkButton href="/bijdragen/beoordelen" variant="secondary">
          {toReview ? `Beoordelen (${toReview} wachten)` : 'Beoordelen'}
        </LinkButton>
      </div>

      {error ? <Notice>Geen verbinding. Probeer het nog eens.</Notice> : null}
      {items == null ? <Spinner /> : (
        <section class="stack" aria-labelledby="lijst-kop">
          <div class="stack-sm">
            <h2 id="lijst-kop" class="title">{complete} van {items.length} gebaren compleet</h2>
            <p class="muted">Compleet = een demovideo en minstens {TARGET_APPROVED} goedgekeurde opnames, liefst van verschillende mensen.</p>
          </div>
          <ul class="checklist">
            {items.map((i) => {
              const done = i.has_demo_video && i.approved >= TARGET_APPROVED;
              const video = i.has_demo_video ? 'ja' : i.demo_pending > 0 ? 'wacht op beoordeling' : 'nee';
              return (
                <li key={i.gesture_id}>
                  <span class={`letter-chip${done ? ' done' : ''}`} aria-hidden="true">{i.name}</span>
                  <div class="stack-sm" style={{ gap: '2px' }}>
                    <strong>{i.name}{done ? ' · Compleet' : ''}</strong>
                    <span class="caption">
                      Video: {video} · goedgekeurd {i.approved}/{TARGET_APPROVED} · wacht op beoordeling: {i.pending}
                    </span>
                  </div>
                  <div class="student-actions">
                    <LinkButton href={`/bijdragen/demo/${encodeURIComponent(i.gesture_id)}`} variant="secondary" size="small">Demovideo</LinkButton>
                    <LinkButton href={`/bijdragen/opname/${encodeURIComponent(i.gesture_id)}`} variant="secondary" size="small">Herkenning</LinkButton>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
