// Demovideo opnemen (5 seconden) en ter beoordeling insturen, zoals DemoVideoViewModel in de app.
import { useEffect, useRef, useState } from 'preact/hooks';
import { randomId, submitDemoVideo } from '../../lib/api';
import { auth, signLanguage } from '../../lib/auth';
import { learning } from '../../lib/learning';
import { navigate } from '../../lib/router';
import { useStore } from '../../lib/store';
import { findGesture } from '../../domain/curriculum';
import { CameraFailure, cameraErrorText, openCamera, stopStream } from '../../camera/handCamera';
import { extensionFor, recordClip, recordingSupported, sleep } from '../../camera/recorder';
import { Button, Icon, Notice } from '../../components/ui';

type Step = 'consent' | 'countdown' | 'recording' | 'preview' | 'uploading' | 'sent';
const DURATION_MS = 5000;

export function RecordDemoPage({ gestureId }: { gestureId: string }) {
  const a = useStore(auth);
  const l = useStore(learning);
  const name = findGesture(l.units, gestureId)?.name ?? gestureId;
  const [step, setStep] = useState<Step>('consent');
  const [consent, setConsent] = useState(false);
  const [count, setCount] = useState(3);
  const [progress, setProgress] = useState(0);
  const [clip, setClip] = useState<Blob | null>(null);
  const [clipUrl, setClipUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const liveRef = useRef<HTMLVideoElement>(null);
  const alive = useRef(true);

  useEffect(() => () => {
    alive.current = false;
    stopStream(streamRef.current);
  }, []);
  useEffect(() => () => { if (clipUrl) URL.revokeObjectURL(clipUrl); }, [clipUrl]);

  async function record() {
    if (!consent) return;
    setError(null);
    setClip(null);
    try {
      if (!streamRef.current) streamRef.current = await openCamera();
    } catch (e) {
      setError(cameraErrorText(e instanceof CameraFailure ? e.kind : 'other'));
      return;
    }
    setStep('countdown');
    await sleep(0);
    if (liveRef.current) {
      liveRef.current.srcObject = streamRef.current;
      void liveRef.current.play().catch(() => undefined);
    }
    for (let n = 3; n >= 1; n--) {
      if (!alive.current) return;
      setCount(n);
      await sleep(1000);
    }
    setStep('recording');
    setProgress(0);
    try {
      const blob = await recordClip(streamRef.current!, DURATION_MS, setProgress);
      if (!alive.current) return;
      stopStream(streamRef.current);
      streamRef.current = null;
      setClip(blob);
      setClipUrl(URL.createObjectURL(blob));
      setStep('preview');
    } catch {
      setError('De opname is mislukt. Probeer het nog eens.');
      setStep('consent');
    }
  }

  async function send() {
    if (!clip || !a.userId) return;
    setStep('uploading');
    setError(null);
    const lang = signLanguage(a);
    const path = `${lang}/${gestureId}/${randomId()}.${extensionFor(clip.type)}`;
    try {
      await submitDemoVideo(a.userId, gestureId, lang, path, clip);
      setStep('sent');
    } catch {
      setError('Versturen is mislukt. Controleer de verbinding en probeer opnieuw.');
      setStep('preview');
    }
  }

  return (
    <div class="lesson">
      <header class="lesson-top">
        <button type="button" class="icon-btn" onClick={() => navigate('/bijdragen')} aria-label="Sluiten"><Icon name="close" /></button>
        <span class="label">Demovideo: {name}</span>
        <span style={{ width: '44px' }} />
      </header>
      <main id="main" class="lesson-main lesson-center">
        {step === 'consent' ? (
          <div class="stack-lg" style={{ maxWidth: '560px', textAlign: 'left' }}>
            <h1 class="title">Demovideo voor {name}</h1>
            {!recordingSupported() ? <Notice>Deze browser kan geen video opnemen. Gebruik Chrome, Edge of Safari.</Notice> : null}
            <p>Je neemt een video van 5 seconden op waarin je dit gebaar voordoet. Na goedkeuring door een collega-vakman zien alle leerlingen deze video als voorbeeld.</p>
            <div class="card-flat stack-sm" style={{ background: 'var(--night)' }}>
              <h2 class="heading">Zo wordt het een goede demovideo</h2>
              <ul style={{ margin: 0, paddingLeft: '1.2em' }}>
                <li>Ga met je gezicht en bovenlichaam in beeld zitten, rustige achtergrond.</li>
                <li>Licht van voren, niet met het raam achter je.</li>
                <li>Doe het gebaar rustig en duidelijk, en houd het even vast.</li>
                <li>Zet het apparaat stevig neer, niet in de hand.</li>
              </ul>
            </div>
            <label class="check-row">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.currentTarget.checked)} />
              <span>Ik geef toestemming dat deze video, met mij in beeld, aan leerlingen wordt getoond.</span>
            </label>
            {error ? <Notice>{error}</Notice> : null}
            <Button size="large" disabled={!consent || !recordingSupported()} onClick={() => void record()}>Start opname</Button>
          </div>
        ) : null}

        {step === 'countdown' || step === 'recording' ? (
          <div class="stack" style={{ alignItems: 'center', width: '100%' }}>
            <div class="demo-frame" style={{ position: 'relative', aspectRatio: '4 / 3', width: 'min(100%, 640px)' }}>
              <video ref={liveRef} muted playsInline style={{ transform: 'scaleX(-1)' }} aria-label="Live camerabeeld" />
              {step === 'countdown' ? (
                <div class="camera-idle" style={{ background: 'rgba(27,21,51,0.45)' }}><span class="countdown" aria-live="assertive">{count}</span></div>
              ) : null}
            </div>
            {step === 'recording' ? (
              <div class="stack-sm" style={{ width: 'min(100%, 640px)' }}>
                <p aria-live="polite"><strong>Opnemen… doe het gebaar nu voor.</strong></p>
                <div class="progress-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress * 100)}>
                  <span style={{ width: `${progress * 100}%` }} />
                </div>
              </div>
            ) : <p>Zet je klaar. De opname begint zo.</p>}
          </div>
        ) : null}

        {(step === 'preview' || step === 'uploading') && clipUrl ? (
          <div class="stack" style={{ alignItems: 'center', width: '100%' }}>
            <h1 class="title">Kijk je opname terug</h1>
            <p>Duidelijk en goed in beeld? Verstuur hem dan ter beoordeling.</p>
            <div class="demo-frame" style={{ aspectRatio: '4 / 3', width: 'min(100%, 640px)' }}>
              <video src={clipUrl} controls playsInline loop autoplay muted aria-label="Je opname" />
            </div>
            {error ? <Notice>{error}</Notice> : null}
            <div class="row" style={{ justifyContent: 'center' }}>
              <Button busy={step === 'uploading'} onClick={() => void send()}>Versturen</Button>
              <Button variant="on-night" disabled={step === 'uploading'} onClick={() => void record()}>Opnieuw opnemen</Button>
            </div>
          </div>
        ) : null}

        {step === 'sent' ? (
          <div class="stack-lg" style={{ alignItems: 'center' }}>
            <div class="success-mark"><Icon name="check" size={56} /></div>
            <h1 class="title">Verstuurd!</h1>
            <p class="lead" style={{ maxWidth: '480px' }}>Een collega-vakman bekijkt je video. Na goedkeuring zien leerlingen hem bij dit gebaar.</p>
            <Button onClick={() => navigate('/bijdragen')}>Terug naar bijdragen</Button>
          </div>
        ) : null}
      </main>
    </div>
  );
}
