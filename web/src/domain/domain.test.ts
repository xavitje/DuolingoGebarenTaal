// Dezelfde tests als de Kotlin-app (shared/src/commonTest), zodat de browser en Android gelijk
// blijven beoordelen. Pas je regels aan in Kotlin, pas ze hier ook aan.
import { describe, expect, it } from 'vitest';
import { normalizePose } from './normalize';
import { DynamicGestureScorer, StaticGestureScorer } from './scoring';
import { repStep, type RepStep } from './repCounter';
import { applyProgress, applyToUnit, nextLessonId } from './curriculum';
import { mergeTemplates } from './templates';
import type { CurriculumGesture, HandPose, LandmarkPoint, SignTemplate } from './types';

function pose(wristX = 0.3, timestampMs = 0, transform: (i: number, p: LandmarkPoint) => LandmarkPoint = (_, p) => p): HandPose {
  return {
    landmarks: Array.from({ length: 21 }, (_, index) =>
      transform(index, { x: wristX + (index % 5) * 0.025, y: 0.8 - index * 0.018, z: -index * 0.002 }),
    ),
    timestampMs,
  };
}
const staticTemplate = (frame: HandPose = pose()): SignTemplate => ({ id: 'letter_a', name: 'letter_a', frames: [frame], rules: [] });
const dynamicTemplate = (): SignTemplate => ({
  id: 'swipe_right',
  name: 'Swipe rechts',
  frames: [pose(0.3, 0), pose(0.45, 200), pose(0.6, 400)],
  rules: [{ type: 'MOTION_DIRECTION', targetValue: 0.2, minDurationMs: 150 }],
});

describe('LandmarkNormalizer', () => {
  it('pols wordt oorsprong en middelvinger-MCP bepaalt de schaal', () => {
    const n = normalizePose(pose())!;
    expect(n.landmarks[0].x).toBeCloseTo(0, 4);
    expect(n.landmarks[0].y).toBeCloseTo(0, 4);
    const m = n.landmarks[9];
    expect(Math.sqrt(m.x * m.x + m.y * m.y)).toBeCloseTo(1, 4);
  });
  it('spiegelen gebeurt voor het centreren', () => {
    const normal = normalizePose(pose())!;
    const mirrored = normalizePose(pose(), true)!;
    expect(mirrored.landmarks[5].x).toBeCloseTo(-normal.landmarks[5].x, 4);
    expect(mirrored.landmarks[5].y).toBeCloseTo(normal.landmarks[5].y, 4);
  });
  it('onvolledige en ongeldige poses worden geweigerd', () => {
    expect(normalizePose({ landmarks: pose().landmarks.slice(0, 20), timestampMs: 0 })).toBeNull();
    expect(normalizePose(pose(0.3, 0, (i, p) => (i === 4 ? { ...p, x: NaN } : p)))).toBeNull();
  });
});

describe('GestureScorer', () => {
  const scorer = new StaticGestureScorer();
  it('perfecte pose scoort 1', () => {
    const frame = pose();
    const r = scorer.score([frame], staticTemplate(frame));
    expect(r.score).toBeCloseTo(1, 4);
    expect(r.isCorrect).toBe(true);
  });
  it('kleine en grote afwijking geven geordende scores', () => {
    const small = pose(0.3, 0, (i, p) => (i === 8 ? { ...p, x: p.x + 0.02 } : p));
    const reference = pose();
    const large = pose(0.3, 0, (i, p) => {
      if (i > 4 && i % 4 === 0) {
        const mcp = reference.landmarks[i - 3];
        return { x: mcp.x + 0.005, y: mcp.y + 0.005, z: mcp.z };
      }
      return p;
    });
    const s = scorer.score([small], staticTemplate());
    const l = scorer.score([large], staticTemplate());
    expect(s.isCorrect).toBe(true);
    expect(s.score).toBeGreaterThan(l.score);
    expect(l.isCorrect).toBe(false);
  });
  it('verschoven vingers scoren lager dan een kleine afwijking', () => {
    const small = pose(0.3, 0, (i, p) => (i === 8 ? { ...p, x: p.x + 0.02 } : p));
    const shifted = pose(0.3, 0, (i, p) => (i > 4 ? { ...p, x: p.x + 0.4 } : p));
    expect(scorer.score([small], staticTemplate()).score).toBeGreaterThan(scorer.score([shifted], staticTemplate()).score);
  });
  it('ontbrekende landmarks geven veilige feedback', () => {
    const r = scorer.score([{ landmarks: [], timestampMs: 0 }], staticTemplate());
    expect(r.score).toBe(0);
    expect(r.isCorrect).toBe(false);
  });
  it('beweging naar rechts is goed, naar links niet', () => {
    const d = new DynamicGestureScorer();
    const right = [pose(0.3, 0), pose(0.45, 200), pose(0.6, 400)];
    const left = [...right].reverse().map((f, i) => ({ ...f, timestampMs: i * 200 }));
    expect(d.score(right, dynamicTemplate()).isCorrect).toBe(true);
    expect(d.score(left, dynamicTemplate()).isCorrect).toBe(false);
  });
});

