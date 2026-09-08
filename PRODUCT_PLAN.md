# Plan: van test-app naar een volledige NGT-leerapp

Dit document beschrijft hoe SignCoach van de huidige testopstelling (3-4 losse gebaren, geen
login, geen enkele vorm van voortgang/motivatie) naar een echte, afgeronde leerapp gaat —
visueel en functioneel. Dit is een aanvulling op `PLAN.md` (dat gaat over de
herkenning/AI-architectuur: iOS-pariteit, gezichtstracking, getraind model). Dit document gaat
over de app die de gebruiker daadwerkelijk opent, gebruikt en blijft gebruiken.

**Scope-keuzes (met jou afgestemd):**
- Contentomvang v1: **volledig handalfabet (A-Z) + cijfers (0-9) — 36 gebaren**, gestructureerd
  zodat er later moeiteloos woorden/zinnen als vervolg-units bij kunnen.
- Visuele richting: **eigen NGT-merkidentiteit** — wél gamified en motiverend (streaks, voortgang,
  XP, een duidelijk leerpad), maar met een eigen kleurenpalet en toon, geen Duolingo-kloon.

## 0. Waar de app nu staat (nulmeting)

Kort en eerlijk, want dit bepaalt de scope van elke fase hieronder:
- Geen navigatie-library, geen theming-systeem (`MaterialTheme {}` zonder eigen kleuren/typografie),
  geen enkel merk-asset (icoon, lettertype, illustratie).
- Schermwissel is een handgeschreven `enum Screen { PRACTICE, CONTRIBUTE, ADMIN }` zonder terugknop-
  stack.
- Geen accounts voor gewone gebruikers — alleen het ene admin-account (dat we net gebouwd hebben)
  heeft auth. De app start altijd direct in de oefenmodus.
- 4 gebaren totaal in `templates.json` (A, B, L, "swipe rechts"), waarvan er 3 ook in de
  Supabase `gestures`-tabel staan (die tabel wordt overigens door de app zelf nooit gelezen — hij
  bestaat wel, maar doet nu niets).
- Geen streak, geen punten, geen "les" of "unit"-concept — gewoon 3 knoppen naast elkaar.
- Geen instellingenscherm, geen profiel, geen uitlogmogelijkheid voor gewone gebruikers (die
  bestaan immers nog niet).

Dat is de kloof die dit plan dicht.

## 1. Informatiearchitectuur: hoe de app eruit gaat zien

Vervang de huidige platte schermwissel door echte navigatie
(`androidx.navigation` compose-multiplatform, sinds Compose Multiplatform 1.7+ ook op iOS
beschikbaar — geen nieuwe onbekende dependency, wel nieuw voor dit project) met deze structuur:

```
Splash (sessie-check)
 └─ Niet ingelogd → Onboarding/Auth-flow
     ├─ Welkomstscherm (uitleg + "Inloggen" / "Account maken")
     ├─ Registreren (e-mail + wachtwoord, via Supabase Auth)
     └─ Inloggen (e-mail + wachtwoord, "wachtwoord vergeten")
 └─ Ingelogd → Hoofd-app (bottom navigation, 3 tabs)
     ├─ Tab "Leren"      → Leerpad (units → lessen → oefenscherm)
     ├─ Tab "Voortgang"  → Streak, XP-historie, behaalde badges
     └─ Tab "Profiel"    → Account, instellingen, uitloggen
```

De bestaande `CoachScreen`-oefenlogica (camera, tracker, scoring) verhuist ongewijzigd naar een
"Practice"-route die vanuit een lesnode wordt geopend — de hand-tracking/scoring-code zelf hoeft
niet te veranderen, alleen de manier waarop je er in/uit navigeert.

