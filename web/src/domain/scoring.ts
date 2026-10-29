// Port van StaticGestureScorer.kt en DynamicGestureScorer.kt. Zelfde gewichten, drempels en
// feedbackteksten als de Android-app, zodat een gebaar in de browser even streng wordt beoordeeld.
import { normalizePose, type CanonicalPose } from './normalize';
import {
  type FeedbackResult,
  type HandPose,
  type LandmarkPoint,
  type SignTemplate,
  blendshapeScore,
  isFinitePoint,
} from './types';

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
const average = (xs: number[]) => (xs.length === 0 ? NaN : xs.reduce((a, b) => a + b, 0) / xs.length);

export function scoreFromDistance(meanDistance: number, maxDistance: number): number {
  return !Number.isFinite(meanDistance) || maxDistance <= 0 ? 0 : clamp(1 - meanDistance / maxDistance, 0, 1);
}

// ─── Statisch ──────────────────────────────────────────────────────────────────

const DEFAULT_MAX_DISTANCE = 1.25;
const DEFAULT_CORRECT_THRESHOLD = 0.75;
const DEFAULT_NON_MANUAL_WEIGHT = 0.2;
const DEFAULT_BLENDSHAPE_TOLERANCE = 0.3;
const MIN_TOLERANCE = 0.05;
const TWO_HAND_IMBALANCE_FACTOR = 1.3;
const FACE_FEEDBACK_THRESHOLD = 0.5;
const FINGER_CHAINS = [
  [1, 2, 3, 4], // duim
  [5, 6, 7, 8], // wijsvinger
  [9, 10, 11, 12], // middelvinger
  [13, 14, 15, 16], // ringvinger
  [17, 18, 19, 20], // pink
];
const MIN_CHAIN_LENGTH = 0.0001;
const CURL_MAX_DISTANCE = 0.5;
const CURL_WEIGHT = 0.5;

interface CandidateResult {
  score: number;
  primaryDistance: number;
  secondaryDistance: number | null;
  faceScore: number | null;
}

