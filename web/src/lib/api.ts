// Alle aanroepen naar Supabase, gelijk aan de API-klassen van de app (shared/.../data/remote).
// De rechten worden in de database afgedwongen (RLS en security-definer-functies).
import { SUPABASE_URL } from './config';
import { ApiError, callFunction, check, supabase } from './supabase';
import type { GestureRow } from '../domain/curriculum';
import type { ApprovedSample } from '../domain/templates';
import type { LandmarkPoint, SignTemplate } from '../domain/types';

export type Role = 'student' | 'teacher' | 'school_admin' | 'professional' | 'admin';

export interface Profile {
  id: string;
  display_name: string | null;
  avatar_emoji?: string;
  current_streak: number;
  longest_streak: number;
  total_xp: number;
  daily_goal_xp: number;
  streak_freezes_available: number;
  last_practice_date: string | null;
  sign_language: string;
}

export interface MySchool {
  school_id: string;
  school_name: string;
  class_name: string | null;
  staff_role: string | null;
  license_end: string;
  license_active: boolean;
  is_student: boolean;
  camera_allowed: boolean;
}

export interface SchoolClass {
  id: string;
  school_id: string;
  name: string;
  code: string;
}

export interface StudentProgress {
  user_id: string;
  first_name: string;
  completed_lessons: number;
  total_xp: number;
  current_streak: number;
  last_practice_date: string | null;
  camera_allowed: boolean;
  pin: string | null;
}

export interface WeekStats {
  week_start: string;
  active_students: number;
  total_students: number;
  attempts: number;
  successes: number;
}

export interface ContentStatus {
  gesture_id: string;
  name: string;
  unit_id: string | null;
  has_demo_video: boolean;
  pending: number;
  approved: number;
  demo_pending: number;
}

export interface DemoVideo {
  id: string;
  gesture_id: string;
  sign_language: string;
  storage_path: string;
  recorded_by: string | null;
  status: string;
  review_note: string | null;
  created_at: string | null;
}

export interface Badge {
  id: string;
  name: string;
  description: string;
  icon_key: string;
  sort_order: number;
}

// ─── Content ─────────────────────────────────────────────────────────────────

export async function fetchGestures(language: string): Promise<GestureRow[]> {
  return check(
    await supabase
      .from('gestures')
      .select('id,name,category,order_index,unit_id,demo_video_url')
      .eq('sign_language', language)
      .order('unit_id', { ascending: true })
      .order('order_index', { ascending: true }),
  ) as GestureRow[];
}

export async function fetchApprovedSamples(language: string): Promise<ApprovedSample[]> {
  return check(
    await supabase
      .from('gesture_samples')
      .select('gesture_id,landmarks,handedness,sign_language,gestures(category,unit_id)')
      .eq('status', 'approved')
      .eq('sign_language', language),
  ) as unknown as ApprovedSample[];
}

export async function fetchBundledTemplates(): Promise<SignTemplate[]> {
  const response = await fetch('/templates.json');
  if (!response.ok) throw new ApiError(response.status, 'templates');
  return (await response.json()) as SignTemplate[];
}

// ─── Account ─────────────────────────────────────────────────────────────────

export async function myRole(): Promise<Role> {
  const role = check(await supabase.rpc('my_role')) as string;
  return (['student', 'teacher', 'school_admin', 'professional', 'admin'].includes(role) ? role : 'student') as Role;
}

export async function mySchool(): Promise<MySchool[]> {
  return check(await supabase.rpc('my_school')) as MySchool[];
}

/** Profielrij aanmaken als hij nog niet bestaat (bestaande rij blijft ongemoeid). */
export async function ensureProfile(userId: string): Promise<void> {
  check(await supabase.from('profiles').upsert({ id: userId, sign_language: 'ngt' }, { onConflict: 'id', ignoreDuplicates: true }));
}

async function currentUserId(): Promise<string | null> {
  const { data } = await supabase.auth.getSession();
  return data.session?.user.id ?? null;
}

// Altijd op de eigen id filteren: een beheerder mag via RLS meer rijen zien dan alleen de eigen.
export async function getProfile(): Promise<Profile | null> {
  const id = await currentUserId();
  if (!id) return null;
  const rows = check(await supabase.from('profiles').select('*').eq('id', id).limit(1)) as Profile[];
  return rows[0] ?? null;
}

export async function getTodayXp(): Promise<number> {
  return Number(check(await supabase.rpc('get_today_xp'))) || 0;
}

// ─── Oefenen ─────────────────────────────────────────────────────────────────

export async function recordPractice(gestureId: string, score: number): Promise<void> {
  check(await supabase.rpc('record_practice', { p_gesture_id: gestureId, p_score: score }));
}

export async function checkAndAwardBadges(): Promise<void> {
  check(await supabase.rpc('check_and_award_badges'));
}

export async function logPracticeAttempt(gestureId: string, success: boolean): Promise<void> {
  check(await supabase.rpc('log_practice_attempt', { p_gesture_id: gestureId, p_success: success }));
}

export async function markPracticeDay(): Promise<void> {
  check(await supabase.rpc('mark_practice_day'));
}

export async function fetchCompletedGestureIds(language: string): Promise<string[]> {
  const id = await currentUserId();
  if (!id) return [];
  const rows = check(await supabase.from('user_progress').select('gesture_id').eq('user_id', id).eq('sign_language', language)) as { gesture_id: string }[];
  return rows.map((r) => r.gesture_id);
}