**Beslist: Admin wordt een voorwaardelijke tab in Profiel, geen verborgen entry-point.** De
huidige "Beheer"-tekstknop staat nu zichtbaar onder de oefenknoppen, voor iedereen — dat moet
weg zodra er echte gebruikers zijn. In plaats van een verstopte toegang: de app checkt na inloggen
of het ingelogde account een admin is, en toont dan gewoon een normale "Admin"-knop/tab in het
Profiel-scherm — voor iedere andere gebruiker is die knop er simpelweg niet. Technisch:
- Een nieuwe, kleine Postgres-functie `public.am_i_admin()` (`security definer`, net als de
  bestaande `is_admin()`), die elke ingelogde gebruiker via een RPC-call mag aanroepen en die
  alleen `true`/`false` teruggeeft voor *zijn eigen* account. Dit is nodig omdat de `admins`-tabel
  zelf bewust geen enkele leesbare policy heeft (dat is precies wat 'm veilig maakt) — dus de app
  kan niet zomaar `select * from admins` doen om te checken. Een RPC die alleen een boolean
  teruggeeft lekt niets (niet wie de andere admins zijn, alleen "ben ik er één").
- `AdminViewModel`/`AdminScreen` blijven functioneel ongewijzigd — de RLS-policies die je net
  gebouwd en getest hebt (alleen `is_admin()`-gebruikers zien pending-rijen/review-beelden) blijven
  de échte beveiliging. De `am_i_admin()`-check bepaalt alleen of de knop zichtbaar is, niet of de
  data toegankelijk is — dus zelfs als iemand de UI zou omzeilen, blijft de server-kant dicht.

## 2. Authenticatie & accounts

Hergebruik wat al bewezen werkt: Supabase Auth (e-mail/wachtwoord) draait al voor het
admin-account. Voor gewone gebruikers:
- Registratie/login via dezelfde Supabase Auth-API (`SupabaseAuthApi` bestaat al, deze sessie
  gebruikt hem alleen nog puur voor de admin-flow — wordt de basis voor de gebruikersflow).
- Sessie lokaal bewaren (access + refresh token) zodat je niet elke app-start opnieuw hoeft in te
  loggen — dit vereist een nieuwe, kleine multiplatform-opslaglaag (er bestaat nu letterlijk nog
  geen enkele lokale opslag in dit project buiten de gesynchroniseerde-templates-cache). Aanrader:
  `multiplatform-settings` (klein, stabiel, KMP-first) of Compose Multiplatform's eigen DataStore —
  beide zijn nieuwe dependencies, geen van beide bestaat nu al.
- Bij registratie automatisch een rij aanmaken in een nieuwe `profiles`-tabel (zie §5).
- "Wachtwoord vergeten" via Supabase's ingebouwde reset-mail — geen zelfgebouwde flow nodig.
- Account verwijderen (AVG-relevant, zie ook privacy-sectie in `PLAN.md`) hoort in het
  instellingenscherm thuis (§4).

**Beslist: Google-inloggen zit in v1**, naast e-mail/wachtwoord. Jij levert de Google OAuth
client ID + secret aan; die worden als provider in Supabase Auth (Authentication → Providers →
Google) geconfigureerd — dat gebeurt server-side in het Supabase-dashboard, geen geheimen in de
app zelf. Aan de client-kant komt er een aparte redirect-flow bij (Supabase's
`signInWithOAuth`-stijl aanpak, via een systeembrowser/`Custom Tabs` op Android en
`ASWebAuthenticationSession` op iOS) — iets meer platform-specifieke code dan het bestaande
`SupabaseAuthApi` (dat nu alleen het simpele wachtwoord-endpoint aanroept), maar een bekend,
goed gedocumenteerd patroon. Bij eerste Google-login geldt dezelfde `profiles`-aanmaak-stap als
bij e-mail-registratie (er is dan alleen geen wachtwoord om te zetten).

## 3. Curriculum: van "3 losse knoppen" naar een leerpad

**Datamodel-uitbreiding.** `SignTemplate` (en de Supabase `gestures`-tabel, die nu ongebruikt is
maar hier eindelijk een echte rol krijgt) krijgt er een paar velden bij:
- `category`: `"letter"` of `"number"` (later uit te breiden met `"word"`).
- `orderIndex`: bepaalt volgorde binnen de unit.
- `unitId`: groepeert gebaren in units — voor v1 gewoon twee units, "Alfabet" en "Cijfers", geen
  aparte `units`-tabel nodig (kan later alsnog als er meer structuur nodig is).

**`gestures`-tabel wordt de bron van waarheid** voor welke gebaren er zijn (nu leest de app 'm
nooit — dat verandert: bij opstarten haalt de app het curriculum uit deze tabel, niet meer alleen
uit het gebundelde `templates.json`). `templates.json` blijft bestaan als **offline-fallback**
(precies zoals het nu al voor sync werkt), maar wordt niet meer de plek waar je nieuwe gebaren
toevoegt — dat wordt gewoon een rij in Supabase, net als bij `gesture_samples` nu al.

**Het "swipe_right"-testgebaar** past niet in Alfabet/Cijfers en verdwijnt uit het standaard
leerpad (blijft evt. bruikbaar als interne test-case voor dynamische gebaren, buiten het
zichtbare curriculum).

**Leerpad-UI:** vervang de rij `FilterChip`s door een verticaal pad van lesnodes (cirkels), per
unit gegroepeerd, met drie zichtbare statussen: op slot, beschikbaar, voltooid. Elke node opent het
bestaande oefenscherm voor dat ene gebaar. Dit is puur UI-werk bovenop bestaande logica — geen
nieuwe scoring-code nodig.

**Belangrijke koppeling met `PLAN.md`:** met 4 gebaren was "1 opgeslagen template per gebaar"
al fragiel; met 36 gebaren wordt dat een veel groter betrouwbaarheidsprobleem.
 2 uit
`PLAN.md` (meerdere referentie-opnames per gebaar, multi-template-matching) gaat van "nice to
have" naar **randvoorwaarde** zodra het curriculum echt naar 36 gebaren groeit. Praktisch:
gebruik de bestaande contributie-flow (die al werkt, inclusief admin-goedkeuring) om per gebaar
meerdere eigen opnames vast te leggen, in plaats van de handmatig getekende 1-frame-templates die
er nu voor A/B/L staan.

**Hoeveel opnames per gebaar is genoeg?** Voor de multi-template/k-NN-aanpak (vergelijken met de
dichtstbijzijnde opgeslagen variant, niet een getraind model) geldt: méér opnames helpt, maar met
duidelijk afnemend rendement zodra je de echte bronnen van variatie al hebt gedekt. Twintig bijna
identieke opnames onder dezelfde lamp voegen veel minder toe dan acht bewust verschillende. Concreet
advies: **6-10 opnames per (statisch) gebaar**, gericht op deze assen:
- **Licht** (2-3 varianten): fel/direct licht, dimmer/indirect licht, en licht van opzij (schaduw op
  de hand).
- **Positie/afstand** (2-3 varianten): iets dichterbij, iets verder, en iets uit het midden van het
  beeld i.p.v. altijd gecentreerd.
- **Kleine hoekvariatie**: pols/hand net iets gedraaid of gekanteld t.o.v. een "schoolvoorbeeld"-
  houding — reëel, want niemand houdt zijn hand elke keer identiek.

Kruis twee lichtcondities met twee positie/hoek-varianten en je zit al op 4-6 zinvolle opnames; rond
af naar 6-10 en je hebt de belangrijkste praktijkvariatie gedekt. Voor gebaren met een beetje
beweging (sommige NGT-letters zijn niet volledig statisch) mag je er 2-3 extra bij doen om
timingverschillen te vangen. Boven de ~15-20 per gebaar levert extra volume met dezelfde ene hand
nauwelijks meer op met deze aanpak — dát is precies het punt waarop je *meer signers* nodig hebt in
plaats van meer opnames van jezelf, en dat hoort bij een latere stap (een echt getraind model, zie
`PLAN.md` Fase 2, waar volume én diversiteit tussen personen er wél toe doen — o.a. via Corpus
NGT/Global Signbank). Kortom: voor nu is bewuste variatie belangrijker dan aantal, en 6-10 goed
gekozen opnames per gebaar is een prima doel.

## 4. Gamification & motivatie — met een eigen gezicht, geen kopie

Met "eigen merkidentiteit" als uitgangspunt, hieronder concrete, haalbare bouwstenen (geen van
deze vereist een geanimeerde mascotte-illustrator — dat is bewust vermeden, past niet bij "minder
kopie" en is duur om zelf te maken):

- **Streak, inclusief freeze (beslist: zit in v1)**: aantal opeenvolgende dagen geoefend. Vereist
  datumlogica (nieuwe dependency: `kotlinx-datetime`, staat nu nergens in het project) en velden in
  `profiles` (`current_streak`, `longest_streak`, `last_practice_date`). Simpel, voorspelbaar
  freeze-mechanisme: elke gebruiker heeft **1 gratis streak-freeze per kalendermaand**
  (`streak_freezes_available`, reset op de 1e van de maand). Mis je een dag met nog een freeze
  beschikbaar, dan wordt die automatisch verbruikt (geen keuzemoment nodig) en blijft de streak
  intact; mis je een dag zonder freeze, dan reset de streak naar 0. Dit is bewust het simpelste
  eerlijke systeem — geen aparte "freeze kopen met XP"-economie nodig voor v1, dat kan altijd nog
  als vervolgstap.
- **XP/punten**: vast aantal punten per correct geoefend gebaar/afgeronde les. Geen levels nodig
  voor v1 — gewoon een oplopend totaal, zichtbaar op het profiel en per les.
- **Dagelijks doel**: gebruiker kiest (of krijgt standaard) een dagelijks XP-doel; een
  voortgangsring op het startscherm ("Leren"-tab) laat zien hoe ver je vandaag bent.
- **Afronding-moment**: een korte, eigen-stijl bevestiging (geen confetti-import nodig — een
  simpele geanimeerde vinkje/kleur-transitie in Compose is genoeg) zodra een gebaar of les
  gehaald wordt.
- **Badges met eigen ontworpen iconen (beslist)**: simpele tabel `badges` (id, naam, omschrijving,
  icoon-asset-pad) + `user_badges` (user_id, badge_id, behaald_op). Onder §6a hieronder staat een
  concrete lijst badge-concepten mét kant-en-klare prompts, zodat je die direct in een
  beeldgenerator (of bij een designer) kunt gebruiken — geen losse ontwerp-briefing nodig, de
  prompts zijn het startpunt.
- **Bewust NIET in v1**: een hartjes/levens-systeem (zoals Duolingo's "verlies een leven bij een
  fout") — dat vereist een aparte foutdefinitie bovenop de bestaande score (wanneer telt een
  poging als "fout"?) en straft net af wat deze app juist goed doet: iemand een gebaar laten
  herhalen tot de handvorm klopt. Kan later alsnog, maar is geen randvoorwaarde voor "voelt als een
  volledige app".

## 5. Supabase-schema: wat erbij komt

Naast de bestaande `gestures`, `gesture_samples`, `admins`:

```sql
-- Eén rij per gebruiker, gekoppeld aan auth.users
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  avatar_emoji text default '🧑',
  current_streak int not null default 0,
  longest_streak int not null default 0,
  last_practice_date date,
  streak_freezes_available int not null default 1,
  streak_freezes_reset_at date not null default date_trunc('month', now())::date,
  total_xp int not null default 0,
  daily_goal_xp int not null default 20,
  created_at timestamptz not null default now()
);
-- RLS: een gebruiker mag alleen zijn eigen profiel lezen/schrijven (using auth.uid() = id)

-- Laat een ingelogde gebruiker alleen checken of HIJZELF admin is (voor de Admin-tab in Profiel).
-- Leest niets uit de admins-tabel zelf bloot (die blijft zonder policies), en verandert niets aan
-- de bestaande RLS-beveiliging op gesture_samples/storage.objects.
create or replace function public.am_i_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists(select 1 from public.admins where user_id = auth.uid());
$$;
grant execute on function public.am_i_admin() to authenticated;

-- Voortgang per gebruiker per gebaar
create table public.user_progress (
  user_id uuid references auth.users(id) on delete cascade,
  gesture_id text references public.gestures(id),
  status text not null default 'locked', -- locked | available | completed
  best_score numeric,
  last_practiced_at timestamptz,
  primary key (user_id, gesture_id)
);
-- RLS: alleen eigen rijen zichtbaar/schrijfbaar

-- gestures-tabel uitbreiden (bestaat al, 3 rijen)
alter table public.gestures
  add column category text not null default 'letter',
  add column order_index int not null default 0;
```

`profiles` en `user_progress` volgen exact hetzelfde RLS-patroon dat je net voor de
admin-tabellen hebt gezien (policy die `auth.uid()` vergelijkt) — geen nieuwe technieken, wel
nieuwe tabellen/policies.

## 6. Visueel ontwerp: eigen stijl, geen Duolingo-groen

Concreet startpunt (aanpasbaar, maar een plan moet ergens beginnen):
- **Kleurenpalet**: een diep petrolblauw/indigo als primaire kleur (rust, "taal leren" i.p.v.
  speelsheid-voorop), met een warme amber/koraal accentkleur voor call-to-action-knoppen en
  streak/XP-indicatoren — bewust géén Duolingo-groen.
- **Typografie**: één rustig, goed leesbaar lettertype (systeemfont is prima voor v1 — een custom
  font toevoegen is puur cosmetisch en kan altijd later).
- **Vormtaal**: het leerpad gebruikt geometrische nodes (cirkels/afgeronde vormen) i.p.v. een
  illustratief personage — herkenbaar "pad-gevoel" zonder mascotte-ontwerp nodig te hebben.
- **Iconen**: Compose's ingebouwde Material Symbols zijn voldoende voor v1 (slot-icoon, vinkje,
  vlam voor streak, etc.) — geen aparte icon-set-productie nodig.
- Technisch: dit wordt een eigen `Theme.kt`/`Color.kt`/`Typography.kt`/`Shape.kt` in
  `composeApp` — bestaat momenteel he­lemaal niet (alles draait nu op kale
  `MaterialTheme {}`-defaults).

## 6a. Badge-ontwerp: concepten en kant-en-klare prompts

Acht badges als startset (later uit te breiden), passend bij het petrolblauw/indigo +
amber/koraal-palet uit §6 — bewust geometrisch/plat i.p.v. cartoon-illustratief, zodat ze bij de
"eigen merk, geen kopie"-richting passen. Elke regel is een kant-en-klare prompt voor een
beeldgenerator (bijv. Adobe Firefly of een andere afbeeldingen-AI); pas gerust de exacte
kleurcodes aan zodra het echte kleurenpalet vastligt.

| Badge | Wanneer behaald | Prompt |
|---|---|---|
| Eerste stap | Eerste gebaar ooit voltooid | "Flat minimalist app badge icon, a single geometric hand silhouette forming a simple sign, deep indigo (#2A3B5C) circle background, warm amber (#E8974A) outline accent, no text, no gradient, vector icon style, centered composition" |
| 7-daagse vlam | 7 dagen streak | "Flat minimalist app badge icon, a small geometric flame shape made of two overlapping rounded triangles, amber and coral gradient-free flat colors, deep indigo circular badge background, vector icon style, no text" |
| 30-daagse vlam | 30 dagen streak | "Flat minimalist app badge icon, a bolder geometric flame shape with a subtle laurel/wreath outline made of simple line strokes around it, amber flame on deep indigo circular background, vector icon style, no text, no gradients" |
| Alfabet compleet | Alle 26 letters voltooid | "Flat minimalist app badge icon, stylized geometric letters 'A' and 'Z' overlapping slightly, warm amber color on deep indigo circular badge background, clean vector icon style, no additional text, no gradients" |
| Cijfers compleet | Alle 10 cijfers voltooid | "Flat minimalist app badge icon, stylized geometric digits '1' '2' '3' arranged in a small ascending row, warm amber color on deep indigo circular badge background, clean vector icon style, no additional text" |
| Vroege vogel | Voor 8:00 geoefend | "Flat minimalist app badge icon, a simple geometric sunrise shape (half circle with three short rays), amber sun on deep indigo circular background, vector icon style, no text, no gradients" |
| Perfecte les | Les met maximale score gehaald | "Flat minimalist app badge icon, a simple geometric checkmark inside a rounded shield outline, amber checkmark on deep indigo circular background, vector icon style, no text, no gradients" |
| Bijdrager | Eigen gebaar-opname goedgekeurd | "Flat minimalist app badge icon, a simple geometric hand silhouette with a small plus sign beside it, amber accent on deep indigo circular background, vector icon style, no text, no gradients" |

Workflow-voorstel: genereer een paar varianten per prompt, kies de sterkste, en zorg dat alle acht
dezelfde compositie-regels aanhouden (cirkelvormige badge, indigo achtergrond, amber accent, geen
tekst in het icoon zelf) zodat de set als geheel consistent oogt — dat consistentie-punt is
belangrijker dan dat elk icoon individueel perfect is.

## 7. Profiel & instellingen

Nieuw scherm, ontbreekt nu volledig:
- **Profiel**: naam, e-mail, avatar (simpele emoji-picker, geen foto-upload nodig voor v1), totale
  XP, langste streak.
- **Voortgang**: overzicht per unit (Alfabet X/26, Cijfers X/10).
- **Instellingen**: wachtwoord wijzigen (alleen relevant bij e-mail-login, niet bij Google),
  uitloggen, account verwijderen (verwijdert `auth.users`-rij → cascadeert via foreign keys naar
  `profiles`/`user_progress`).
- **Admin (alleen zichtbaar voor admins)**: een aparte rij/knop bovenaan of onderaan het
  Profiel-scherm, alleen zichtbaar als `am_i_admin()` `true` teruggeeft, die naar het bestaande
  admin-dashboard leidt (functioneel ongewijzigd t.o.v. wat er nu al staat).
- **Notificaties**: een aan/uit-toggel voor "herinner me dagelijks" is zinvol voor motivatie, maar
  *push-notificaties zelf* zijn een apart, groter stuk infrastructuur (FCM/APNs-koppeling) — dat
  hoort niet in dezelfde fase als de rest van dit plan. Voorstel: toggle nu al in de UI zetten
  (voorbereiding), de daadwerkelijke meldingen als apart vervolgproject.

## 8. Gefaseerd stappenplan

**Fase A — Fundament (navigatie, thema, auth)**
- `androidx.navigation`-compose toevoegen, `App.kt` herstructureren naar een `NavHost`.
- Eigen `Theme.kt`/`Color.kt`/`Typography.kt`.
- Registreren/inloggen/uitloggen voor gewone gebruikers: e-mail/wachtwoord **én** Google (OAuth via
  Supabase, met jouw Google client ID/secret), sessie lokaal bewaard (`multiplatform-settings` of
  DataStore — nieuwe dependency).
- `profiles`-tabel + RLS-policies + `am_i_admin()`-functie; profiel-rij aanmaken bij registratie
  (ongeacht of via e-mail of Google).
- Bottom navigation met 3 tabs (lege/placeholder Voortgang- en Profiel-tabs mogen in deze fase nog
  minimaal zijn).
- "Beheer"-knop weghalen uit de hoofd-oefenscherm-UI; Admin-entry verhuist naar een
  voorwaardelijke rij in Profiel (zichtbaar via `am_i_admin()`).

**Fase B — Curriculum (alfabet + cijfers)**
- `gestures`-tabel uitbreiden (`category`, `order_index`), 33 ontbrekende gebaren toevoegen
  (huidige 3 + `swipe_right` eruit + 33 nieuwe = 36).
- `SignTemplate`/sync-laag aanpassen zodat curriculum uit `gestures` komt i.p.v. alleen uit
  `templates.json`.
- Leerpad-UI (units → lesnodes → bestaand oefenscherm).
- Zelf alle 36 gebaren opnemen via de bestaande contributie-flow (meerdere takes per gebaar,
  zie koppeling met `PLAN.md` Fase 2 hierboven), goedkeuren via het net gebouwde admin-paneel.

**Fase C — Motivatie**
- `user_progress`-tabel; les/gebaar-voltooiing schrijft ernaartoe.
- Streak-logica inclusief maandelijkse freeze (`kotlinx-datetime`), XP, dagelijks doel +
  voortgangsring.
- Voortgang-tab: streak (+ freezes resterend), XP-historie, per-unit-overzicht.

**Fase D — Polish**
- Afrondings-animatie, lege-staat-schermen, badges (iconen uit §6a laten genereren/ontwerpen en
  inbouwen).
- Profiel/instellingen compleet (avatar-picker, wachtwoord wijzigen, account verwijderen).
- Visuele consistentiepas over alle bestaande schermen (Contribute/Admin meenemen in het nieuwe
  thema, niet alleen de nieuwe schermen).

**Fase E — Validatie**
- Testen met echte nieuwe accounts, volledige registratie→leerpad→oefenen→streak-cyclus.
- Koppeling terugleggen naar `PLAN.md` Fase 2 (multi-template-matching) zodra er voor meerdere
  gebaren echt meerdere opnames binnen zijn.

## 9. Beslissingen (definitief)

Alle eerder open punten zijn nu vastgelegd:
- **Auth**: e-mail/wachtwoord + Google, beide in v1.
- **Admin-toegang**: voorwaardelijke tab/knop in Profiel via `am_i_admin()`, geen verborgen
  entry-point.
- **Streak-freeze**: zit in v1, 1 gratis freeze per kalendermaand, automatisch toegepast.
- **Badges**: eigen ontworpen iconen, startset + prompts staan in §6a.
- **Opnames**: jij neemt de 36 gebaren zelf op, met bewuste variatie in licht/positie/hoek — zie de
  concrete richtlijn (6-10 opnames per gebaar) in §3. Meerdere signers/echte datasetdiversiteit is
  een latere stap (gekoppeld aan `PLAN.md` Fase 2), niet nodig om v1 te shippen.

Geen resterende open beslissingen — dit plan is klaar om in fases (§8) opgepakt te worden.
