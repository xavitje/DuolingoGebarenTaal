// Leerpad: port van CurriculumRepository.kt en LessonUnlocker.kt.
import { type CurriculumGesture, type CurriculumUnit, type LessonStatus, type SignTemplate, hasContent } from './types';

export interface GestureRow {
  id: string;
  name: string;
  category?: string | null;
  order_index?: number | null;
  unit_id?: string | null;
  demo_video_url?: string | null;
}

const UNITS: Record<string, { id: string; title: string }[]> = {
  ngt: [
    { id: 'alphabet', title: 'Alfabet' },
    { id: 'numbers', title: 'Cijfers' },
  ],
  asl: [{ id: 'core_words', title: 'Basiswoorden' }],
};

/** Offline terugval als de gestures-tabel niet bereikbaar is (zelfde lijst als de app). */
export function fallbackGestures(language: string): GestureRow[] {
  if (language === 'ngt') {
    return [
      ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((c, i) => ({
        id: `letter_${c.toLowerCase()}`, name: c, category: 'letter', order_index: i, unit_id: 'alphabet',
      })),
      ...Array.from({ length: 10 }, (_, n) => ({
        id: `number_${n}`, name: String(n), category: 'number', order_index: n, unit_id: 'numbers',
      })),
    ];
  }
  if (language === 'asl') {
    return ['hello', 'please', 'thankyou', 'sorry', 'yes', 'no', 'help', 'more', 'again', 'finished', 'eat', 'drink', 'water',
      'want', 'like', 'love', 'happy', 'sad', 'friend', 'play'].map((w, i) => ({
      id: `asl_${w}`, name: w[0].toUpperCase() + w.slice(1), category: 'word', order_index: i, unit_id: 'core_words',
    }));
  }
  return [];
}

export function buildUnits(rows: GestureRow[], templates: SignTemplate[], language: string): CurriculumUnit[] {
  const templateIds = new Set(templates.map((t) => t.id));
  return (UNITS[language] ?? []).map((unit) => ({
    id: unit.id,
    title: unit.title,
    language,
    gestures: rows
      .filter((r) => (r.unit_id ?? 'alphabet') === unit.id)
      .sort((a, b) => (a.order_index ?? 0) - (b.order_index ?? 0))
      .map<CurriculumGesture>((r) => ({
        id: r.id,
        name: r.name,
        category: r.category ?? 'letter',
        orderIndex: r.order_index ?? 0,
        unitId: r.unit_id ?? 'alphabet',
        hasTemplate: templateIds.has(r.id),
        demoVideoUrl: r.demo_video_url ?? null,
        language,
        status: 'LOCKED',
      })),
  }));
}

/**
 * Regels (gelijk aan de app):
 * 1. De eerste les van elke unit is altijd open.
 * 2. Een les gaat open zodra de vorige gepasseerd is: gehaald, of zonder content maar zelf bereikbaar.
 * 3. Een gehaalde les blijft gehaald (en opnieuw te oefenen).
 */
export function applyToUnit(gestures: CurriculumGesture[], completed: Set<string>): CurriculumGesture[] {
  let previousPassed = true;
  return gestures.map((g) => {
    let status: LessonStatus;
    if (completed.has(g.id)) status = 'COMPLETED';
    else if (!hasContent(g)) status = 'COMING_SOON';
    else if (previousPassed) status = 'AVAILABLE';
    else status = 'LOCKED';
    previousPassed = status === 'COMPLETED' ? true : status === 'COMING_SOON' ? previousPassed : false;
    return { ...g, status };
  });
}

export function applyProgress(units: CurriculumUnit[], completed: Set<string>): CurriculumUnit[] {
  return units.map((u) => ({ ...u, gestures: applyToUnit(u.gestures, completed) }));
}

/** De les die de leerling nu moet doen: de eerste open, nog niet gehaalde les. */
export function nextLessonId(units: CurriculumUnit[]): string | null {
  for (const u of units) for (const g of u.gestures) if (g.status === 'AVAILABLE') return g.id;
  return null;
}

export function findGesture(units: CurriculumUnit[], id: string): CurriculumGesture | undefined {
  for (const u of units) for (const g of u.gestures) if (g.id === id) return g;
  return undefined;
}

/** Leesbare naam voor een les, zoals de app: "Letter A" of "Cijfer 3". */
export function lessonLabel(g: Pick<CurriculumGesture, 'category' | 'name'>): string {
  if (g.category === 'letter') return `Letter ${g.name}`;
  if (g.category === 'number') return `Cijfer ${g.name}`;
  return g.name;
}
