// Camera + MediaPipe-handherkenning in de browser, zo gelijk mogelijk aan AndroidHandTrackerBridge:
// - hetzelfde model (hand_landmarker.task), tot 2 handen, dezelfde drempels (0,5);
// - het beeld wordt gespiegeld (zoals de voorcamera op Android) en staand 3:4 bijgesneden, net als
//   het portretbeeld waarmee de templates zijn opgenomen. De herkenning vergelijkt x- en y-afstanden
//   zonder beeldverhouding te corrigeren, dus een breed webcambeeld zou handvormen vervormen.
// Alles draait lokaal in de browser: er gaat geen beeld naar een server.
import type { HandLandmarker, HandLandmarkerResult } from '@mediapipe/tasks-vision';
import type { HandPose, Handedness, LandmarkPoint } from '../domain/types';

export const FRAME_WIDTH = 480;
export const FRAME_HEIGHT = 640;

export type CameraError = 'unsupported' | 'denied' | 'notfound' | 'busy' | 'model' | 'other';

export class CameraFailure extends Error {
  constructor(public readonly kind: CameraError) {
    super(kind);
  }
}

type Delegate = 'GPU' | 'CPU';
const landmarkers: Partial<Record<Delegate, Promise<HandLandmarker>>> = {};

/** Software-WebGL (geen echte videokaart, zoals in sommige virtuele of oude Chromebooks) is voor
 * MediaPipe veel trager dan de CPU. Dan meteen de CPU gebruiken. */
function softwareWebGL(): boolean {
  try {
    const gl = document.createElement('canvas').getContext('webgl');
    if (!gl) return true;
    const ext = gl.getExtension('WEBGL_debug_renderer_info');
    const renderer = ext ? String(gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)) : '';
    return /swiftshader|llvmpipe|softpipe|software|basic render/i.test(renderer);
  } catch {
    return true;
  }
}

export function preferredDelegate(): Delegate {
  return softwareWebGL() ? 'CPU' : 'GPU';
}

/** Laadt het model één keer per bezoek (ongeveer 20 MB, daarna uit de cache). */
export function loadHandLandmarker(delegate: Delegate = preferredDelegate()): Promise<HandLandmarker> {
  let promise = landmarkers[delegate];
  if (!promise) {
    promise = (async () => {
      // Pas laden als er echt geoefend wordt: inloggen en het leerpad blijven zo licht.
      const { FilesetResolver, HandLandmarker } = await import('@mediapipe/tasks-vision');
      const fileset = await FilesetResolver.forVisionTasks('/mediapipe/wasm');
      const options = (d: Delegate) => ({
        baseOptions: { modelAssetPath: '/mediapipe/hand_landmarker.task', delegate: d },
        runningMode: 'VIDEO' as const,
        numHands: 2,
        minHandDetectionConfidence: 0.5,
        minHandPresenceConfidence: 0.5,
        minTrackingConfidence: 0.5,
      });
      try {
        return await HandLandmarker.createFromOptions(fileset, options(delegate));
      } catch (e) {
        if (delegate === 'CPU') throw e;
        return await HandLandmarker.createFromOptions(fileset, options('CPU'));
      }
    })();
    landmarkers[delegate] = promise;
    promise.catch(() => {
      delete landmarkers[delegate];
    });
  }
  return promise;
}

export function cameraSupported(): boolean {
  return !!navigator.mediaDevices?.getUserMedia && typeof WebAssembly === 'object';
}

export async function openCamera(withLargeFrames = true): Promise<MediaStream> {
  if (!cameraSupported()) throw new CameraFailure('unsupported');
  try {
    return await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: 'user', width: { ideal: withLargeFrames ? 1280 : 640 }, height: { ideal: withLargeFrames ? 720 : 480 } },
    });
  } catch (e) {
    const name = (e as DOMException)?.name;
    if (name === 'NotAllowedError' || name === 'SecurityError') throw new CameraFailure('denied');
    if (name === 'NotFoundError' || name === 'OverconstrainedError') throw new CameraFailure('notfound');
    if (name === 'NotReadableError' || name === 'AbortError') throw new CameraFailure('busy');
    throw new CameraFailure('other');
  }
}

export function stopStream(stream: MediaStream | null | undefined) {
  stream?.getTracks().forEach((t) => t.stop());
}

const CONNECTIONS: [number, number][] = [
  [0, 1], [1, 2], [2, 3], [3, 4], [0, 5], [5, 6], [6, 7], [7, 8], [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16], [13, 17], [17, 18], [18, 19], [19, 20], [0, 17],
];

function handednessAt(result: HandLandmarkerResult, index: number): Handedness {
  const lists = (result as unknown as { handedness?: { categoryName: string }[][]; handednesses?: { categoryName: string }[][] });
  const label = (lists.handedness ?? lists.handednesses)?.[index]?.[0]?.categoryName?.toLowerCase();
  return label === 'left' ? 'LEFT' : label === 'right' ? 'RIGHT' : 'UNKNOWN';
}

export interface HandCameraOptions {
  /** Toon de herkende punten over het beeld (feedback voor de leerling). */
  overlay: boolean;
  /** Elk herkend frame: een pose, of null als er geen hand in beeld is. */
  onPose: (pose: HandPose | null) => void;
  /** Na het tekenen van het (gespiegelde, bijgesneden) frame, vóór de overlay. */
  onFrame?: (canvas: HTMLCanvasElement) => void;
}

/** Tekent het camerabeeld in een canvas en laat MediaPipe op precies dat beeld de handen zoeken. */
export class HandCamera {
  private stream: MediaStream | null = null;
  private video: HTMLVideoElement | null = null;
  private landmarker: HandLandmarker | null = null;
  private raf = 0;
  private lastTs = 0;
  private running = false;
  private stopped = false;
  private timings: number[] = [];
  private switching = false;

