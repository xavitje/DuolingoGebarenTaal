// Demovideo opnemen met MediaRecorder. Liefst MP4 (speelt overal, ook op Android en iPad); browsers
// die dat niet kunnen (Firefox) nemen WebM op.
const TYPES = ['video/mp4;codecs=avc1', 'video/mp4', 'video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'];

export function recordingSupported(): boolean {
  return typeof MediaRecorder !== 'undefined' && TYPES.some((t) => MediaRecorder.isTypeSupported(t));
}

export function pickMimeType(): string {
  return TYPES.find((t) => MediaRecorder.isTypeSupported(t)) ?? '';
}

export function extensionFor(mime: string): 'mp4' | 'webm' {
  return mime.startsWith('video/webm') ? 'webm' : 'mp4';
}

/** Neemt `durationMs` op van de stream en meldt de voortgang (0..1). */
export function recordClip(stream: MediaStream, durationMs: number, onProgress: (p: number) => void): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const mimeType = pickMimeType();
    let recorder: MediaRecorder;
    try {
      recorder = new MediaRecorder(stream, { mimeType, videoBitsPerSecond: 2_500_000 });
    } catch (e) {
      reject(e);
      return;
    }
    const chunks: Blob[] = [];
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    recorder.onerror = () => reject(new Error('recording'));
    recorder.onstop = () => {
      const type = (recorder.mimeType || mimeType).split(';')[0] || 'video/mp4';
      const blob = new Blob(chunks, { type });
      if (blob.size === 0) reject(new Error('empty'));
      else resolve(blob);
    };
    const started = performance.now();
    const tick = window.setInterval(() => onProgress(Math.min(1, (performance.now() - started) / durationMs)), 100);
    recorder.start(250);
    window.setTimeout(() => {
      window.clearInterval(tick);
      onProgress(1);
      if (recorder.state !== 'inactive') recorder.stop();
    }, durationMs);
  });
}

export const sleep = (ms: number) => new Promise((r) => window.setTimeout(r, ms));
