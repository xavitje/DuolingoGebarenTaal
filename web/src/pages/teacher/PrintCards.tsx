// Inlogkaartjes, dezelfde opmaak als LoginCards.kt in de app, plus het adres van de web-app.
import { useEffect, useState } from 'preact/hooks';
import { type SchoolClass, type StudentProgress, classOverview, myClasses } from '../../lib/api';
import { navigate } from '../../lib/router';
import { Button, Notice, Spinner } from '../../components/ui';

export function PrintCardsPage({ classId }: { classId: string }) {
  const [cls, setCls] = useState<SchoolClass | null>(null);
  const [students, setStudents] = useState<StudentProgress[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    Promise.all([myClasses(), classOverview(classId)])
      .then(([list, s]) => {
        setCls(list.find((c) => c.id === classId) ?? null);
        setStudents(s);
        document.title = `Inlogkaartjes ${list.find((c) => c.id === classId)?.name ?? ''}`;
      })
      .catch(() => setError(true));
  }, [classId]);

  const host = location.host;
  return (
    <main id="main" class="print-page">
      <div class="no-print row-between" style={{ marginBottom: '20px' }}>
        <Button variant="secondary" onClick={() => navigate(`/klas/${classId}`)}>← Terug naar de klas</Button>
        <Button onClick={() => window.print()} disabled={!students?.length}>Printen</Button>
      </div>
      {error ? <Notice>Geen verbinding. Probeer het nog eens.</Notice> : null}
      {!cls || !students ? (error ? null : <Spinner />) : (
        <>
          <h1 style={{ fontSize: '16pt', margin: '0 0 4mm' }}>Inlogkaartjes · {cls.name}</h1>
          <div class="print-grid">
            {students.map((s) => (
              <div class="login-card" key={s.user_id}>
                <div class="brand">SignCoach</div>
                <div class="line"><span>Klascode</span><b class="mono">{cls.code}</b></div>
                <div class="line"><span>Naam</span><b>{s.first_name}</b></div>
                <div class="line"><span>Pincode</span><b class="mono">{s.pin ?? ' '.repeat(6)}</b></div>
                <div class="steps">
                  Open SignCoach (de app of <b>{host}</b>) → <b>Ik ben een leerling</b> → klascode, naam, pincode
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </main>
  );
}
