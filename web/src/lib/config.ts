// Zelfde Supabase-project en publieke sleutel als de app (SupabaseConfig.kt). Deze sleutel hoort in
// de client; de echte beveiliging zit in de RLS-regels en functies van de database.
export const SUPABASE_URL = 'https://zjedxlvdeedpkbxzqkpo.supabase.co';
export const SUPABASE_ANON_KEY =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpqZWR4bHZkZWVkcGtieHpxa3BvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg4NzYzNDIsImV4cCI6MjEwNDQ1MjM0Mn0.qAs07pzMWGuQsqY1SFV2VGvs_UT0h0m6-1ftj14Lcp4';

/** Leerlingaccounts hebben een e-mailadres op dit domein (geen echt adres). */
export const STUDENT_EMAIL_DOMAIN = 'students.signcoach.invalid';

/** Leerling op een gedeeld apparaat: na zoveel minuten met de app op de achtergrond uitloggen. */
export const STUDENT_IDLE_LIMIT_MS = 10 * 60 * 1000;

export const isSchoolStudentEmail = (email: string | null | undefined) =>
  !!email && email.toLowerCase().endsWith(`@${STUDENT_EMAIL_DOMAIN}`);

/** De website (privacy-documenten). Wordt https://signcoach.nl zodra het domein er is. */
export const WEBSITE_URL = 'https://signcoachweb.rafi-elidrissi.workers.dev';