  constructor(private readonly canvas: HTMLCanvasElement, private readonly options: HandCameraOptions) {
    canvas.width = FRAME_WIDTH;
    canvas.height = FRAME_HEIGHT;
  }

  async start(): Promise<void> {
    const [cam, model] = await Promise.allSettled([openCamera(), loadHandLandmarker()]);
    if (cam.status === 'rejected') throw cam.reason instanceof CameraFailure ? cam.reason : new CameraFailure('other');
    if (model.status === 'rejected') {
      stopStream(cam.value);
      throw new CameraFailure('model');
    }
    const stream = cam.value;
    if (this.stopped) {
      stopStream(stream);
      return;
    }
    this.stream = stream;
    this.landmarker = model.value;
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.srcObject = stream;
    this.video = video;
    await video.play().catch(() => undefined);
    if (video.readyState < 2) await new Promise((r) => video.addEventListener('loadeddata', r, { once: true }));
    if (this.stopped) return;
    this.running = true;
    this.loop();
  }

  private loop = () => {
    if (!this.running) return;
    this.raf = requestAnimationFrame(this.loop);
    const video = this.video;
    const ctx = this.canvas.getContext('2d');
    if (!video || !ctx || video.videoWidth === 0) return;

    const vw = video.videoWidth;
    const vh = video.videoHeight;
    const target = FRAME_WIDTH / FRAME_HEIGHT;
    let sw = vw;
    let sh = vh;
    if (vw / vh > target) sw = vh * target;
    else sh = vw / target;
    ctx.save();
    ctx.translate(FRAME_WIDTH, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, (vw - sw) / 2, (vh - sh) / 2, sw, sh, 0, 0, FRAME_WIDTH, FRAME_HEIGHT);
    ctx.restore();
    this.options.onFrame?.(this.canvas);

    if (!this.landmarker) return;
    let now = performance.now();
    if (now <= this.lastTs) now = this.lastTs + 1;
    this.lastTs = now;
    let result: HandLandmarkerResult;
    try {
      const t0 = performance.now();
      result = this.landmarker.detectForVideo(this.canvas, now);
      this.checkSpeed(performance.now() - t0);
    } catch {
      return;
    }
    const hands = result.landmarks ?? [];
    const valid = (list: { x: number; y: number; z: number }[] | undefined): LandmarkPoint[] | null =>
      list && list.length === 21 && list.every((p) => Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z))
        ? list.map((p) => ({ x: p.x, y: p.y, z: p.z }))
        : null;
    const primary = valid(hands[0]);
    if (!primary) {
      this.options.onPose(null);
      return;
    }
    const secondary = valid(hands[1]);
    const pose: HandPose = {
      landmarks: primary,
      timestampMs: Math.round(now),
      handedness: handednessAt(result, 0),
      secondaryHandLandmarks: secondary,
      secondaryHandedness: secondary ? handednessAt(result, 1) : 'UNKNOWN',
      faceBlendshapes: [],
    };
    if (this.options.overlay) this.drawOverlay(ctx, secondary ? [primary, secondary] : [primary]);
    this.options.onPose(pose);
  };

  /** Blijkt de videokaart toch traag (meer dan 150 ms per beeld), dan overstappen op de CPU. */
  private checkSpeed(ms: number) {
    if (this.switching || this.timings.length >= 12) return;
    this.timings.push(ms);
    if (this.timings.length < 12) return;
    const sorted = this.timings.slice(2).sort((a, b) => a - b);
    if (sorted[Math.floor(sorted.length / 2)] <= 150) return;
    this.switching = true;
    loadHandLandmarker('CPU')
      .then((cpu) => {
        if (!this.stopped) this.landmarker = cpu;
      })
      .catch(() => undefined);
  }

  private drawOverlay(ctx: CanvasRenderingContext2D, hands: LandmarkPoint[][]) {
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    for (const hand of hands) {
      ctx.strokeStyle = 'rgba(255, 248, 240, 0.75)';
      ctx.beginPath();
      for (const [a, b] of CONNECTIONS) {
        ctx.moveTo(hand[a].x * FRAME_WIDTH, hand[a].y * FRAME_HEIGHT);
        ctx.lineTo(hand[b].x * FRAME_WIDTH, hand[b].y * FRAME_HEIGHT);
      }
      ctx.stroke();
      ctx.fillStyle = '#FFB627';
      for (const p of hand) {
        ctx.beginPath();
        ctx.arc(p.x * FRAME_WIDTH, p.y * FRAME_HEIGHT, 5, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }

  stop() {
    this.stopped = true;
    this.running = false;
    cancelAnimationFrame(this.raf);
    stopStream(this.stream);
    this.stream = null;
    if (this.video) this.video.srcObject = null;
    this.video = null;
  }
}

export function cameraErrorText(kind: CameraError): string {
  switch (kind) {
    case 'denied':
      return 'De browser mag de camera niet gebruiken. Klik op het camera-icoon in de adresbalk en kies Toestaan.';
    case 'notfound':
      return 'Er is geen camera gevonden op dit apparaat.';
    case 'busy':
      return 'De camera wordt al door een ander programma gebruikt. Sluit dat en probeer opnieuw.';
    case 'unsupported':
      return 'Deze browser kan de camera niet gebruiken. Probeer Chrome, Edge of Safari.';
    case 'model':
      return 'De handherkenning kon niet laden. Controleer de verbinding en probeer opnieuw.';
    default:
      return 'De camera kon niet starten. Probeer het opnieuw.';
  }
}
