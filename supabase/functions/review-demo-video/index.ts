// Beoordelen van een demovideo door een vakman (of admin).
//
//   { id, approve: true }                  -> video naar de openbare bucket 'demo-videos', bij het gebaar gezet
//   { id, approve: false, note: "..." }   -> afgekeurd, opname uit 'demo-review' verwijderd
//
// Regels: alleen vakmensen en admins (can_contribute), en NOOIT je eigen opname beoordelen:
// elke demovideo wordt door een tweede persoon gecontroleerd.
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
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

  const { data: allowed } = await userClient.rpc("can_contribute");
  if (allowed !== true) return json({ error: "forbidden" }, 403);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const id = String(body.id ?? "");
  const approve = body.approve === true;
  const note = body.note == null ? null : String(body.note).slice(0, 500);
  if (!id) return json({ error: "bad_request" }, 400);

  const { data: video } = await admin.from("demo_videos").select("*").eq("id", id).maybeSingle();
  if (!video) return json({ error: "not_found" }, 404);
  if (video.status !== "pending") return json({ error: "already_reviewed" }, 409);
  if (video.recorded_by === user.id) return json({ error: "own_video" }, 403);

  if (!approve) {
    await admin.storage.from("demo-review").remove([video.storage_path]);
    const { error } = await admin.from("demo_videos").update({
      status: "rejected", reviewed_by: user.id, reviewed_at: new Date().toISOString(), review_note: note,
    }).eq("id", id);
    if (error) return json({ error: "server", detail: error.message }, 500);
    return json({ ok: true });
  }

  // Goedgekeurd: kopiëren naar de openbare bucket en bij het gebaar zetten.
  const { data: file, error: dlError } = await admin.storage.from("demo-review").download(video.storage_path);
  if (dlError || !file) return json({ error: "server", detail: dlError?.message ?? "download" }, 500);
  const publicPath = `${video.sign_language}/${video.gesture_id}/${video.id}.mp4`;
  const { error: upError } = await admin.storage.from("demo-videos").upload(publicPath, file, {
    contentType: "video/mp4",
    upsert: true,
  });
  if (upError) return json({ error: "server", detail: upError.message }, 500);
  const publicUrl = admin.storage.from("demo-videos").getPublicUrl(publicPath).data.publicUrl;

  const { error: gError } = await admin.from("gestures").update({ demo_video_url: publicUrl })
    .eq("sign_language", video.sign_language).eq("id", video.gesture_id);
  if (gError) return json({ error: "server", detail: gError.message }, 500);

  const { error: vError } = await admin.from("demo_videos").update({
    status: "approved", reviewed_by: user.id, reviewed_at: new Date().toISOString(), review_note: note,
  }).eq("id", id);
  if (vError) return json({ error: "server", detail: vError.message }, 500);

  await admin.storage.from("demo-review").remove([video.storage_path]);
  return json({ ok: true, url: publicUrl });
});
