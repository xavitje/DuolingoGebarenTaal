// Klas van een docent: klascode, leerlingen met pincode en voortgang, leerlingen toevoegen,
// pincode vernieuwen, camera per leerling, verwijderen en inlogkaartjes. Zoals TeacherScreen in de app.
import { useEffect, useState } from 'preact/hooks';
import {
  type SchoolClass, type StudentProgress, classOverview, createStudents, myClasses, removeStudent, resetPin, setStudentCamera,
} from '../../lib/api';
import { navigate } from '../../lib/router';
import { ApiError } from '../../lib/supabase';
import { Button, Dialog, Field, Icon, Link, Notice, Spinner, formatDate } from '../../components/ui';

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.isNetwork) return 'Geen verbinding. Probeer het nog eens.';
    if (e.status === 409 && e.code === 'no_seats') {
      const left = Number(e.body.seats_left);
      return Number.isFinite(left) ? `Niet genoeg plekken in de licentie. Nog ${left} over.` : 'Niet genoeg plekken in de licentie.';
    }
    if (e.status === 409) return 'De licentie van je school is verlopen.';
  }
  return 'Er ging iets mis. Probeer het nog eens.';
}

export function ClassPage({ classId }: { classId: string }) {
  const [cls, setCls] = useState<SchoolClass | null>(null);
  const [students, setStudents] = useState<StudentProgress[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState(false);
  const [names, setNames] = useState('');
  const [newPins, setNewPins] = useState<{ user_id: string; first_name: string; pin: string }[] | null>(null);
  const [removing, setRemoving] = useState<StudentProgress | null>(null);

  async function refresh() {
    try {
      setStudents(await classOverview(classId));
    } catch (e) {
      setError(errorText(e));
      setStudents((s) => s ?? []);
    }
  }

  useEffect(() => {
    myClasses()
      .then((list) => {
        const found = list.find((c) => c.id === classId);
        if (!found) navigate('/klas', true);
        else setCls(found);
      })
      .catch((e) => setError(errorText(e)));
    void refresh();
  }, [classId]);

  async function add(e: Event) {
    e.preventDefault();
    const list = [...new Set(names.split(/[\n,]/).map((n) => n.trim()).filter((n) => n.length > 0))];
    if (list.length === 0) return;
    if (list.some((n) => n.length > 40)) return setError('Een voornaam mag maximaal 40 tekens hebben.');
    if (list.length > 60) return setError('Voeg maximaal 60 leerlingen tegelijk toe.');
    setBusy(true);
    setError(null);
    try {
      const created = await createStudents(classId, list);
      setAdding(false);
      setNames('');
      setNewPins(created);
      await refresh();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  async function newPin(s: StudentProgress) {
    setBusy(true);
    setError(null);
    try {
      const pin = await resetPin(classId, s.user_id);
      setStudents((list) => list?.map((x) => (x.user_id === s.user_id ? { ...x, pin } : x)) ?? null);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function confirmRemove() {
    if (!removing) return;
    setBusy(true);
    setError(null);
    try {
      await removeStudent(classId, removing.user_id);
      setRemoving(null);
      await refresh();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function toggleCamera(s: StudentProgress, allowed: boolean) {
    setStudents((list) => list?.map((x) => (x.user_id === s.user_id ? { ...x, camera_allowed: allowed } : x)) ?? null);
    try {
      await setStudentCamera(classId, s.user_id, allowed);
    } catch (e) {
      setError(errorText(e));
      void refresh();
    }
  }

  return (
    <div class="page stack-lg">
      <Link href="/klas">← Alle klassen</Link>
      {!cls ? <Spinner /> : (
        <>
          <div class="stack-sm">
            <h1 class="display">{cls.name}</h1>
            <div class="row">
              <span class="muted">Klascode</span>
              <span class="code-big" aria-label={`Klascode ${cls.code.split('').join(' ')}`}>{cls.code}</span>
            </div>
            <p class="muted">Leerlingen loggen in met deze code, kiezen hun naam en typen hun pincode.</p>
          </div>
          <div class="row">
            <Button onClick={() => { setError(null); setAdding(true); }}><Icon name="plus" size={20} /> Leerlingen toevoegen</Button>
            <Button variant="secondary" disabled={!students || students.length === 0} onClick={() => navigate(`/klas/${classId}/kaartjes`)}>
              <Icon name="print" size={20} /> Inlogkaartjes printen
            </Button>
            <Button variant="ghost" onClick={() => void refresh()} aria-label="Vernieuwen"><Icon name="refresh" size={20} /> Vernieuwen</Button>
          </div>
        </>
      )}

      {error ? <Notice>{error}</Notice> : null}

      <section class="stack" aria-labelledby="lln-kop">
        <h2 id="lln-kop" class="title">{students ? `${students.length} leerlingen` : 'Leerlingen'}</h2>
        {students == null ? <Spinner /> : students.length === 0 ? <p class="muted">Nog geen leerlingen in deze klas.</p> : (
          <ul class="list">
            {students.map((s) => (
              <li key={s.user_id} class="card student">
                <div class="stack-sm" style={{ gap: '4px' }}>
                  <strong class="heading">{s.first_name}</strong>
                  <span>
                    {s.pin ? <>Pincode <span class="mono">{s.pin}</span></> : <span class="muted">Pincode: kies Nieuwe pincode</span>}
                  </span>
                  <span class="muted">{s.completed_lessons} lessen · {s.total_xp} XP · {s.current_streak} dagen op rij</span>
                  <span class="caption">{s.last_practice_date ? `Laatst geoefend: ${formatDate(s.last_practice_date)}` : 'Nog niet geoefend'}</span>
                  <label class="switch">
                    <input type="checkbox" role="switch" checked={s.camera_allowed} onChange={(e) => void toggleCamera(s, e.currentTarget.checked)} />
                    <span>
                      Oefenen met camera: {s.camera_allowed ? 'aan, de app kijkt mee en geeft feedback' : 'uit, de leerling kijkt de voorbeelden en vinkt zelf af'}
                    </span>
                  </label>
                </div>
                <div class="student-actions">
                  <Button variant="secondary" size="small" disabled={busy} onClick={() => void newPin(s)}>Nieuwe pincode</Button>
                  <Button variant="danger" size="small" disabled={busy} onClick={() => setRemoving(s)}>Verwijderen</Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Dialog open={adding} title="Leerlingen toevoegen" onClose={() => setAdding(false)}>
        <form class="stack" onSubmit={add}>
          <Field id="namen" label="Voornamen" hint="Eén voornaam per regel. Alleen de voornaam; geen achternaam of andere gegevens.">
            <textarea id="namen" value={names} onInput={(e) => setNames(e.currentTarget.value)} aria-describedby="namen-hint" />
          </Field>
          {error ? <Notice>{error}</Notice> : null}
          <div class="row">
            <Button type="submit" busy={busy} disabled={!names.trim()}>Toevoegen</Button>
            <Button variant="secondary" onClick={() => setAdding(false)}>Annuleren</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={newPins != null} title="Pincodes van de nieuwe leerlingen" onClose={() => setNewPins(null)}>
        <p>Je kunt de pincodes altijd terugzien in de klaslijst. Print meteen de inlogkaartjes voor je leerlingen.</p>
        <ul class="list">
          {newPins?.map((p) => (
            <li key={p.user_id} class="row-between card-flat" style={{ padding: '12px 16px' }}>
              <strong>{p.first_name}</strong>
              <span class="mono">{p.pin}</span>
            </li>
          ))}
        </ul>
        <div class="row">
          <Button onClick={() => navigate(`/klas/${classId}/kaartjes`)}><Icon name="print" size={20} /> Kaartjes printen</Button>
          <Button variant="secondary" onClick={() => setNewPins(null)}>Klaar</Button>
        </div>
      </Dialog>

      <Dialog open={removing != null} title={`${removing?.first_name ?? ''} verwijderen?`} onClose={() => setRemoving(null)}>
        <p>Het account en alle voortgang van deze leerling worden verwijderd. Dit kan niet ongedaan worden.</p>
        <div class="row">
          <Button variant="danger" busy={busy} onClick={() => void confirmRemove()}>Verwijderen</Button>
          <Button variant="secondary" onClick={() => setRemoving(null)}>Annuleren</Button>
        </div>
      </Dialog>
    </div>
  );
}
