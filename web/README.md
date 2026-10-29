# SignCoach web-app

De browserversie van SignCoach, voor leerlingen, docenten en vakmensen. Dezelfde accounts, lessen
en voortgang als de Android-app (zelfde Supabase-project): wat een leerling op de tablet haalt, is in
de browser ook gehaald, en andersom.

- **Leerlingen**: inloggen met klascode, naam en pincode; leerpad; oefenen met de camera of met de
  zelfcheck; voortgang en badges. Na 10 minuten met de app op de achtergrond uitgelogd (gedeelde
  apparaten), net als in de app.
- **Docenten**: klassen, leerlingen toevoegen, pincodes, camera per leerling, inlogkaartjes printen,
  weekoverzicht en het feedbackformulier.
- **Vakmensen**: content-checklist, demovideo's opnemen en van collega's beoordelen,
  herkenningsopnames.

Beheer van scholen en licenties (admin) blijft in de Android-app.

## Hoe de herkenning in de browser werkt

- Hetzelfde MediaPipe-model als de Android-app (`shared/src/androidMain/assets/hand_landmarker.task`)
  en dezelfde gebundelde templates (`composeApp/.../files/templates.json`). `npm run build` kopieert
  ze; er is dus één bron.
- De scoring is een letterlijke port van de Kotlin-code (`LandmarkNormalizer`, `StaticGestureScorer`,
  `DynamicGestureScorer`, `RepCounter`, `LessonUnlocker`). De Kotlin-tests staan als
  `src/domain/domain.test.ts` ook hier. **Pas je de regels in Kotlin aan, pas ze hier ook aan.**
- Het camerabeeld wordt gespiegeld en staand 3:4 bijgesneden, zoals het portretbeeld van de
  Android-camera. De scoring corrigeert niet voor beeldverhouding; met een breed webcambeeld zouden
  handvormen anders gemeten worden.
- Alles draait in de browser. Er gaat geen camerabeeld naar een server; alleen "gehaald" en XP.
- Op een apparaat zonder echte videokaart (software-WebGL) gebruikt de herkenning de CPU; die is
  daar sneller.

## Lokaal draaien

```bash
cd web
npm install
npm run dev        # http://localhost:5173
npm test           # scoring- en leerpadtests
npm run build      # naar dist/
```

## Online zetten (Cloudflare)

Een tweede Worker naast de website, gekoppeld aan deze repo:

| Instelling | Waarde |
| --- | --- |
| Root directory | `web` |
| Build command | `npm run build` |
| Deploy command | `npx wrangler deploy` |
| Build-variabele | `NODE_VERSION` = `22` |

Geen runtime-variabelen nodig: de Supabase-URL en de publieke sleutel staan in `src/lib/config.ts`
(net als in de app). Beveiliging, camera-toestemming en de CSP staan in `public/_headers`.

### Supabase: één keer instellen

**Authentication → URL Configuration → Redirect URLs**: voeg het adres van de web-app toe, met `/**`
erachter (bijvoorbeeld `https://signcoach-app.<jouw-subdomein>.workers.dev/**`). Anders komen de
links uit "wachtwoord vergeten" en de bevestigingsmail niet terug in de web-app.

Komt er later een eigen domein (bijvoorbeeld `app.signcoach.nl`), voeg dat hier dan ook toe.
