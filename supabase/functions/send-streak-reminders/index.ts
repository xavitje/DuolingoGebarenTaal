// supabase/functions/send-streak-reminders/index.ts
//
// Dagelijkse (via pg_cron, zie migratie) streak-herinnering: zoekt gebruikers die vandaag nog
// niet geoefend hebben en stuurt ze een pushbericht via Firebase Cloud Messaging (HTTP v1 API).
// Vereist het secret FCM_SERVICE_ACCOUNT_JSON (de volledige JSON van een Firebase
// service-account-sleutel met de "Firebase Cloud Messaging API"-rol) — zolang dat niet gezet is,
// doet deze functie veilig niets (geen crash, gewoon een duidelijke log + skipped-response).
import { createClient } from "npm:@supabase/supabase-js@2";

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

function base64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let str = "";
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binaryDer = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** Wisselt de service-account-sleutel in voor een kortlevend OAuth2-accesstoken (JWT-bearer
 * grant), nodig om de FCM HTTP v1 API aan te roepen. Zie
 * https://firebase.google.com/docs/cloud-messaging/auth-server. */
async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const encHeader = base64url(JSON.stringify(header));
  const encClaim = base64url(JSON.stringify(claim));
  const signingInput = `${encHeader}.${encClaim}`;
  const key = await importPrivateKey(sa.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64url(signature)}`;

  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!resp.ok) {
    throw new Error(`OAuth token request failed: ${resp.status} ${await resp.text()}`);
  }
  const data = await resp.json();
  return data.access_token as string;
}

Deno.serve(async (_req: Request) => {
  try {
    const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
    if (!saJson) {
      console.error("FCM_SERVICE_ACCOUNT_JSON secret is niet gezet — sla het versturen over.");
      return new Response(JSON.stringify({ skipped: true, reason: "no_fcm_secret" }), { status: 200 });
    }
    const sa: ServiceAccount = JSON.parse(saJson);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, serviceRoleKey);

    const today = new Date().toISOString().slice(0, 10);

    // Gebruikers die vandaag nog niet (of nog nooit) geoefend hebben.
    const { data: profiles, error: profilesError } = await supabase
      .from("profiles")
      .select("id, last_practice_date")
      .or(`last_practice_date.is.null,last_practice_date.lt.${today}`);
    if (profilesError) throw profilesError;
    if (!profiles || profiles.length === 0) {
      return new Response(JSON.stringify({ sent: 0, reason: "no_users_due" }), { status: 200 });
    }

    const userIds = profiles.map((p: { id: string }) => p.id);
    const { data: tokens, error: tokensError } = await supabase
      .from("device_push_tokens")
      .select("user_id, token")
      .in("user_id", userIds);
    if (tokensError) throw tokensError;
    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0, reason: "no_tokens" }), { status: 200 });
    }

    const accessToken = await getAccessToken(sa);
    let sent = 0;
    let failed = 0;

    for (const row of tokens as { user_id: string; token: string }[]) {
      const resp = await fetch(
        `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: row.token,
              notification: {
                title: "Tijd om te oefenen! 🤟",
                body: "Je hebt vandaag nog niet geoefend — hou je streak in leven met één gebaar.",
              },
            },
          }),
        },
      );
      if (resp.ok) {
        sent++;
      } else {
        failed++;
        const body = await resp.text();
        console.error(`FCM-verzending mislukt voor token ${row.token.slice(0, 12)}…: ${resp.status} ${body}`);
        // Een ongeldig/verlopen token opruimen zodat we er niet op blijven proberen.
        if (resp.status === 404 || resp.status === 400) {
          await supabase.from("device_push_tokens").delete().eq("token", row.token);
        }
      }
    }

    return new Response(JSON.stringify({ sent, failed }), { status: 200 });
  } catch (e) {
    console.error("send-streak-reminders mislukt:", e);
    return new Response(JSON.stringify({ error: String(e) }), { status: 500 });
  }
});
