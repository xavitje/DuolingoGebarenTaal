import { useEffect, useRef, useState } from 'preact/hooks';
import { auth } from '../lib/auth';
import { learning } from '../lib/learning';
import { navigate } from '../lib/router';
import { useStore } from '../lib/store';
import { findGesture, lessonLabel } from '../domain/curriculum';
import type { CurriculumGesture } from '../domain/types';
import { Icon, Notice, Spinner } from '../components/ui';

// Zigzag zoals een leerpad: per les een stukje naar links of rechts.
const OFFSETS = [0, 56, 84, 56, 0, -56, -84, -56];

export function LearnPage() {
  const a = useStore(auth);
  const l = useStore(learning);
  const [toast, setToast] = useState<string | null>(null);
  const timer = useRef<number>();
  const currentRef = useRef<HTMLButtonElement>(null);

  useEffect(() => () => window.clearTimeout(timer.current), []);
  useEffect(() => {
    if (!l.loading) currentRef.current?.scrollIntoView({ block: 'center', behavior: 'instant' as ScrollBehavior });
  }, [l.loading]);

  function say(message: string) {
    setToast(message);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setToast(null), 3500);
  }

  function open(g: CurriculumGesture) {
    if (g.status === 'AVAILABLE' || g.status === 'COMPLETED') navigate(`/les/${encodeURIComponent(g.id)}`);
    else if (g.status === 'COMING_SOON') say('Dit gebaar komt binnenkort!');
    else {
      const next = l.nextId ? findGesture(l.units, l.nextId) : undefined;
      say(next ? `Haal eerst ${lessonLabel(next)}, dan gaat deze open.` : 'Deze les gaat later open.');
    }
  }

  const name = a.profile?.display_name?.trim();
  return (
    <div class="page stack-lg">
      <div class="hello">
        <div class="stack-sm">
          <h1 class="display">{name ? `Hoi ${name}` : 'Hoi!'}</h1>
          <p class="lead muted">Tijd om te oefenen</p>
        </div>
        <div class="pills">
          <span class="pill" title="Dagen op rij"><Icon name="flame" size={20} /> {a.profile?.current_streak ?? 0}<span class="visually-hidden"> dagen op rij</span></span>
          <span class="pill pill-gem" title="Totaal XP"><Icon name="gem" size={20} /> {a.profile?.total_xp ?? 0}<span class="visually-hidden"> XP</span></span>
        </div>
      </div>

      {l.pendingCount > 0 ? (
        <Notice kind="warning">{l.pendingCount} {l.pendingCount === 1 ? 'les is' : 'lessen zijn'} nog niet opgeslagen. Dat gebeurt vanzelf zodra je weer online bent.</Notice>
      ) : null}

      {l.loading ? <Spinner label="Leerpad laden…" /> : null}

      {!l.loading && l.units.map((unit) => {
        const done = unit.gestures.filter((g) => g.status === 'COMPLETED').length;
        const total = unit.gestures.length;
        return (
          <section key={unit.id} class="unit" aria-labelledby={`unit-${unit.id}`}>
            <div class="unit-head">
              <div class="stack-sm" style={{ gap: '2px' }}>
                <h2 id={`unit-${unit.id}`} class="heading">{unit.title}</h2>
                <span class="caption">{done} van {total} gehaald</span>
              </div>
              <span class="unit-bar" aria-hidden="true"><span style={{ width: `${total ? (done / total) * 100 : 0}%` }} /></span>
            </div>
            <ol class="path">
              {unit.gestures.map((g, i) => {
                const isCurrent = g.id === l.nextId;
                const cls = g.status === 'COMPLETED' ? 'node-done'
                  : g.status === 'AVAILABLE' ? `node-open${isCurrent ? ' node-current' : ''}`
                    : g.status === 'COMING_SOON' ? 'node-soon' : 'node-locked';
                const stateText = g.status === 'COMPLETED' ? 'gehaald' : g.status === 'AVAILABLE' ? 'open' : g.status === 'COMING_SOON' ? 'komt binnenkort' : 'op slot';
                return (
                  <li key={g.id} style={{ transform: `translateX(${OFFSETS[i % OFFSETS.length]}px)` }}>
                    {isCurrent ? <span class="start-tag" aria-hidden="true">START</span> : null}
                    <button
                      type="button"
                      ref={isCurrent ? currentRef : undefined}
                      class={`node ${cls}`}
                      aria-disabled={g.status === 'LOCKED' || g.status === 'COMING_SOON' ? 'true' : undefined}
                      aria-label={`${lessonLabel(g)}, ${stateText}`}
                      onClick={() => open(g)}
                    >
                      {g.name}
                      {g.status === 'COMPLETED' ? <span class="badge"><Icon name="check" size={16} /></span> : null}
                      {g.status === 'LOCKED' ? <span class="badge"><Icon name="lock" size={14} /></span> : null}
                    </button>
                  </li>
                );
              })}
            </ol>
          </section>
        );
      })}

      <div aria-live="polite" class="toast-region">
        {toast ? <div class="toast" role="status">{toast}</div> : null}
      </div>
    </div>
  );
}
