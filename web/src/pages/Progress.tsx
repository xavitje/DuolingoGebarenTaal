import { useEffect, useState } from 'preact/hooks';
import { type Badge, fetchBadges, fetchEarnedBadgeIds, getTodayXp } from '../lib/api';
import { auth, refreshProfile } from '../lib/auth';
import { flushPending, learning } from '../lib/learning';
import { useStore } from '../lib/store';
import { Icon, Notice, Spinner } from '../components/ui';

export function ProgressPage() {
  const a = useStore(auth);
  const l = useStore(learning);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [todayXp, setTodayXp] = useState(0);
  const [badges, setBadges] = useState<Badge[]>([]);
  const [earned, setEarned] = useState<Set<string>>(new Set());

  useEffect(() => {
    let alive = true;
    (async () => {
      await flushPending();
      const [profile, xp, all, mine] = await Promise.all([
        refreshProfile(),
        getTodayXp().catch(() => null),
        fetchBadges().catch(() => [] as Badge[]),
        fetchEarnedBadgeIds().catch(() => [] as string[]),
      ]);
      if (!alive) return;
      setFailed(!profile && xp == null);
      setTodayXp(xp ?? 0);
      setBadges(all);
      setEarned(new Set(mine));
      setLoading(false);
    })();
    return () => { alive = false; };
  }, []);

  const p = a.profile;
  const goal = p?.daily_goal_xp || 35;
  const pct = Math.min(1, todayXp / goal);
  const r = 46;
  const c = 2 * Math.PI * r;

  return (
    <div class="page stack-lg">
      <h1 class="display">Voortgang</h1>
      {loading ? <Spinner /> : null}
      {failed ? <Notice>Geen internetverbinding. Controleer je verbinding.</Notice> : null}
      {l.pendingCount > 0 ? <Notice kind="warning">{l.pendingCount} niet gesynchroniseerd. Wordt bijgewerkt zodra je online bent.</Notice> : null}

      {!loading && p ? (
        <>
          {p.total_xp === 0 ? (
            <div class="card-flat stack-sm">
              <h2 class="heading">Je hebt nog niets geoefend</h2>
              <p>Begin met je eerste gebaar om je reeks en XP te zien groeien.</p>
            </div>
          ) : null}
          <div class="card ring-wrap">
            <svg width="112" height="112" viewBox="0 0 112 112" aria-hidden="true">
              <circle cx="56" cy="56" r={r} stroke="var(--surface-raised)" stroke-width="12" fill="none" />
              <circle cx="56" cy="56" r={r} stroke={pct >= 1 ? 'var(--clover)' : 'var(--coral)'} stroke-width="12" fill="none" stroke-linecap="round"
                stroke-dasharray={c} stroke-dashoffset={c * (1 - pct)} transform="rotate(-90 56 56)" />
            </svg>
            <div class="stack-sm">
              <h2 class="heading">Dagdoel</h2>
              <p class="lead"><strong>{todayXp} / {goal} XP</strong></p>
              <p class="muted">{pct >= 1 ? 'Dagdoel gehaald!' : `Nog ${goal - todayXp} XP voor je dagdoel`}</p>
            </div>
          </div>
          <div class="grid-3">
            <div class="card stat">
              <span class="muted">Dagen op rij</span>
              <strong>{p.current_streak}</strong>
              <span class="caption">Langste reeks: {p.longest_streak}</span>
            </div>
            <div class="card stat">
              <span class="muted">Totaal XP</span>
              <strong>{p.total_xp}</strong>
            </div>
            <div class="card stat">
              <span class="muted">Reeks-bescherming</span>
              <strong>{p.streak_freezes_available}</strong>
              <span class="caption">Mis je een dag? Dan blijft je reeks toch staan. Je krijgt er 1 per maand.</span>
            </div>
          </div>
          {badges.length > 0 ? (
            <section class="stack" aria-labelledby="badges-kop">
              <h2 id="badges-kop" class="title">Je badges</h2>
              <ul class="badges">
                {badges.map((b) => {
                  const has = earned.has(b.id);
                  return (
                    <li key={b.id} class={`badge-item${has ? ' earned' : ''}`}>
                      <span class="medal" aria-hidden="true"><Icon name={has ? 'star' : 'lock'} size={26} /></span>
                      <span>{b.name}</span>
                      <span class="caption">{has ? 'Behaald' : b.description}</span>
                    </li>
                  );
                })}
              </ul>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
