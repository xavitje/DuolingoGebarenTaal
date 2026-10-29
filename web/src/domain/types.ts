// Dezelfde datamodellen als in de Kotlin-app (shared/src/commonMain/.../data/model). De JSON-vorm
// is gelijk, zodat templates.json en de goedgekeurde opnames uit Supabase zonder omzetten werken.

export interface LandmarkPoint {
  x: number;
  y: number;
  z: number;
}

export type Handedness = 'LEFT' | 'RIGHT' | 'UNKNOWN';

export interface FaceBlendshape {
  name: string;
  score: number;
}

export interface HandPose {
  landmarks: LandmarkPoint[];
  timestampMs: number;
  handedness?: Handedness;
  secondaryHandLandmarks?: LandmarkPoint[] | null;
  secondaryHandedness?: Handedness;
  faceBlendshapes?: FaceBlendshape[];
}

export type RuleType =
  | 'LANDMARK_DISTANCE'
  | 'LANDMARK_ANGLE'
  | 'MOTION_DIRECTION'
  | 'MIN_STABLE_FRAMES'
  | 'FACE_BLENDSHAPE'
  | 'TWO_HAND_PRESENCE';

export interface GestureRule {
  type: RuleType;
  landmarkIndices?: number[];
  targetValue?: number | null;
  tolerance?: number | null;
  minDurationMs?: number | null;
  blendshapeName?: string | null;
}

export interface SignTemplate {
  id: string;
  name: string;
  frames: HandPose[];
  rules: GestureRule[];
  referenceVariants?: HandPose[];
  category?: string;
  orderIndex?: number;
  unitId?: string;
  language?: string;
}

export interface FeedbackResult {
  score: number;
  isCorrect: boolean;
  feedbackMessages: string[];
}

export type LessonStatus = 'LOCKED' | 'AVAILABLE' | 'COMPLETED' | 'COMING_SOON';

export interface CurriculumGesture {
  id: string;
  name: string;
  category: string;
  orderIndex: number;
  unitId: string;
  hasTemplate: boolean;
  demoVideoUrl: string | null;
  language: string;
  status: LessonStatus;
}

export interface CurriculumUnit {
  id: string;
  title: string;
  gestures: CurriculumGesture[];
  language: string;
}

/** Er is iets om te leren: een herkenningstemplate en/of een demovideo. */
export function hasContent(g: Pick<CurriculumGesture, 'hasTemplate' | 'demoVideoUrl'>): boolean {
  return g.hasTemplate || g.demoVideoUrl != null;
}

export function blendshapeScore(pose: HandPose, name: string): number {
  return pose.faceBlendshapes?.find((b) => b.name === name)?.score ?? 0;
}

export function isFinitePoint(p: LandmarkPoint): boolean {
  return Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z);
}
