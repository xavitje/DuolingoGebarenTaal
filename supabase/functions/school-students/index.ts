// supabase/functions/school-students/index.ts
//
// Leerlingen beheren door een docent, schoolbeheerder of admin:
//   { action: "create",    class_id, names: ["Sara", "Daan", ...] }  -> { students: [{ user_id, first_name, pin }] }
//   { action: "reset_pin", class_id, user_id }                       -> { pin }
//   { action: "remove",    class_id, user_id }                       -> { ok: true }
//
// Leerlingen krijgen een account zonder echt e-mailadres (<uuid>@students.signcoach.invalid) en een
// sterk, willekeurig intern wachtwoord dat alleen deze functies kennen. Inloggen gaat met klascode +
// naam + 4-cijferige pin (zie student-login). De pin wordt alleen gehasht opgeslagen; de docent ziet
// hem één keer, direct na aanmaken of resetten.
//
// Alleen aan te roepen met een geldige sessie van iemand die staff is van de school van de klas.
import { createClient } from "npm:@supabase/supabase-js@2";

const STUDENT_EMAIL_DOMAIN = "students.signcoach.invalid";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

function randomPin(): string {
  const a = new Uint32Array(1);
  crypto.getRandomValues(a);
  return String(a[0] % 10000).padStart(4, "0");
}

function randomPassword(): string {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/[^A-Za-z0-9]/g, "") + "Aa1!";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false },
  });
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });

  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const action = String(body.action ?? "");
  const classId = String(body.class_id ?? "");
  if (!classId) return json({ error: "bad_request" }, 400);

  const { data: cls } = await admin.from("classes").select("id, school_id").eq("id", classId).maybeSingle();
  if (!cls) return json({ error: "not_found" }, 404);

  // Rechtencheck met de sessie van de aanroeper: is hij staff van deze school (of admin)?
  const { data: isStaff } = await userClient.rpc("is_school_staff", { p_school_id: cls.school_id });
  if (isStaff !== true) return json({ error: "forbidden" }, 403);

  if (action === "create") {
    const names = (Array.isArray(body.names) ? body.names : [])
      .map((n) => String(n).trim())
      .filter((n) => n.length > 0 && n.length <= 40);
    if (names.length === 0 || names.length > 60) return json({ error: "bad_request" }, 400);

    const { data: school } = await admin
      .from("schools").select("max_students, license_start, license_end").eq("id", cls.school_id).single();
    const today = new Date().toISOString().slice(0, 10);
    if (!school || !(school.license_start <= today && today <= school.license_end)) {
      return json({ error: "license_expired" }, 409);
    }
    const { data: used } = await admin.rpc("school_seats_used", { p_school_id: cls.school_id });
    const seatsLeft = school.max_students - (used ?? 0);
    if (names.length > seatsLeft) return json({ error: "no_seats", seats_left: Math.max(0, seatsLeft) }, 409);

    const created: { user_id: string; first_name: string; pin: string }[] = [];
    for (const name of names) {
      const password = randomPassword();
      const pin = randomPin();
      const { data: made, error } = await admin.auth.admin.createUser({
        email: `${crypto.randomUUID()}@${STUDENT_EMAIL_DOMAIN}`,
        password,
        email_confirm: true,
        user_metadata: { first_name: name, school_student: true },
      });
      if (error || !made.user) {
        return json({ error: "create_failed", detail: error?.message, students: created }, 500);
      }
      const uid = made.user.id;
      const steps = [
        await admin.from("profiles").upsert({ id: uid, display_name: name, role: "student" }),
        await admin.from("class_students").insert({ class_id: classId, user_id: uid, first_name: name }),
        await admin.rpc("set_student_secret", { p_user_id: uid, p_password: password, p_pin: pin }),
      ];
      const failed = steps.find((s) => s.error);
      if (failed) {
        await admin.auth.admin.deleteUser(uid); // geen half aangemaakte leerling achterlaten
        return json({ error: "create_failed", detail: failed.error?.message, students: created }, 500);
      }
      created.push({ user_id: uid, first_name: name, pin });
    }
    return json({ students: created });
  }

  const userId = String(body.user_id ?? "");
  const { data: member } = await admin
    .from("class_students").select("user_id").eq("class_id", classId).eq("user_id", userId).maybeSingle();
  if (!member) return json({ error: "not_found" }, 404);

  if (action === "reset_pin") {
    const pin = randomPin();
    const { error } = await admin.rpc("set_student_secret", { p_user_id: userId, p_password: "", p_pin: pin });
    if (error) return json({ error: "server", detail: error.message }, 500);
    return json({ pin });
  }

  if (action === "remove") {
    // Verwijdert het account; profiel, voortgang en klaslidmaatschap gaan mee (on delete cascade).
    const { error } = await admin.auth.admin.deleteUser(userId);
    if (error) return json({ error: "server", detail: error.message }, 500);
    return json({ ok: true });
  }

  return json({ error: "bad_request" }, 400);
});
