// Leerpad, templates en voortgang. Een gehaalde les wordt meteen lokaal onthouden en naar Supabase
// gestuurd; lukt dat niet (geen verbinding), dan blijft hij in een wachtrij tot er weer verbinding is.
import {
  checkAndAwardBadges,
  fetchApprovedSamples,
  fetchBundledTemplates,
  fetchCompletedGestureIds,
  fetchGestures,
  logPracticeAttempt,
  markPracticeDay,
  recordPractice,
} from './api';
import { auth, refreshProfile, signLanguage } from './auth';
import { Store, local } from './store';
import { ApiError } from './supabase';
import { applyProgress, buildUnits, fallbackGestures, nextLessonId, type GestureRow } from '../domain/curriculum';
import { mergeTemplates } from '../domain/templates';
import type { CurriculumUnit, SignTemplate } from '../domain/types';

export interface LearningState {
  loading: boolean;
  language: string;
  units: CurriculumUnit[];
  templates: SignTemplate[];
  nextId: string | null;
  pendingCount: number;
}

export const learning = new Store<LearningState>({ loading: true, language: 'ngt', units: [], templates: [], nextId: null, pendingCount: 0 });

let rawUnits: CurriculumUnit[] = [];
let loadedFor: string | null = null;

interface PendingPractice {
  gestureId: string;
  language: string;
  score: number;
  at: number;
}

const pendingKey = (userId: string) => `sc-pending-${userId}`;
const doneKey = (userId: string, lang: string) => `sc-done-${userId}-${lang}`;
const todayKey = (userId: string) => `sc-today-${userId}`;

function localDone(userId: string, lang: string): Set<string> {
  return new Set(local.get<string[]>(doneKey(userId, lang), []));
}

function rememberDone(userId: string, lang: string, ids: Iterable<string>) {
  const set = localDone(userId, lang);
  for (const id of ids) set.add(id);
  local.set(doneKey(userId, lang), [...set]);
}

function apply(userId: string, lang: string) {
  const units = applyProgress(rawUnits, localDone(userId, lang));
  learning.set((s) => ({
    ...s,
    units,
    nextId: nextLessonId(units),
    pendingCount: local.get<PendingPractice[]>(pendingKey(userId), []).length,
  }));
}

export async function loadLearning(force = false): Promise<void> {
  const a = auth.get();
  if (!a.userId) return;
  const lang = signLanguage(a);
  const key = `${a.userId}:${lang}`;
  if (!force && loadedFor === key) return;
  loadedFor = key;
  learning.set((s) => ({ ...s, loading: true, language: lang }));

  const [bundled, approved, rows] = await Promise.all([
    fetchBundledTemplates().catch(() => [] as SignTemplate[]),
    fetchApprovedSamples(lang).catch(() => null),
    fetchGestures(lang).catch(() => null),
  ]);
  const templates = mergeTemplates(bundled, approved ?? [], lang);
  const gestureRows: GestureRow[] = rows && rows.length > 0 ? rows : fallbackGestures(lang);
  rawUnits = buildUnits(gestureRows, templates, lang);
  learning.set((s) => ({ ...s, templates }));
  apply(a.userId, lang);
  learning.set((s) => ({ ...s, loading: false }));

  // Wat op een ander apparaat (de tablet) al gehaald is, ook hier als gehaald tonen.
  const server = await fetchCompletedGestureIds(lang).catch(() => null);
  if (server) {
    rememberDone(a.userId, lang, server);
    apply(a.userId, lang);
  }
  void flushPending();
}

/** Volgende les na het halen van deze, voor de knop "Volgend gebaar". */
export function nextAfter(gestureId: string): string | null {
  const a = auth.get();
  if (!a.userId) return null;
  const done = localDone(a.userId, signLanguage(a));
  done.add(gestureId);
  return nextLessonId(applyProgress(rawUnits, done));
}

function xpForLocal(userId: string, gestureId: string): number {
  // Zelfde regel als de server: de eerste keer per gebaar per dag 15 XP, daarna 2 XP.
  const d = new Date();
  const today = `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
  const stored = local.get<{ date: string; ids: string[] }>(todayKey(userId), { date: today, ids: [] });
  const ids = stored.date === today ? stored.ids : [];
  const xp = ids.includes(gestureId) ? 2 : 15;
  local.set(todayKey(userId), { date: today, ids: [...new Set([...ids, gestureId])] });
  return xp;
}

/**
 * Les gehaald. Geeft de verdiende XP terug. Met camera: ook de anonieme pilotmeting (gelukt);
 * met zelfcheck: alleen een oefendag (zoals de app).
 */
export async function completeLesson(gestureId: string, score: number, mode: 'camera' | 'self'): Promise<number> {
  const a = auth.get();
  if (!a.userId) return 0;
  const lang = signLanguage(a);
  rememberDone(a.userId, lang, [gestureId]);
  apply(a.userId, lang);
  const localXp = xpForLocal(a.userId, gestureId);

  if (mode === 'camera') void logPracticeAttempt(gestureId, true).catch(() => undefined);
  else void markPracticeDay().catch(() => undefined);

  const before = a.profile?.total_xp ?? null;
  try {
    await recordPractice(gestureId, score);
    void checkAndAwardBadges().catch(() => undefined);
    const after = await refreshProfile();
    if (before != null && after && after.total_xp > before) return after.total_xp - before;
    return localXp;
  } catch (e) {
    if (e instanceof ApiError && !e.isNetwork && e.status < 500) return localXp;
    const queue = local.get<PendingPractice[]>(pendingKey(a.userId), []);
    queue.push({ gestureId, language: lang, score, at: Date.now() });
    local.set(pendingKey(a.userId), queue);
    apply(a.userId, lang);
    return localXp;
  }
}

/** Opgegeven na een poging waarin wel een hand te zien was (pilotmeting, zoals de app). */
export function logGaveUp(gestureId: string) {
  void logPracticeAttempt(gestureId, false).catch(() => undefined);
}

let flushing = false;
export async function flushPending(): Promise<void> {
  const a = auth.get();
  if (!a.userId || flushing) return;
  flushing = true;
  try {
    const queue = local.get<PendingPractice[]>(pendingKey(a.userId), []);
    while (queue.length > 0) {
      try {
        await recordPractice(queue[0].gestureId, queue[0].score);
      } catch (e) {
        // Geen verbinding of serverfout: later opnieuw. Een definitieve weigering (4xx) laten we
        // vallen, anders blijft hij eeuwig in de wachtrij staan.
        if (!(e instanceof ApiError && !e.isNetwork && e.status < 500)) break;
      }
      queue.shift();
      local.set(pendingKey(a.userId), queue);
    }
    if (queue.length === 0) {
      void checkAndAwardBadges().catch(() => undefined);
      await refreshProfile();
    }
    learning.set((s) => ({ ...s, pendingCount: queue.length }));
  } finally {
    flushing = false;
  }
}

window.addEventListener('online', () => void flushPending());

export function resetLearning() {
  loadedFor = null;
  rawUnits = [];
  learning.set({ loading: true, language: 'ngt', units: [], templates: [], nextId: null, pendingCount: 0 });
}
