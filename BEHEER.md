# SignCoach — beheerhandleiding

Spiekbriefje voor het dagelijks beheer: rollen toewijzen, content toevoegen, bouwen en testen.
Alle SQL hieronder plak je in Supabase onder **SQL Editor** en voer je uit met **Run**.

Bijgewerkt: 25 oktober 2026 (na fase 0 t/m 3 van het implementatieplan).

---

## 1. Rollen

Elke gebruiker heeft één rol. Nieuwe accounts zijn altijd **student**.

| Rol (in de database) | Wie | Ziet en mag |
| --- | --- | --- |
| `student` | Leerling | Leerpad, oefenen, eigen voortgang en badges |
| `teacher` | Docent | Tabblad **Klas**: klassen, leerlingen, pincodes, voortgang |
| `school_admin` | ICT-coördinator / directie | Zelfde als docent (alle klassen van de school) |
| `professional` | Vakman: gebarentaaldocent of tolk | Gebaren opnemen via "Draag een gebaar bij" |
| `admin` | Jij | Alles, inclusief het Admin Panel en bijdragen goedkeuren |

Gebruikers kunnen hun eigen rol niet veranderen. Dat kan alleen een admin, of jij via de SQL Editor.
Na een rolwijziging moet de gebruiker **uit- en opnieuw inloggen** in de app.

### Een vakman aanwijzen

```sql
update public.profiles set role = 'professional'
 where id = (select id from auth.users where email = 'naam@voorbeeld.nl');
```

De vakman moet eerst zelf een account hebben aangemaakt in de app. Anders verandert er niets
("0 rows").

### Een docent of schoolbeheerder aanwijzen

Doe dit in de app: **Profiel → Admin Panel → Scholen en licenties → school → Koppelen** (zie §2).
Dan krijgt de docent meteen de juiste rol én de koppeling met de school. Alleen de rol zetten
via SQL is niet genoeg: een docent zonder school ziet "Nog niet gekoppeld aan een school".

### Een rol intrekken (terug naar leerling)

```sql
update public.profiles set role = 'student'
 where id = (select id from auth.users where email = 'naam@voorbeeld.nl');
```

### Een extra admin toevoegen

Admins staan in een aparte tabel. Zet iemand in beide:

```sql
insert into public.admins (user_id)
select id from auth.users where email = 'naam@voorbeeld.nl';

update public.profiles set role = 'admin'
 where id = (select id from auth.users where email = 'naam@voorbeeld.nl');
```

Admin afnemen: verwijder de rij uit `public.admins` en zet de rol terug op `student`.

### Overzicht: wie heeft welke rol?

```sql
select u.email, p.role, p.created_at
  from public.profiles p join auth.users u on u.id = p.id
 order by p.role, u.email;
```

---

## 2. Scholen, klassen en leerlingen

### Een nieuwe school (klant)

1. **Profiel → Admin Panel → Scholen en licenties → Nieuwe school.** Vul naam, aantal
   leerlingplekken en de einddatum van de licentie in (JJJJ-MM-DD).
2. Laat de docent zelf een account aanmaken in de app (gewoon registreren met e-mail).
   **E-mailbevestiging staat aan**: de docent krijgt een mail met een link en moet daarop klikken
   voordat hij kan inloggen. (Uitzetten kan in Supabase onder Authentication → Sign In / Providers →
   Email → "Confirm email", maar voor scholen is aan laten veiliger.)
3. Open de school en koppel de docent via zijn e-mailadres, als **Docent** of **Schoolbeheerder**.
4. De docent logt uit en weer in en ziet dan het tabblad **Klas**.

### Wat de docent doet

1. **Klas → Nieuwe klas** (bijv. "Groep 7b"). De klas krijgt een klascode van 6 tekens.
2. **Leerlingen toevoegen**: één voornaam per regel. De app toont daarna per leerling een
   **pincode van 4 cijfers — maar één keer**. Opschrijven of uitprinten.
3. Pincode kwijt? Bij de leerling op **Nieuwe pincode** tikken.
4. Per leerling ziet de docent: gehaalde lessen, XP, dagen op rij en wanneer laatst geoefend.

### Hoe een leerling inlogt

Inlogscherm → **Ik ben een leerling** → klascode → eigen naam aantikken → pincode.
Na 5 foute pincodes is die leerling 15 minuten geblokkeerd (de docent kan een nieuwe pincode maken).

