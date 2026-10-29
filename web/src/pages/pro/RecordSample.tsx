// Herkenningsopname (2,5 seconden): de handpunten worden gemiddeld tot één referentie, en een paar
// beelden gaan mee voor de beoordelaar. Zoals ContributionViewModel in de app.
import { useEffect, useRef, useState } from 'preact/hooks';
import { randomId, submitGestureSample, uploadReviewFrame } from '../../lib/api';
import { auth, signLanguage } from '../../lib/auth';
import { learning } from '../../lib/learning';
import { navigate } from '../../lib/router';
import { useStore } from '../../lib/store';
import { findGesture } from '../../domain/curriculum';
import type { HandPose, Handedness, LandmarkPoint } from '../../domain/types';
import { CameraFailure, HandCamera, cameraErrorText } from '../../camera/handCamera';
import { sleep } from '../../camera/recorder';
import { Button, Icon, Notice } from '../../components/ui';

type Step = 'consent' | 'countdown' | 'recording' | 'review' | 'uploading' | 'success';
const RECORD_MS = 2500;
const FRAME_INTERVAL_MS = 200;
const MAX_FRAMES = 15;
const MIN_POSES = 10;

function averageLandmarks(poses: HandPose[]): LandmarkPoint[] {
  return Array.from({ length: 21 }, (_, i) => ({
    x: poses.reduce((s, p) => s + p.landmarks[i].x, 0) / poses.length,
    y: poses.reduce((s, p) => s + p.landmarks[i].y, 0) / poses.length,
    z: poses.reduce((s, p) => s + p.landmarks[i].z, 0) / poses.length,
  }));
}

function majorityHandedness(poses: HandPose[]): Handedness {
  const counts = new Map<Handedness, number>();
  for (const p of poses) if (p.handedness && p.handedness !== 'UNKNOWN') counts.set(p.handedness, (counts.get(p.handedness) ?? 0) + 1);
  let best: Handedness = 'UNKNOWN';
  let max = 0;
  for (const [h, n] of counts) if (n > max) { best = h; max = n; }
  return best;
}