function euclidean(a: LandmarkPoint, b: LandmarkPoint): number {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

/** Per vinger: rechte afstand MCP->TIP gedeeld door de opgetelde segmentlengte (1 = gestrekt). */
function fingerCurlRatios(landmarks: LandmarkPoint[]): number[] {
  return FINGER_CHAINS.map((chain) => {
    const straight = euclidean(landmarks[chain[0]], landmarks[chain[3]]);
    let chainLength = 0;
    for (let i = 0; i < chain.length - 1; i++) chainLength += euclidean(landmarks[chain[i]], landmarks[chain[i + 1]]);
    return chainLength < MIN_CHAIN_LENGTH ? 1 : clamp(straight / chainLength, 0, 1.2);
  });
}

function averageCurl(frames: HandPose[]): number[] {
  const perFrame = frames.map((f) => fingerCurlRatios(f.landmarks));
  return FINGER_CHAINS.map((_, i) => average(perFrame.map((c) => c[i])));
}

function meanDistance(actual: CanonicalPose[], expected: CanonicalPose): number {
  const distances: number[] = [];
  for (const a of actual) {
    const n = Math.min(a.landmarks.length, expected.landmarks.length);
    for (let i = 0; i < n; i++) distances.push(euclidean(a.landmarks[i], expected.landmarks[i]));
  }
  return average(distances);
}

const invalidPoseFeedback = (): FeedbackResult => ({
  score: 0,
  isCorrect: false,
  feedbackMessages: ['Houd één hand volledig in beeld.'],
});

export class StaticGestureScorer {
  constructor(
    private readonly maxDistance = DEFAULT_MAX_DISTANCE,
    private readonly correctThreshold = DEFAULT_CORRECT_THRESHOLD,
    private readonly nonManualWeight = DEFAULT_NON_MANUAL_WEIGHT,
  ) {}

  score(input: HandPose[], template: SignTemplate): FeedbackResult {
    const stableRule = template.rules.find((r) => r.type === 'MIN_STABLE_FRAMES');
    const requiredFrames = stableRule?.targetValue != null ? Math.max(1, Math.trunc(stableRule.targetValue)) : 1;
    if (input.length < requiredFrames) {
      return { score: 0, isCorrect: false, feedbackMessages: ['Houd de handvorm nog even stabiel.'] };
    }

    const actualFrames = input.slice(-requiredFrames);
    const actualPrimary = actualFrames.map((f) => normalizePose(f)).filter((p): p is CanonicalPose => p != null);
    if (actualPrimary.length !== requiredFrames) return invalidPoseFeedback();

    const first = template.frames[0];
    if (!first) return invalidPoseFeedback();
    const candidates = [first, ...(template.referenceVariants ?? [])];

    const requiresTwoHands =
      template.rules.some((r) => r.type === 'TWO_HAND_PRESENCE') || candidates.some((c) => c.secondaryHandLandmarks != null);
    if (requiresTwoHands && actualFrames.some((f) => f.secondaryHandLandmarks == null)) {
      return { score: 0, isCorrect: false, feedbackMessages: ['Gebruik twee handen voor dit gebaar.'] };
    }

    let best: CandidateResult | null = null;
    for (const candidate of candidates) {
      const result = this.scoreAgainst(candidate, actualFrames, actualPrimary, template);
      if (result && (best == null || result.score > best.score)) best = result;
    }
    if (!best) return invalidPoseFeedback();
    const isCorrect = best.score >= this.correctThreshold;

    let message: string;
    if (isCorrect) message = 'Goed gedaan! Houd deze positie even vast.';
    else if (requiresTwoHands && best.secondaryDistance != null && best.secondaryDistance > best.primaryDistance * TWO_HAND_IMBALANCE_FACTOR)
      message = 'Let op de positie van je andere hand.';
    else if (best.faceScore != null && best.faceScore < FACE_FEEDBACK_THRESHOLD) message = 'Let ook op je mondbeeld bij dit gebaar.';
    else if (best.score >= 0.5) message = 'Bijna goed. Kijk nog eens naar de stand van je vingers.';
    else message = 'Breng je hele hand in beeld en vorm het gebaar opnieuw.';

    return { score: best.score, isCorrect, feedbackMessages: [message] };
  }

  private scoreAgainst(
    expected: HandPose,
    actualFrames: HandPose[],
    actualPrimary: CanonicalPose[],
    template: SignTemplate,
  ): CandidateResult | null {
    const expectedPrimary = normalizePose(expected);
    if (!expectedPrimary) return null;
    const primaryDistance = meanDistance(actualPrimary, expectedPrimary);

    let secondaryDistance: number | null = null;
    const expectedSecondary = expected.secondaryHandLandmarks
      ? normalizePose({ landmarks: expected.secondaryHandLandmarks, timestampMs: expected.timestampMs })
      : null;
    if (expectedSecondary) {
      const actualSecondary = actualFrames
        .map((f) => (f.secondaryHandLandmarks ? normalizePose({ landmarks: f.secondaryHandLandmarks, timestampMs: f.timestampMs }) : null))
        .filter((p): p is CanonicalPose => p != null);
      secondaryDistance = actualSecondary.length === actualFrames.length ? meanDistance(actualSecondary, expectedSecondary) : null;
    }

    const handMean = secondaryDistance != null ? (primaryDistance + secondaryDistance) / 2 : primaryDistance;
    const positionScore = scoreFromDistance(handMean, this.maxDistance);

    const actualCurl = averageCurl(actualFrames);
    const expectedCurl = fingerCurlRatios(expected.landmarks);
    const curlDistance = average(actualCurl.map((c, i) => Math.abs(c - expectedCurl[i])));
    const curlScore = clamp(1 - curlDistance / CURL_MAX_DISTANCE, 0, 1);
    const handScore = clamp((1 - CURL_WEIGHT) * positionScore + CURL_WEIGHT * curlScore, 0, 1);

    const faceRules = template.rules.filter((r) => r.type === 'FACE_BLENDSHAPE' && r.blendshapeName);
    let faceScore: number | null = null;
    if (faceRules.length > 0) {
      const latest = actualFrames[actualFrames.length - 1];
      faceScore = average(
        faceRules.map((rule) => {
          const target = rule.targetValue ?? 0;
          const tolerance = Math.max(rule.tolerance ?? DEFAULT_BLENDSHAPE_TOLERANCE, MIN_TOLERANCE);
          return clamp(1 - Math.abs(blendshapeScore(latest, rule.blendshapeName!) - target) / tolerance, 0, 1);
        }),
      );
    }

    const score = faceScore != null ? clamp((1 - this.nonManualWeight) * handScore + this.nonManualWeight * faceScore, 0, 1) : handScore;
    return { score, primaryDistance, secondaryDistance, faceScore };
  }
}

// ─── Bewegend ──────────────────────────────────────────────────────────────────

const DIRECTION_WEIGHT = 0.75;
const SHAPE_WEIGHT = 0.25;
const WRIST = 0;

export class DynamicGestureScorer {
  constructor(
    private readonly defaultMinDisplacement = 0.12,
    private readonly defaultMinDurationMs = 150,
    private readonly defaultMaxDurationMs = 2500,
  ) {}

  score(input: HandPose[], template: SignTemplate): FeedbackResult {
    const ordered = input
      .filter((p) => p.landmarks.length === 21 && p.timestampMs >= 0 && p.landmarks.every(isFinitePoint))
      .sort((a, b) => a.timestampMs - b.timestampMs);
    const newest = ordered.length > 0 ? ordered[ordered.length - 1].timestampMs : null;
    const valid = newest == null ? [] : ordered.filter((p) => newest - p.timestampMs <= this.defaultMaxDurationMs);
    if (valid.length < 3) return feedback(0, false, 'Maak de beweging iets langer.');

    const first = valid[0];
    const last = valid[valid.length - 1];
    const duration = last.timestampMs - first.timestampMs;
    const motionRule = template.rules.find((r) => r.type === 'MOTION_DIRECTION');
    const minDuration = motionRule?.minDurationMs ?? this.defaultMinDurationMs;
    if (duration < minDuration) return feedback(0.15, false, 'Beweeg iets rustiger van links naar rechts.');

    const displacement = last.landmarks[WRIST].x - first.landmarks[WRIST].x;
    const minDisplacement =
      motionRule?.targetValue != null ? Math.abs(motionRule.targetValue) : motionRule?.tolerance ?? this.defaultMinDisplacement;
    const directionScore = clamp(displacement / minDisplacement, 0, 1);
    const shapeScore = new StaticGestureScorer().score(valid, template).score;
    const score = clamp(DIRECTION_WEIGHT * directionScore + SHAPE_WEIGHT * shapeScore, 0, 1);
    const isCorrect = displacement >= minDisplacement && score >= DEFAULT_CORRECT_THRESHOLD;

    if (isCorrect) return feedback(score, true, 'Mooie beweging naar rechts!');
    if (displacement <= 0) return feedback(score, false, 'Beweeg je hand van links naar rechts.');
    if (displacement < minDisplacement) return feedback(score, false, 'Maak de beweging wat groter naar rechts.');
    return feedback(score, false, 'Houd de handvorm stabiel tijdens de beweging.');
  }
}

function feedback(score: number, correct: boolean, message: string): FeedbackResult {
  return { score: clamp(score, 0, 1), isCorrect: correct, feedbackMessages: [message] };
}

/** Kiest de scorer zoals CoachViewModel: bewegende gebaren (MOTION_DIRECTION) apart. */
export function scorerFor(template: SignTemplate): StaticGestureScorer | DynamicGestureScorer {
  return template.rules.some((r) => r.type === 'MOTION_DIRECTION') ? new DynamicGestureScorer() : new StaticGestureScorer();
}
