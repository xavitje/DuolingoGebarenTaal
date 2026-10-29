// Port van LandmarkNormalizer.kt: maakt een handvorm onafhankelijk van positie, grootte en kanteling.
// - Translatie: pols (landmark 0) wordt de oorsprong.
// - Schaal: gedeeld door de afstand pols -> middelvinger-MCP (landmark 9).
// - Rotatie: de as pols -> MCP wijst altijd recht omhoog.
import { type HandPose, type LandmarkPoint, isFinitePoint } from './types';

const LANDMARK_COUNT = 21;
const WRIST = 0;
const MIDDLE_FINGER_MCP = 9;
const MIN_SCALE = 0.0001;
const CANONICAL_ANGLE = -Math.PI / 2;

export interface CanonicalPose {
  landmarks: LandmarkPoint[];
  timestampMs: number;
}

export function normalizePose(pose: HandPose, mirrored = false): CanonicalPose | null {
  if (pose.landmarks.length !== LANDMARK_COUNT || pose.timestampMs < 0) return null;
  if (!pose.landmarks.every(isFinitePoint)) return null;

  const oriented = mirrored ? pose.landmarks.map((p) => ({ ...p, x: 1 - p.x })) : pose.landmarks;
  const wrist = oriented[WRIST];
  const middleMcp = oriented[MIDDLE_FINGER_MCP];
  const dx = middleMcp.x - wrist.x;
  const dy = middleMcp.y - wrist.y;
  const scale = Math.sqrt(dx * dx + dy * dy);
  if (!Number.isFinite(scale) || scale < MIN_SCALE) return null;

  const rotation = CANONICAL_ANGLE - Math.atan2(dy, dx);
  const cosR = Math.cos(rotation);
  const sinR = Math.sin(rotation);

  return {
    landmarks: oriented.map((point) => {
      const tx = (point.x - wrist.x) / scale;
      const ty = (point.y - wrist.y) / scale;
      return {
        x: tx * cosR - ty * sinR,
        y: tx * sinR + ty * cosR,
        z: (point.z - wrist.z) / scale,
      };
    }),
    timestampMs: pose.timestampMs,
  };
}
