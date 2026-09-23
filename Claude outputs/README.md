# SignCoach

SignCoach is een mobiele app waarmee gebruikers **Nederlandse Gebarentaal (NGT)** kunnen leren op een laagdrempelige, speelse manier — vergelijkbaar met hoe apps als Duolingo talen leren, maar dan gericht op gebaren.

## Wat de app doet

- **Gebaren leren via lessen**: de app biedt lespaden (zoals het gebarenalfabet) opgebouwd uit losse, opeenvolgende oefeningen die je stap voor stap ontgrendelt.
- **Camera-gebaseerde gebaarherkenning**: met de telefooncamera en handtracking (MediaPipe) beoordeelt de app of je een gebaar correct uitvoert, en geeft direct feedback.
- **Gamification**: gebruikers bouwen een **streak** (aantal dagen op rij geoefend) en **XP/gems** op om motivatie en herhaling te stimuleren.
- **Offline-first**: oefeningen en voortgang werken ook zonder internetverbinding — alles wordt lokaal opgeslagen en op de achtergrond gesynchroniseerd zodra er weer verbinding is, zodat er nooit voortgang verloren gaat.
- **Pushmeldingen**: gebruikers die een dag nog niet geoefend hebben, krijgen automatisch een herinnering om hun streak in leven te houden.
- **Privacy & GDPR**: de app bevat een privacybeleid-scherm en is opgezet met dataminimalisatie en transparantie in gedachten.
- **Crash reporting & adminpaneel**: onverwachte fouten worden (ook offline) vastgelegd en gesynchroniseerd naar een backend, waar ze via een adminpaneel in de app zelf ingezien kunnen worden.

## Doel

Het doel van SignCoach is om gebarentaal **toegankelijker en leerbaarder** te maken voor een breed publiek — of dat nu is uit interesse, om te kunnen communiceren met dove of slechthorende mensen, of als aanvulling op formeel gebarentaalonderwijs. Door directe feedback op de uitvoering van gebaren (in plaats van alleen passief kijken naar video's) leert de gebruiker actief en met herhaling, ondersteund door een systeem dat dagelijks oefenen aanmoedigt.

## Techniek

- **Kotlin Multiplatform (KMP) + Compose Multiplatform** — gedeelde business-logica en UI voor Android en iOS vanuit één codebase (`shared/` en `composeApp/`).
- **MediaPipe** — voor real-time handdetectie en het scoren van gebaren tegen een referentiepatroon.
- **SQLDelight** — lokale, typeveilige SQLite-database voor offline-first opslag van voortgang, lessen en pending sync-data.
- **Supabase** — backend (Postgres, authenticatie, Row Level Security, Edge Functions) voor accountbeheer, voortgang-sync, crashrapporten en het versturen van pushherinneringen.
- **Firebase Cloud Messaging** — voor pushmeldingen op Android.
- Package: `io.github.xavitje.signcoach`

## Projectstructuur (kort)

- `composeApp/` — platformspecifieke app-entrypoints en UI (Android/iOS), navigatie, schermen.
- `shared/` — gedeelde logica: domeinmodellen, repositories, API-clients, lokale database (SQLDelight), sync-logica, streak/XP-berekeningen.
- `supabase/functions/` — Edge Functions die op de backend draaien (o.a. het versturen van dagelijkse streak-pushmeldingen).
