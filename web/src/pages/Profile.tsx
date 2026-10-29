import { useState } from 'preact/hooks';
import { auth, signOut } from '../lib/auth';
import { WEBSITE_URL } from '../lib/config';
import { useStore } from '../lib/store';
import { Button, Dialog, Icon } from '../components/ui';

const ROLE: Record<string, string> = {
  student: 'Leerling', teacher: 'Docent', school_admin: 'Schoolbeheerder', professional: 'Gebarentaalvakman', admin: 'Beheerder',
};

export function ProfilePage() {
  const a = useStore(auth);
  const [confirm, setConfirm] = useState(false);
  const name = a.profile?.display_name?.trim() || (a.isSchoolStudent ? 'Leerling' : a.email ?? '');

  return (
    <div class="page page-narrow stack-lg">
      <h1 class="display">Profiel</h1>
      <div class="card stack">
        <div class="row">
          <span class="letter-chip" aria-hidden="true" style={{ width: '64px', height: '64px', fontSize: '30px' }}>
            {(name[0] ?? '?').toUpperCase()}
          </span>
          <div class="stack-sm" style={{ gap: '2px' }}>
            <strong class="heading">{name}</strong>
            <span class="muted">
              {a.isSchoolStudent && a.school ? `${a.school.class_name ?? ''} · ${a.school.school_name}` : ROLE[a.role] ?? ''}
            </span>
            {!a.isSchoolStudent && a.school ? <span class="muted">{a.school.school_name}</span> : null}
            {!a.isSchoolStudent && a.email ? <span class="caption">{a.email}</span> : null}
          </div>
        </div>
      </div>

      <div class="card stack">
        <h2 class="heading">Privacy</h2>
        <p>De camera kijkt alleen op dit apparaat mee. Er wordt geen beeld opgenomen of verstuurd; alleen of een les gehaald is gaat naar de server.</p>
        <a href={`${WEBSITE_URL}/privacy/`} target="_blank" rel="noopener">Privacy en de documenten (opent de website)</a>
      </div>

      <Button variant="secondary" onClick={() => setConfirm(true)}>
        <Icon name="logout" size={20} /> Uitloggen
      </Button>

      <Dialog open={confirm} title="Uitloggen?" onClose={() => setConfirm(false)}>
        <p>Je voortgang wordt opgeslagen. Je kunt altijd weer inloggen.</p>
        <div class="row">
          <Button onClick={() => void signOut()}>Uitloggen</Button>
          <Button variant="secondary" onClick={() => setConfirm(false)}>Annuleren</Button>
        </div>
      </Dialog>
    </div>
  );
}
