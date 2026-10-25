// supabase/functions/student-login/index.ts
//
// Inloggen voor leerlingen: { class_code, user_id, pin } -> een gewone Supabase-sessie
// ({ access_token, refresh_token, expires_in, user: { id, email } }), net als bij inloggen met
// e-mail en wachtwoord. De app kiest user_id uit de namenlijst van de klas (RPC class_roster).
//
// Foute pin -> 401 { error: "invalid" }. Na 5 foute pogingen 15 minuten geblokkeerd -> 423 { error: "locked" }.
// Aan te roepen met de publieke anon-sleutel (geen sessie nodig).
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const classCode = String(body.class_code ?? "").trim().toUpperCase();
  const userId = String(body.user_id ?? "");
  const pin = String(body.pin ?? "");
  if (!/^[A-Z0-9]{6}$/.test(classCode) || !UUID.test(userId) || !/^\d{4}$/.test(pin)) {
    return json({ error: "bad_request" }, 400);
  }

  const url = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });

  const { data: password, error } = await admin.rpc("verify_student_pin", {
    p_code: classCode, p_user_id: userId, p_pin: pin,
  });
  if (error) {
    if (error.message.includes("locked")) return json({ error: "locked" }, 423);
    return json({ error: "server" }, 500);
  }
  if (!password) return json({ error: "invalid" }, 401);

  const { data: found } = await admin.auth.admin.getUserById(userId);
  if (!found?.user?.email) return json({ error: "server" }, 500);

  const anon = createClient(url, anonKey, { auth: { persistSession: false } });
  const { data, error: signInError } = await anon.auth.signInWithPassword({ email: found.user.email, password });
  if (signInError || !data.session) return json({ error: "server" }, 500);

  const s = data.session;
  return json({
    access_token: s.access_token,
    refresh_token: s.refresh_token,
    expires_in: s.expires_in,
    user: { id: s.user.id, email: s.user.email },
  });
});