export async function fetchBadges(): Promise<Badge[]> {
  return check(await supabase.from('badges').select('*').order('sort_order', { ascending: true })) as Badge[];
}

export async function fetchEarnedBadgeIds(): Promise<string[]> {
  const id = await currentUserId();
  if (!id) return [];
  const rows = check(await supabase.from('user_badges').select('badge_id').eq('user_id', id)) as { badge_id: string }[];
  return rows.map((r) => r.badge_id);
}

// ─── Leerling-login ──────────────────────────────────────────────────────────

export async function classRoster(code: string): Promise<{ user_id: string; first_name: string }[]> {
  return check(await supabase.rpc('class_roster', { p_code: code.trim().toUpperCase() })) as { user_id: string; first_name: string }[];
}

export interface StudentSession {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user: { id: string; email: string };
}

export async function studentLogin(code: string, userId: string, pin: string): Promise<StudentSession> {
  return callFunction<StudentSession>('student-login', { class_code: code.trim().toUpperCase(), user_id: userId, pin }, false);
}

// ─── Docent ──────────────────────────────────────────────────────────────────

export async function myClasses(): Promise<SchoolClass[]> {
  return check(await supabase.from('classes').select('id,school_id,name,code').order('name', { ascending: true })) as SchoolClass[];
}

export async function createClass(schoolId: string, name: string): Promise<SchoolClass> {
  const rows = check(await supabase.from('classes').insert({ school_id: schoolId, name: name.trim() }).select('id,school_id,name,code')) as SchoolClass[];
  return rows[0];
}

export async function classOverview(classId: string): Promise<StudentProgress[]> {
  return check(await supabase.rpc('class_overview', { p_class_id: classId })) as StudentProgress[];
}

export async function createStudents(classId: string, names: string[]): Promise<{ user_id: string; first_name: string; pin: string }[]> {
  const res = await callFunction<{ students: { user_id: string; first_name: string; pin: string }[] }>('school-students', {
    action: 'create', class_id: classId, names,
  });
  return res.students ?? [];
}

export async function resetPin(classId: string, userId: string): Promise<string> {
  return (await callFunction<{ pin: string }>('school-students', { action: 'reset_pin', class_id: classId, user_id: userId })).pin;
}

export async function removeStudent(classId: string, userId: string): Promise<void> {
  await callFunction('school-students', { action: 'remove', class_id: classId, user_id: userId });
}

export async function setStudentCamera(classId: string, userId: string, allowed: boolean): Promise<void> {
  check(await supabase.rpc('set_student_camera', { p_class_id: classId, p_user_id: userId, p_allowed: allowed }));
}

export async function schoolStats(schoolId: string): Promise<WeekStats[]> {
  return check(await supabase.rpc('school_pilot_stats', { p_school_id: schoolId })) as WeekStats[];
}

export async function feedbackDue(): Promise<boolean> {
  return check(await supabase.rpc('feedback_due')) === true;
}

export interface FeedbackAnswers {
  p_usage: string;
  p_pupils_enjoy: number;
  p_recognition: number;
  p_ease_of_use: number;
  p_recommend: number;
  p_improve: string;
  p_best: string;
  p_may_quote: boolean;
}

export async function submitFeedback(answers: FeedbackAnswers): Promise<void> {
  check(await supabase.rpc('submit_teacher_feedback', answers as unknown as Record<string, unknown>));
}

// ─── Vakmensen ───────────────────────────────────────────────────────────────

export async function contentStatus(language: string): Promise<ContentStatus[]> {
  return check(await supabase.rpc('content_status', { p_sign_language: language })) as ContentStatus[];
}

export async function submitDemoVideo(userId: string, gestureId: string, language: string, path: string, video: Blob): Promise<void> {
  check(await supabase.storage.from('demo-review').upload(path, video, { contentType: video.type || 'video/mp4', upsert: false }));
  check(await supabase.from('demo_videos').insert({ gesture_id: gestureId, sign_language: language, storage_path: path, recorded_by: userId }));
}

export async function pendingDemoVideos(language: string): Promise<DemoVideo[]> {
  return check(
    await supabase
      .from('demo_videos')
      .select('id,gesture_id,sign_language,storage_path,recorded_by,status,review_note,created_at')
      .eq('status', 'pending')
      .eq('sign_language', language)
      .order('created_at', { ascending: true }),
  ) as DemoVideo[];
}

export async function demoSignedUrl(path: string): Promise<string> {
  const data = check(await supabase.storage.from('demo-review').createSignedUrl(path, 3600)) as { signedUrl: string };
  return data.signedUrl.startsWith('http') ? data.signedUrl : `${SUPABASE_URL}/storage/v1${data.signedUrl}`;
}

export async function reviewDemoVideo(id: string, approve: boolean, note: string | null): Promise<void> {
  await callFunction('review-demo-video', { id, approve, note: note?.trim() ? note.trim() : null });
}

export async function uploadReviewFrame(path: string, jpeg: Blob): Promise<void> {
  check(await supabase.storage.from('gesture-videos').upload(path, jpeg, { contentType: 'image/jpeg', upsert: false }));
}

export async function submitGestureSample(sample: {
  gesture_id: string;
  landmarks: LandmarkPoint[];
  handedness: string;
  video_path: string;
  contributor_id: string;
  sign_language: string;
}): Promise<void> {
  check(await supabase.from('gesture_samples').insert(sample));
}

export function randomId(length = 16): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (b) => chars[b % chars.length]).join('');
}
