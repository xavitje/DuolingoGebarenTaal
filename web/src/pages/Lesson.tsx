// Eén les, zoals LessonOverlay in de app: eerst kijken (demovideo), dan nadoen met de camera, of
// met de zelfcheck als er geen herkenning is, de docent de camera uitzette, of de camera niet werkt.
import { useEffect, useRef, useState } from 'preact/hooks';
import { auth } from '../lib/auth';
import { completeLesson, learning, logGaveUp, nextAfter } from '../lib/learning';
import { navigate } from '../lib/router';
import { useStore } from '../lib/store';
import { findGesture, lessonLabel } from '../domain/curriculum';
import { REPS_REQUIRED, repStep } from '../domain/repCounter';
import { scorerFor } from '../domain/scoring';
import type { CurriculumGesture, FeedbackResult, HandPose, SignTemplate } from '../domain/types';
import { CameraFailure, HandCamera, cameraErrorText, cameraSupported, type CameraError } from '../camera/handCamera';
import { Button, Icon, Notice, RepDots, ScoreRing, Spinner, SuccessMark } from '../components/ui';

type Phase = 'demo' | 'camera' | 'self' | 'done';

const INSTRUCTIONS: Record<string, string> = {
  letter_a: 'Maak een vuist en leg je duim langs de zijkant van je vingers.',
  letter_b: 'Strek vier vingers omhoog en buig je duim over je handpalm.',
  letter_l: 'Strek je wijsvinger omhoog en je duim opzij tot een L.',
  swipe_right: 'Houd de handvorm vast en beweeg duidelijk van links naar rechts.',
};

export function LessonPage({ gestureId }: { gestureId: string }) {
  const l = useStore(learning);
  const a = useStore(auth);
  const [phase, setPhase] = useState<Phase>('demo');
  const [xp, setXp] = useState<number | null>(null);
  const [cameraBroken, setCameraBroken] = useState(false);

  useEffect(() => {
    setPhase('demo');
    setXp(null);
    setCameraBroken(false);
  }, [gestureId]);

  if (l.loading) {
    return <div class="lesson"><Spinner label="Les laden…" /></div>;
  }
  const gesture = findGesture(l.units, gestureId);
  if (!gesture || (gesture.status !== 'AVAILABLE' && gesture.status !== 'COMPLETED')) {
    return <Gone />;
  }
  const template = l.templates.find((t) => t.id === gesture.id);
  const cameraAllowed = a.school?.camera_allowed ?? true;
  const useCamera = !!template && cameraAllowed && cameraSupported() && !cameraBroken;
  const close = () => navigate('/leren');

  async function finish(score: number, mode: 'camera' | 'self') {
    setPhase('done');
    setXp(await completeLesson(gesture!.id, score, mode));
  }

  return (
    <div class="lesson">
      <header class="lesson-top">
        <button type="button" class="icon-btn" onClick={close} aria-label="Terug naar leerpad">
          <Icon name="close" />
        </button>
        <span class="label">{lessonLabel(gesture)}</span>
        <span style={{ width: '44px' }} />
      </header>
      <main id="main" class="lesson-main" tabIndex={-1}>
        {phase === 'demo' ? <Demo gesture={gesture} onStart={() => setPhase(useCamera ? 'camera' : 'self')} /> : null}
        {phase === 'camera' && template ? (
          <Coach
            template={template}
            gesture={gesture}
            onComplete={(score) => void finish(score, 'camera')}
            onUseSelfCheck={() => { setCameraBroken(true); setPhase('self'); }}
          />
        ) : null}
        {phase === 'self' ? (
          <SelfCheck gesture={gesture} onDone={() => void finish(1, 'self')} onWatchAgain={() => setPhase('demo')} />
        ) : null}
        {phase === 'done' ? <Done gestureId={gesture.id} xp={xp} onClose={close} /> : null}
      </main>
    </div>
  );
}

function Gone() {
  useEffect(() => navigate('/leren', true), []);
  return null;
}