### Licentie verlengen of plekken erbij

**Admin Panel → Scholen en licenties → school**: aantal plekken en einddatum aanpassen, **Opslaan**.

Als de licentie verlopen is:
- kunnen leerlingen niet meer inloggen (hun klascode werkt niet meer), en zien al ingelogde
  leerlingen een melding in plaats van de lessen;
- ziet de docent een rode melding en kan hij geen leerlingen toevoegen;
- blijft alle voortgang bewaard. Na verlengen werkt alles weer.

Zijn alle plekken vol, dan kan de docent geen nieuwe leerlingen toevoegen (bestaande leerlingen
blijven gewoon werken).

### Overzicht in SQL

```sql
select s.name, s.license_end, s.max_students,
       public.school_seats_used(s.id) as leerlingen,
       (select count(*) from public.classes c where c.school_id = s.id) as klassen
  from public.schools s order by s.name;
```

### Hoe het technisch werkt

- Leerlingen krijgen een account zonder echt e-mailadres (`…@students.signcoach.invalid`) en een
  willekeurig intern wachtwoord dat niemand ziet. De pincode wordt alleen versleuteld (gehasht)
  opgeslagen.
- Dat regelen twee **edge functions** in Supabase: `school-students` (docent maakt leerlingen
  aan, reset pincodes, verwijdert leerlingen) en `student-login` (leerling logt in).
  De code staat in `supabase/functions/`.
- Een edge function bijwerken: Supabase → **Edge Functions** → functie → **Code** → nieuwe code
  plakken → **Deploy**.

---

### Privacy: wat er met leerlinggegevens gebeurt

- **Leerling verwijderen**: de docent doet dit in het tabblad Klas. Account, voortgang, badges en
  pincode verdwijnen direct.
- **Einde licentie**: 90 dagen na de einddatum worden alle leerlingaccounts, voortgang en klassen
  van die school automatisch gewist (dagelijkse taak, zie hieronder). De school zelf, de
  licentiegegevens en de docentaccounts blijven bestaan; docenten zien de school daarna niet meer.
- **School vraagt eerder om verwijdering**: Admin Panel → Scholen → open de school →
  **Leerlinggegevens verwijderen**. Dit kan niet ongedaan worden gemaakt.
- **Crashrapporten** zijn anoniem: geen gebruikers-ID, en e-mailadressen, ID's en tokens worden er in
  de app uit gehaald voordat ze verstuurd worden.
- **Pushmeldingen** krijgen leerlingen niet; er wordt voor hen geen apparaat-token opgeslagen.
- **Camerabeelden** van leerlingen verlaten het apparaat nooit. Alleen "gebaar X gehaald" en XP gaan
  naar de server. Alleen vakmensen sturen beelden in, bij het bijdragen van een gebaar.

**Dagelijkse opschoontaak instellen (eenmalig):** zet in Supabase onder Database → Extensions
`pg_cron` aan en voer dan in de SQL Editor uit:

```sql
select cron.schedule('purge-expired-schools', '17 3 * * *', $$select public.purge_expired_schools()$$);
```

Controleren: `select * from cron.job;`

## 3. Content: gebaren en lessen

### Hoe een les open of dicht staat

- De **eerste les van elke unit** staat altijd open.
- Een les gaat open zodra de **vorige gehaald** is.
- Een gebaar **zonder content** (geen herkenning en geen video) toont een klokje: "komt binnenkort".
  Zo'n gebaar blokkeert het pad niet; de volgende les gaat gewoon open.
- Een les is gehaald na **3 keer goed** voor de camera, of na "Gelukt!" bij een les met alleen een video.

### Wat een gebaar oefenbaar maakt

| Er is... | Dan ziet de leerling |
| --- | --- |
| Een goedgekeurde opname (herkenning) | Demovideo (indien aanwezig), daarna oefenen met de camera |
| Alleen een demovideo | Demovideo, daarna zelfcheck ("Gelukt!" / "Nog een keer kijken") |
| Niets | Klokje "komt binnenkort" |

### Een opname goedkeuren

Vakmensen dienen opnames in via **Profiel → Draag een gebaar bij**. Jij keurt ze goed in de app via
**Profiel → Admin Panel**. Na goedkeuring krijgen leerlingen dat gebaar bij de volgende start van de
app met camera-herkenning.

### Een demovideo koppelen aan een gebaar

