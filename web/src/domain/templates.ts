// Port van SyncingTemplateRepository.kt: de gebundelde templates.json plus alle goedgekeurde opnames
// uit Supabase. Voor een gebaar met een gebundeld template worden opnames extra referentievarianten;
// voor een gebaar zonder gebundeld template wordt er een template van de opnames gemaakt.
import type { HandPose, Handedness, LandmarkPoint, SignTemplate } from './types';

export interface ApprovedSample {
  gesture_id: string;
  landmarks: LandmarkPoint[];
  handedness?: string | null;
  sign_language?: string | null;
  gestures?: { category?: string | null; unit_id?: string | null } | null;
}

function toPose(s: ApprovedSample): HandPose {
  const h = s.handedness === 'LEFT' || s.handedness === 'RIGHT' ? (s.handedness as Handedness) : 'UNKNOWN';
  return { landmarks: s.landmarks, timestampMs: 0, handedness: h };
}

export function mergeTemplates(bundled: SignTemplate[], approved: ApprovedSample[], language: string): SignTemplate[] {
  const base = language === 'ngt' ? bundled : [];
  const byGesture = new Map<string, ApprovedSample[]>();
  for (const s of approved) {
    if (!Array.isArray(s.landmarks) || s.landmarks.length !== 21) continue;
    const list = byGesture.get(s.gesture_id) ?? [];
    list.push(s);
    byGesture.set(s.gesture_id, list);
  }
  const baseIds = new Set(base.map((t) => t.id));

  const enriched = base.map((t) => ({
    ...t,
    referenceVariants: [...(t.referenceVariants ?? []), ...(byGesture.get(t.id) ?? []).map(toPose)],
  }));

  const synthesized: SignTemplate[] = [];
  for (const [gestureId, samples] of byGesture) {
    if (baseIds.has(gestureId)) continue;
    const poses = samples.map(toPose);
    if (poses.length === 0) continue;
    const meta = samples.find((s) => s.gestures)?.gestures;
    synthesized.push({
      id: gestureId,
      name: (gestureId.includes('_') ? gestureId.slice(gestureId.indexOf('_') + 1) : gestureId).toUpperCase(),
      frames: [poses[0]],
      rules: [{ type: 'MIN_STABLE_FRAMES', targetValue: 5 }],
      referenceVariants: poses.slice(1),
      category: meta?.category ?? 'letter',
      unitId: meta?.unit_id ?? 'alphabet',
      language,
    });
  }
  return [...enriched, ...synthesized];
}
