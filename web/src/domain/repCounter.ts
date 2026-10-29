// Port van RepCounter.kt. Een herhaling telt als de score minstens HOLD_SCORE is gedurende HOLD_MS.
// Daarna moet de score eerst onder RELEASE_SCORE zakken (hand laten zakken) voor de volgende.
export const HOLD_SCORE = 0.9;
export const RELEASE_SCORE = 0.6;
export const HOLD_MS = 1500;
export const REPS_REQUIRED = 3;

export interface RepStep {
  repsDone: number;
  waitingForRelease: boolean;
  highScoreSinceMs: number | null;
  /** True precies op het moment dat de laatste herhaling gehaald is. */
  completed: boolean;
}

export function repStep(args: {
  score: number;
  running: boolean;
  timestampMs: number;
  highScoreSinceMs: number | null;
  waitingForRelease: boolean;
  repsDone: number;
  repsRequired: number;
}): RepStep {
  const { score, running, timestampMs, highScoreSinceMs, waitingForRelease, repsDone, repsRequired } = args;
  if (!running || repsDone >= repsRequired) {
    return { repsDone, waitingForRelease, highScoreSinceMs: null, completed: false };
  }
  if (waitingForRelease) {
    return { repsDone, waitingForRelease: score >= RELEASE_SCORE, highScoreSinceMs: null, completed: false };
  }
  if (score < HOLD_SCORE) {
    return { repsDone, waitingForRelease: false, highScoreSinceMs: null, completed: false };
  }
  const since = highScoreSinceMs ?? timestampMs;
  if (timestampMs - since < HOLD_MS) {
    return { repsDone, waitingForRelease: false, highScoreSinceMs: since, completed: false };
  }
  const reps = repsDone + 1;
  return reps >= repsRequired
    ? { repsDone: reps, waitingForRelease: false, highScoreSinceMs: null, completed: true }
    : { repsDone: reps, waitingForRelease: true, highScoreSinceMs: null, completed: false };
}