export function RecordSamplePage({ gestureId }: { gestureId: string }) {
  const a = useStore(auth);
  const l = useStore(learning);
  const name = findGesture(l.units, gestureId)?.name ?? gestureId;
  const [step, setStep] = useState<Step>('consent');
  const [consent, setConsent] = useState(false);
  const [count, setCount] = useState(3);
  const [progress, setProgress] = useState(0);
  const [preview, setPreview] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const camRef = useRef<HandCamera | null>(null);
  const recording = useRef(false);
  const poses = useRef<HandPose[]>([]);
  const frames = useRef<Blob[]>([]);
  const lastFrameAt = useRef(0);
  const alive = useRef(true);

  useEffect(() => () => {
    alive.current = false;
    camRef.current?.stop();
  }, []);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  async function start() {
    if (!consent) return;
    setError(null);
    setStep('countdown');
    await sleep(0);
    if (!canvasRef.current) return;
    camRef.current?.stop();
    poses.current = [];
    frames.current = [];
    const cam = new HandCamera(canvasRef.current, {
      overlay: true,
      onPose: (p) => {
        if (recording.current && p) poses.current.push(p);
      },
      onFrame: (canvas) => {
        const now = performance.now();
        if (!recording.current || frames.current.length >= MAX_FRAMES || now - lastFrameAt.current < FRAME_INTERVAL_MS) return;
        lastFrameAt.current = now;
        const index = frames.current.length;
        frames.current.push(new Blob());
        canvas.toBlob((b) => { if (b) frames.current[index] = b; }, 'image/jpeg', 0.7);
      },
    });
    camRef.current = cam;
    try {
      await cam.start();
    } catch (e) {
      setError(cameraErrorText(e instanceof CameraFailure ? e.kind : 'other'));
      setStep('consent');
      return;
    }
    for (let n = 3; n >= 1; n--) {
      if (!alive.current) return;
      setCount(n);
      await sleep(1000);
    }
    setStep('recording');
    recording.current = true;
    lastFrameAt.current = 0;
    const started = performance.now();
    while (performance.now() - started < RECORD_MS) {
      setProgress((performance.now() - started) / RECORD_MS);
      await sleep(100);
    }
    recording.current = false;
    setProgress(1);
    cam.stop();
    await sleep(150); // laatste JPEG's afronden
    frames.current = frames.current.filter((b) => b.size > 0);
    if (poses.current.length < MIN_POSES) {
      setError('Geen hand gedetecteerd tijdens de opname. Probeer opnieuw met je hand goed in beeld.');
      setStep('consent');
      return;
    }
    const middle = frames.current[Math.floor(frames.current.length / 2)];
    setPreview(middle ? URL.createObjectURL(middle) : null);
    setStep('review');
  }

  async function send() {
    if (!a.userId) return;
    setStep('uploading');
    setError(null);
    const folder = `${gestureId}/${randomId(12)}`;
    try {
      for (let i = 0; i < frames.current.length; i++) {
        await uploadReviewFrame(`${folder}/frame_${String(i).padStart(2, '0')}.jpg`, frames.current[i]);
      }
      await submitGestureSample({
        gesture_id: gestureId,
        landmarks: averageLandmarks(poses.current),
        handedness: majorityHandedness(poses.current),
        video_path: folder,
        contributor_id: a.userId,
        sign_language: signLanguage(a),
      });
      setStep('success');
    } catch {
      setError('Versturen is mislukt. Controleer de verbinding en probeer opnieuw.');
      setStep('review');
    }
  }

  const showCamera = step === 'countdown' || step === 'recording';
  return (
    <div class="lesson">
      <header class="lesson-top">
        <button type="button" class="icon-btn" onClick={() => navigate('/bijdragen')} aria-label="Sluiten"><Icon name="close" /></button>
        <span class="label">Herkenningsopname: {name}</span>
        <span style={{ width: '44px' }} />
      </header>
      <main id="main" class="lesson-main lesson-center">
        {step === 'consent' ? (
          <div class="stack-lg" style={{ maxWidth: '560px', textAlign: 'left' }}>
            <h1 class="title">Herkenningsopname voor {name}</h1>
            <h2 class="heading">Toestemming</h2>
            <p>We nemen een korte reeks beelden van je hand op, samen met de bewegingsdata (handpunten) die de app gebruikt om gebaren te herkennen.</p>
            <p>De beelden zijn alleen zichtbaar voor beoordelaars, uitsluitend om te controleren of het gebaar correct is uitgevoerd. Ze worden niet gedeeld met andere gebruikers en niet gebruikt voor iets anders dan deze beoordeling.</p>
            <p>Na goedkeuring wordt alleen de bewegingsdata (geen beeld) toegevoegd aan de gedeelde referentiedatabase. Je kunt op elk moment vragen om je bijdrage te laten verwijderen.</p>
            <label class="check-row">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.currentTarget.checked)} />
              <span>Ik geef toestemming voor het opnemen en beoordelen van deze bijdrage.</span>
            </label>
            {error ? <Notice>{error}</Notice> : null}
            <Button size="large" disabled={!consent} onClick={() => void start()}>Start opname</Button>
          </div>
        ) : null}

        <div class="stack" style={{ alignItems: 'center', width: '100%', display: showCamera ? 'flex' : 'none' }}>
          <h1 class="title">{step === 'recording' ? 'Opnemen…' : 'Zet je hand klaar'}</h1>
          <p style={{ maxWidth: '520px' }}>
            {step === 'recording'
              ? 'Houd de handvorm stil tot de balk vol is. Niet meer bewegen of van vorm veranderen.'
              : 'Zet je hand nu al in de vorm van het gebaar en houd hem stil in beeld. De opname begint vanzelf.'}
          </p>
          <div class="camera-box">
            <canvas ref={canvasRef} role="img" aria-label="Camerabeeld, gespiegeld" />
            {step === 'countdown' ? <div class="camera-idle" style={{ background: 'rgba(27,21,51,0.35)' }}><span class="countdown" aria-live="assertive">{count}</span></div> : null}
          </div>
          {step === 'recording' ? (
            <div class="progress-bar" style={{ maxWidth: '360px' }} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress * 100)}>
              <span style={{ width: `${progress * 100}%` }} />
            </div>
          ) : null}
        </div>

        {step === 'review' || step === 'uploading' ? (
          <div class="stack" style={{ alignItems: 'center', maxWidth: '520px' }}>
            <h1 class="title">Klopt je opname?</h1>
            <p>Dit is een beeld uit je opname. Staat je hand volledig en scherp in beeld, en is de handvorm goed? Verstuur hem dan naar de beoordelaar.</p>
            {preview ? <img src={preview} alt="Beeld uit je opname" style={{ borderRadius: '20px', width: 'min(100%, 300px)' }} /> : null}
            {error ? <Notice>{error}</Notice> : null}
            <div class="row" style={{ justifyContent: 'center' }}>
              <Button busy={step === 'uploading'} onClick={() => void send()}>Versturen</Button>
              <Button variant="on-night" disabled={step === 'uploading'} onClick={() => void start()}>Opnieuw opnemen</Button>
            </div>
          </div>
        ) : null}

        {step === 'success' ? (
          <div class="stack-lg" style={{ alignItems: 'center' }}>
            <div class="success-mark"><Icon name="check" size={56} /></div>
            <h1 class="title">Bedankt voor je bijdrage!</h1>
            <p class="lead" style={{ maxWidth: '480px' }}>Een beoordelaar bekijkt je opname binnenkort. Zodra hij is goedgekeurd, helpt hij dit gebaar nauwkeuriger te herkennen.</p>
            <Button onClick={() => navigate('/bijdragen')}>Terug naar bijdragen</Button>
          </div>
        ) : null}
      </main>
    </div>
  );
}
