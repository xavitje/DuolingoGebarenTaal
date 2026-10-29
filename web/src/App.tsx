import { useEffect } from 'preact/hooks';
import { auth, canContribute, isStaff } from './lib/auth';
import { loadLearning, resetLearning } from './lib/learning';
import { match, navigate, route } from './lib/router';
import { useStore } from './lib/store';
import { Shell } from './components/Shell';
import { Spinner } from './components/ui';
import { LoginPage } from './pages/Login';
import { StudentLoginPage } from './pages/StudentLogin';
import { RegisterPage } from './pages/Register';
import { ForgotPasswordPage, ResetPasswordPage } from './pages/Password';
import { LearnPage } from './pages/Learn';
import { LessonPage } from './pages/Lesson';
import { ProgressPage } from './pages/Progress';
import { ProfilePage } from './pages/Profile';
import { ClassesPage } from './pages/teacher/Classes';
import { ClassPage } from './pages/teacher/ClassDetail';
import { PrintCardsPage } from './pages/teacher/PrintCards';
import { ContributePage } from './pages/pro/Contribute';
import { RecordDemoPage } from './pages/pro/RecordDemo';
import { ReviewDemosPage } from './pages/pro/ReviewDemos';
import { RecordSamplePage } from './pages/pro/RecordSample';
import { LicenseExpired } from './pages/LicenseExpired';

const PUBLIC = ['/inloggen', '/leerling', '/aanmelden', '/wachtwoord-vergeten'];

export function App() {
  const path = useStore(route);
  const a = useStore(auth);

  useEffect(() => {
    if (a.status === 'loading') return;
    if (a.recovering && path !== '/wachtwoord-herstellen') {
      navigate('/wachtwoord-herstellen', true);
      return;
    }
    if (a.status === 'out' && !PUBLIC.includes(path)) navigate('/inloggen', true);
    if (a.status === 'in' && (PUBLIC.includes(path) || path === '/')) navigate('/leren', true);
    if (a.status === 'out') resetLearning();
  }, [a.status, a.recovering, path]);

  useEffect(() => {
    if (a.status === 'in' && a.userId) void loadLearning();
  }, [a.status, a.userId, a.profile?.sign_language]);

  useEffect(() => {
    const titles: Record<string, string> = {
      '/inloggen': 'Inloggen', '/leerling': 'Inloggen als leerling', '/aanmelden': 'Account aanmaken', '/leren': 'Leren',
      '/voortgang': 'Voortgang', '/profiel': 'Profiel', '/klas': 'Klas', '/bijdragen': 'Bijdragen',
    };
    document.title = titles[path] ? `${titles[path]} · SignCoach` : 'SignCoach';
  }, [path]);

  if (a.status === 'loading') return <Spinner />;

  if (a.status === 'out') {
    if (path === '/leerling') return <StudentLoginPage />;
    if (path === '/aanmelden') return <RegisterPage />;
    if (path === '/wachtwoord-vergeten') return <ForgotPasswordPage />;
    return <LoginPage />;
  }

  if (path === '/wachtwoord-herstellen') return <ResetPasswordPage />;
  if (!a.ready) return <Spinner />;

  // Leerling van een school met een verlopen licentie: alleen het profiel (om uit te loggen).
  if (a.isSchoolStudent && a.school && !a.school.license_active && path !== '/profiel') {
    return (
      <Shell>
        <LicenseExpired schoolName={a.school.school_name} />
      </Shell>
    );
  }

  let params: Record<string, string> | null;
  if ((params = match('/les/:id', path))) return <LessonPage key={params.id} gestureId={params.id} />;
  if (isStaff(a) && (params = match('/klas/:id/kaartjes', path))) return <PrintCardsPage classId={params.id} />;
  if (canContribute(a) && (params = match('/bijdragen/demo/:id', path))) return <RecordDemoPage gestureId={params.id} />;
  if (canContribute(a) && (params = match('/bijdragen/opname/:id', path))) return <RecordSamplePage gestureId={params.id} />;
  if (canContribute(a) && path === '/bijdragen/beoordelen') return <ReviewDemosPage />;

  let page = <LearnPage />;
  if (path === '/voortgang') page = <ProgressPage />;
  else if (path === '/profiel') page = <ProfilePage />;
  else if (path === '/klas' && isStaff(a)) page = <ClassesPage />;
  else if (isStaff(a) && (params = match('/klas/:id', path))) page = <ClassPage classId={params.id} />;
  else if (path === '/bijdragen' && canContribute(a)) page = <ContributePage />;
  else if (path !== '/leren') return <Redirect to="/leren" />;

  return <Shell>{page}</Shell>;
}

function Redirect({ to }: { to: string }) {
  useEffect(() => navigate(to, true), [to]);
  return null;
}