function Demo({ gesture, onStart }: { gesture: CurriculumGesture; onStart: () => void }) {
  const [failed, setFailed] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => heading.current?.focus(), []);
  return (
    <div class="lesson-main lesson-center" style={{ padding: 0 }}>
      <p class="caption" style={{ fontWeight: 700 }}>Bekijk eerst hoe het moet</p>
      <h1 class="big-name" tabIndex={-1} ref={heading}>{gesture.name}</h1>
      <div class="demo-frame">
        {gesture.demoVideoUrl && !failed ? (
          <video src={gesture.demoVideoUrl} autoplay muted loop playsInline controls onError={() => setFailed(true)}
            aria-label={`Voorbeeldvideo van ${lessonLabel(gesture)}`} />
        ) : (
          <div class="demo-empty">
            <span class="circle"><Icon name="play" size={30} /></span>
            <p>{failed ? 'Video kon niet worden geladen. Je kunt alsnog gewoon oefenen.' : 'Video volgt binnenkort voor dit gebaar. Je kunt alvast oefenen.'}</p>
          </div>
        )}
      </div>
      <div class="lesson-actions">
        <Button size="large" block onClick={onStart}>Doe het na!</Button>
      </div>
    </div>
  );
}

function Coach({ template, gesture, onComplete, onUseSelfCheck }: {
  template: SignTemplate; gesture: CurriculumGesture; onComplete: (score: number) => void; onUseSelfCheck: () => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const cameraRef = useRef<HandCamera | null>(null);
  const poses = useRef<HandPose[]>([]);
  const highSince = useRef<number | null>(null);
  const reps = useRef(0);
  const waiting = useRef(false);
  const attemptStarted = useRef(false);
  const completed = useRef(false);
  const lastPoseAt = useRef(0);
  const scorer = useRef(scorerFor(template));

  const [status, setStatus] = useState<'starting' | 'running' | 'stopped' | 'error'>('starting');
  const [error, setError] = useState<CameraError | null>(null);
  const [feedback, setFeedback] = useState<FeedbackResult | null>(null);
  const [repsDone, setRepsDone] = useState(0);
  const [waitingForRelease, setWaitingForRelease] = useState(false);
  const [handVisible, setHandVisible] = useState(true);

  function onPose(pose: HandPose | null) {
    const now = performance.now();
    if (!pose) {
      if (now - lastPoseAt.current > 1200) setHandVisible(false);
      return;
    }
    lastPoseAt.current = now;
    setHandVisible(true);
    attemptStarted.current = true;
    poses.current = [...poses.current, pose].slice(-60);
    const result = scorer.current.score(poses.current, template);
    const step = repStep({
      score: result.score, running: !completed.current, timestampMs: pose.timestampMs, highScoreSinceMs: highSince.current,
      waitingForRelease: waiting.current, repsDone: reps.current, repsRequired: REPS_REQUIRED,
    });
    highSince.current = step.highScoreSinceMs;
    reps.current = step.repsDone;
    waiting.current = step.waitingForRelease;
    setFeedback(result);
    setRepsDone(step.repsDone);
    setWaitingForRelease(step.waitingForRelease);
    if (step.completed && !completed.current) {
      completed.current = true;
      cameraRef.current?.stop();
      onComplete(result.score);
    }
  }

  async function start() {
    if (!canvasRef.current) return;
    cameraRef.current?.stop();
    poses.current = [];
    highSince.current = null;
    reps.current = 0;
    waiting.current = false;
    setRepsDone(0);
    setWaitingForRelease(false);
    setFeedback(null);
    setError(null);
    setStatus('starting');
    const cam = new HandCamera(canvasRef.current, { overlay: true, onPose });
    cameraRef.current = cam;
    try {
      await cam.start();
      if (cameraRef.current === cam) setStatus('running');
    } catch (e) {
      if (cameraRef.current !== cam) return;
      setError(e instanceof CameraFailure ? e.kind : 'other');
      setStatus('error');
    }
  }

  function stop() {
    cameraRef.current?.stop();
    setStatus('stopped');
  }

  useEffect(() => {
    void start();
    return () => {
      cameraRef.current?.stop();
      cameraRef.current = null;
      if (attemptStarted.current && !completed.current) logGaveUp(gesture.id);
    };
  }, []);

  const statusText = status === 'starting' ? 'Camera en herkenning starten…'
    : status === 'running' ? (handVisible ? 'Houd je hand in beeld' : 'Geen hand in beeld')
      : status === 'stopped' ? 'Klaar om te oefenen' : 'Camera werkt niet';

  return (
    <div class="coach">
      <div class="camera-box">
        <canvas ref={canvasRef} aria-label="Camerabeeld, gespiegeld. Er wordt niets opgenomen of verstuurd." role="img" />
        {status !== 'running' ? (
          <div class="camera-idle">
            {status === 'starting' ? <span class="spinner" aria-hidden="true" /> : <Icon name="camera" size={40} />}
          </div>
        ) : null}
        <div class="camera-status" aria-live="polite">
          {status === 'running' ? <span class="live" aria-hidden="true" /> : null}
          {statusText}
        </div>
      </div>

      <div class="coach-side">
        <div class="stack-sm coach-title">
          <p class="caption" style={{ fontWeight: 700 }}>Doe het na</p>
          <h1 class="big-name" style={{ fontSize: '56px' }}>{gesture.name}</h1>
        </div>
        <p class="instruction">{INSTRUCTIONS[template.id] ?? 'Volg het voorbeeld en houd je hand volledig in beeld.'}</p>
        <RepDots done={repsDone} required={REPS_REQUIRED} />
        {waitingForRelease ? <p class="release" aria-live="polite">Goed zo! Laat je hand even zakken en doe het nog een keer.</p> : null}

        {feedback && status === 'running' ? (
          <div class="feedback">
            <ScoreRing score={feedback.score} />
            <div>
              <strong>Score: {Math.round(feedback.score * 100)}%</strong>
              <span>{feedback.feedbackMessages.join(' ')}</span>
            </div>
          </div>
        ) : null}

        {error ? (
          <div class="stack">
            <Notice>{cameraErrorText(error)}</Notice>
            <div class="row">
              <Button onClick={() => void start()}>Opnieuw proberen</Button>
              <Button variant="on-night" onClick={onUseSelfCheck}>Oefen zonder camera</Button>
            </div>
          </div>
        ) : (
          <div class="row">
            {status === 'stopped' ? <Button onClick={() => void start()}>Start de camera</Button> : null}
            {status === 'running' || status === 'starting' ? <Button variant="on-night" onClick={stop}>Stop</Button> : null}
          </div>
        )}
        <p class="caption">Het camerabeeld blijft op dit apparaat. Er wordt niets opgenomen of verstuurd.</p>
      </div>
    </div>
  );
}

function SelfCheck({ gesture, onDone, onWatchAgain }: { gesture: CurriculumGesture; onDone: () => void; onWatchAgain: () => void }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => heading.current?.focus(), []);
  return (
    <div class="lesson-main lesson-center" style={{ padding: 0 }}>
      <p class="caption" style={{ fontWeight: 700 }}>Nu jij!</p>
      <h1 class="big-name" tabIndex={-1} ref={heading}>{gesture.name}</h1>
      <p class="lead" style={{ maxWidth: '420px' }}>Doe het gebaar een paar keer na, net als in de video. Lukt het?</p>
      <div class="lesson-actions">
        <Button size="large" block onClick={onDone}>Gelukt!</Button>
        <Button size="large" variant="on-night" block onClick={onWatchAgain}>Nog een keer kijken</Button>
      </div>
    </div>
  );
}

function Done({ gestureId, xp, onClose }: { gestureId: string; xp: number | null; onClose: () => void }) {
  const heading = useRef<HTMLHeadingElement>(null);
  const next = nextAfter(gestureId);
  useEffect(() => heading.current?.focus(), []);
  return (
    <div class="lesson-main lesson-center" style={{ padding: 0 }}>
      <SuccessMark />
      <h1 class="title" tabIndex={-1} ref={heading}>Je hebt het gehaald!</h1>
      <p class="lead">Goed gedaan! Je kent dit gebaar nu.</p>
      <div aria-live="polite" style={{ minHeight: '48px' }}>
        {xp != null ? <span class="xp-pill">+{xp} XP verdiend!</span> : null}
      </div>
      <div class="lesson-actions">
        {next ? <Button size="large" block onClick={() => navigate(`/les/${encodeURIComponent(next)}`)}>Volgend gebaar</Button> : null}
        <Button size="large" variant={next ? 'on-night' : 'primary'} block onClick={onClose}>Terug naar leerpad</Button>
      </div>
    </div>
  );
}