```sql
update public.gestures
   set demo_video_url = 'https://.../letter_c.mp4'
 where sign_language = 'ngt' and id = 'letter_c';
```

Gebaar-id's: `letter_a` … `letter_z`, `number_0` … `number_9` (NGT). De video moet via een
openbare https-link te bereiken zijn.

### Welke gebaren hebben al content?

```sql
select g.id, g.name,
       g.demo_video_url is not null as heeft_video,
       count(s.id) filter (where s.status = 'approved') as goedgekeurde_opnames,
       count(s.id) filter (where s.status = 'pending')  as wacht_op_goedkeuring
  from public.gestures g
  left join public.gesture_samples s
    on s.gesture_id = g.id and s.sign_language = g.sign_language
 where g.sign_language = 'ngt'
 group by g.id, g.name, g.demo_video_url, g.unit_id, g.order_index
 order by g.unit_id, g.order_index;
```

---

## 4. Bouwen en testen

### JDK

Gebruik **JDK 17** (`C:\Program Files\Java\jdk-17`). De Java die bij Android Studio zit (versie 25)
is te nieuw voor deze Gradle-versie en geeft een foutmelding met alleen "25.0.3".
In Android Studio: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**
op 17 zetten.

### Vanuit PowerShell

```powershell
cd D:\DuolingoGebarenTaal
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# Tests
.\gradlew.bat :shared:testDebugUnitTest

# App bouwen
.\gradlew.bat :composeApp:assembleDebug
```

De app (APK) staat daarna in `composeApp\build\outputs\apk\debug\composeApp-debug.apk`.

### Installeren op de emulator of een telefoon

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r composeApp\build\outputs\apk\debug\composeApp-debug.apk
& $adb shell am start -n io.github.xavitje.signcoach/.MainActivity
```

Let op: de emulator heeft geen echte camera. Test het oefenen met de camera op een echte telefoon.

---

## 5. Database-wijzigingen

- Alle wijzigingen staan als SQL-bestand in `supabase/migrations/`. Voer ze in volgorde uit via de
  SQL Editor. Ze zijn zo geschreven dat nog een keer uitvoeren geen kwaad kan.
- `supabase/inspect/fase2_inspectie.sql` geeft een alleen-lezen overzicht van rechten (policies),
  functies en kolommen. Handig om te plakken als iemand (of Claude) moet weten hoe de database er
  nu uitziet.

Uitgevoerd tot nu toe:

| Bestand | Wat | Uitgevoerd |
| --- | --- | --- |
| `20260915202639_add_sign_language_support.sql` | Gebarentaal per gebaar/profiel | Ja |
| `20261025000000_fase2_rollen.sql` | Rollen; bijdragen alleen voor vakmensen en admins | Ja, 25-10-2026 |
| `20261025210000_fase3_scholen.sql` | Scholen, klassen, leerlingen, licenties | Ja |
| `20261025230000_fase3_fix_rollen.sql` | Docentrol klopt ook zonder profielrij (na fase 3 uitvoeren) | Ja |
| `20261026190000_fase4_privacy.sql` | Gegevens verwijderen bij account/licentie-einde, anonieme crashrapporten | Nog niet |

Edge functions (Supabase → Edge Functions):

| Functie | Wat | Gedeployd |
| --- | --- | --- |
| `send-streak-reminders` | Dagelijkse herinnering | (bestond al) |
| `school-students` | Leerlingen aanmaken, pincode resetten, verwijderen | Ja |
| `student-login` | Inloggen met klascode + naam + pincode | Ja |

---

## 6. Bekende open punten

- **iOS** is geschreven maar nooit gebouwd of getest (zie `IOS_SETUP.md`). Veel scholen gebruiken iPads.
- **Privacydocumenten** voor scholen (verwerkersovereenkomst, privacybijsluiter, beveiligingsbijlage,
  DPIA) moeten nog gemaakt en door een privacyjurist nagekeken worden.
- **Supabase-regio**: het privacybeleid zegt "EU (Frankfurt)". Controleer dit in Supabase onder
  Project Settings → General → Region.
- **Lokale app-database** is bij fase 0 naar schemaversie 3 gegaan (bijhouden van gehaalde lessen).
- Alles staat op branch **`fase-0-1-scholen`**, nog niet samengevoegd met `main`.

Het volledige plan (fase 0 t/m 5) staat in het document "SignCoach — Implementatieplan voor scholen".