describe('RepCounter', () => {
  function play(frames: [number, number][], required = 3): [RepStep, boolean] {
    let reps = 0;
    let waiting = false;
    let since: number | null = null;
    let done = false;
    let last: RepStep = { repsDone: 0, waitingForRelease: false, highScoreSinceMs: null, completed: false };
    for (const [score, t] of frames) {
      last = repStep({ score, running: true, timestampMs: t, highScoreSinceMs: since, waitingForRelease: waiting, repsDone: reps, repsRequired: required });
      reps = last.repsDone;
      waiting = last.waitingForRelease;
      since = last.highScoreSinceMs;
      if (last.completed) done = true;
    }
    return [last, done];
  }
  const hold = (from: number): [number, number][] => [[0.95, from], [0.95, from + 800], [0.95, from + 1600]];
  const release = (at: number): [number, number][] => [[0.3, at]];

  it('één keer vasthouden telt één herhaling', () => {
    const [s, done] = play(hold(0));
    expect(s.repsDone).toBe(1);
    expect(s.waitingForRelease).toBe(true);
    expect(done).toBe(false);
  });
  it('hand omhoog houden telt niet dubbel', () => {
    expect(play([...hold(0), ...hold(2000)])[0].repsDone).toBe(1);
  });
  it('drie keer met loslaten haalt de les', () => {
    const [s, done] = play([...hold(0), ...release(2000), ...hold(3000), ...release(5000), ...hold(6000)]);
    expect(s.repsDone).toBe(3);
    expect(done).toBe(true);
  });
  it('te kort vasthouden telt niet', () => {
    expect(play([[0.95, 0], [0.95, 1000], [0.5, 1200]])[0].repsDone).toBe(0);
  });
});

describe('LessonUnlocker', () => {
  const g = (id: string, template = true, video: string | null = null): CurriculumGesture => ({
    id, name: id, category: 'letter', orderIndex: 0, unitId: 'u', hasTemplate: template, demoVideoUrl: video, language: 'ngt', status: 'LOCKED',
  });
  const statuses = (list: CurriculumGesture[], done: string[] = []) => applyToUnit(list, new Set(done)).map((x) => x.status);

  it('eerste les is altijd open', () => expect(statuses([g('a'), g('b'), g('c')])).toEqual(['AVAILABLE', 'LOCKED', 'LOCKED']));
  it('gehaalde les opent de volgende', () => expect(statuses([g('a'), g('b'), g('c')], ['a'])).toEqual(['COMPLETED', 'AVAILABLE', 'LOCKED']));
  it('les met alleen een video is open', () => expect(statuses([g('a', false, 'https://x/a.mp4'), g('b')])).toEqual(['AVAILABLE', 'LOCKED']));
  it('les zonder content blokkeert het pad niet', () =>
    expect(statuses([g('a'), g('b', false), g('c')], ['a'])).toEqual(['COMPLETED', 'COMING_SOON', 'AVAILABLE']));
  it('binnenkort na een dichte les opent de rest niet', () =>
    expect(statuses([g('a'), g('b'), g('c', false), g('d')])).toEqual(['AVAILABLE', 'LOCKED', 'COMING_SOON', 'LOCKED']));
  it('elke unit begint open', () => {
    const units = applyProgress(
      [
        { id: 'alphabet', title: 'Alfabet', language: 'ngt', gestures: [g('a'), g('b')] },
        { id: 'numbers', title: 'Cijfers', language: 'ngt', gestures: [g('1'), g('2')] },
      ],
      new Set(),
    );
    expect(units[1].gestures[0].status).toBe('AVAILABLE');
    expect(nextLessonId(units)).toBe('a');
  });
  it('geen volgende les als niets content heeft', () => {
    const units = applyProgress([{ id: 'core', title: 'Basis', language: 'ngt', gestures: [g('x', false)] }], new Set());
    expect(nextLessonId(units)).toBeNull();
  });
});

describe('Templates samenvoegen', () => {
  it('opnames worden varianten of een nieuw template', () => {
    const lm = pose().landmarks;
    const merged = mergeTemplates([staticTemplate()], [
      { gesture_id: 'letter_a', landmarks: lm, handedness: 'RIGHT' },
      { gesture_id: 'letter_c', landmarks: lm, gestures: { category: 'letter', unit_id: 'alphabet' } },
      { gesture_id: 'letter_c', landmarks: lm },
    ], 'ngt');
    expect(merged.find((t) => t.id === 'letter_a')!.referenceVariants).toHaveLength(1);
    const c = merged.find((t) => t.id === 'letter_c')!;
    expect(c.name).toBe('C');
    expect(c.referenceVariants).toHaveLength(1);
    expect(c.rules[0]).toEqual({ type: 'MIN_STABLE_FRAMES', targetValue: 5 });
  });
});
