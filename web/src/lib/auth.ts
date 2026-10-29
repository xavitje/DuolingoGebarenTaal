// Inloggen, rol en school van de gebruiker. Zelfde regels als AuthViewModel/StudentIdleGuard in de app.
import { STUDENT_IDLE_LIMIT_MS, isSchoolStudentEmail } from './config';
import { type MySchool, type Profile, type Role, ensureProfile, getProfile, myRole, mySchool, type StudentSession } from './api';
import { Store, local } from './store';
import { supabase } from './supabase';

export interface AuthState {
  status: 'loading' | 'out' | 'in';
  userId: string | null;
  email: string | null;
  role: Role;
  /** Leerlingaccount van een school (klascode + pincode). */
  isSchoolStudent: boolean;
  /** Eigen school(rij): voor leerlingen hun klas, voor docenten hun school. */
  school: MySchool | null;
  profile: Profile | null;
  /** Gebruiker kwam binnen via een wachtwoord-herstellink. */
  recovering: boolean;
  /** Rol en school zijn opgehaald (of het ophalen is mislukt). */
  ready: boolean;
}

const initial: AuthState = {
  status: 'loading', userId: null, email: null, role: 'student', isSchoolStudent: false, school: null, profile: null, recovering: false, ready: false,
};

export const auth = new Store<AuthState>(initial);

export const isStaff = (s: AuthState) => s.role === 'teacher' || s.role === 'school_admin' || (s.role === 'admin' && !!s.school && !s.school.is_student);
export const canContribute = (s: AuthState) => s.role === 'professional' || s.role === 'admin';
export const signLanguage = (s: AuthState) => (s.profile?.sign_language === 'asl' ? 'asl' : 'ngt');

const BACKGROUNDED_KEY = 'sc-backgrounded-at';
const LAST_CLASS_KEY = 'sc-last-class-code';

export const lastClassCode = {
  get: () => local.get<string | null>(LAST_CLASS_KEY, null),
  set: (code: string | null) => (code ? local.set(LAST_CLASS_KEY, code) : local.remove(LAST_CLASS_KEY)),
};

async function loadAccount(userId: string, email: string | null): Promise<void> {
  const studentAccount = isSchoolStudentEmail(email);
  auth.set((s) => ({ ...s, status: 'in', userId, email, isSchoolStudent: studentAccount, ready: false }));
  try {
    if (!studentAccount) await ensureProfile(userId).catch(() => undefined);
    const [role, schools, profile] = await Promise.all([myRole(), mySchool().catch(() => []), getProfile().catch(() => null)]);
    const school = studentAccount ? schools.find((x) => x.is_student) ?? null : schools.find((x) => !x.is_student) ?? null;
    auth.set((s) => ({ ...s, role: studentAccount ? 'student' : role, school, profile }));
  } catch {
    // Geen verbinding: ingelogd blijven met wat we weten; de pagina's tonen zelf een foutmelding.
  } finally {
    auth.set((s) => ({ ...s, ready: true }));
  }
}

export async function refreshProfile(): Promise<Profile | null> {
  const profile = await getProfile().catch(() => null);
  if (profile) auth.set((s) => ({ ...s, profile }));
  return profile;
}

export async function initAuth(): Promise<void> {
  expireIfIdle();

  supabase.auth.onAuthStateChange((event, session) => {
    if (event === 'SIGNED_OUT' || !session) {
      auth.set({ ...initial, status: 'out' });
      return;
    }
    if (event === 'PASSWORD_RECOVERY') auth.set((s) => ({ ...s, recovering: true }));
  });

  const { data } = await supabase.auth.getSession();
  if (data.session) await loadAccount(data.session.user.id, data.session.user.email ?? null);
  else auth.set({ ...initial, status: 'out' });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') local.set(BACKGROUNDED_KEY, Date.now());
    else {
      if (expireIfIdle()) void signOut();
      local.remove(BACKGROUNDED_KEY);
    }
  });
}

/** Leerling op een gedeeld apparaat: na 10 minuten op de achtergrond uitloggen. */
function expireIfIdle(): boolean {
  const backgroundedAt = local.get<number>(BACKGROUNDED_KEY, 0);
  if (!backgroundedAt || Date.now() - backgroundedAt <= STUDENT_IDLE_LIMIT_MS) return false;
  local.remove(BACKGROUNDED_KEY);
  let key: string | undefined;
  try {
    key = Object.keys(localStorage).find((k) => k.startsWith('sb-') && k.endsWith('-auth-token'));
  } catch {
    return false;
  }
  const stored = key ? local.get<{ user?: { email?: string } } | null>(key, null) : null;
  if (!isSchoolStudentEmail(stored?.user?.email)) return false;
  if (key) local.remove(key);
  return true;
}

export async function signInWithPassword(email: string, password: string) {
  const { data, error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
  if (error) throw error;
  await loadAccount(data.user.id, data.user.email ?? null);
}

export async function completeStudentLogin(session: StudentSession, classCode: string) {
  const { data, error } = await supabase.auth.setSession({ access_token: session.access_token, refresh_token: session.refresh_token });
  if (error || !data.user) throw error ?? new Error('session');
  lastClassCode.set(classCode);
  await loadAccount(data.user.id, data.user.email ?? null);
}

export async function signOut() {
  await supabase.auth.signOut({ scope: 'local' }).catch(() => undefined);
  auth.set({ ...initial, status: 'out' });
}
