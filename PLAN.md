# Plan: van MVP naar een betrouwbare NGT-coach app

Dit document beschrijft de aanbevolen vervolgstrategie voor SignCoach: iOS-pariteit, een architectuur die niet per se offline hoeft te zijn, en een aanpak die de betrouwbaarheid van de feedback (hand + gezichtsuitdrukking) echt naar een bruikbaar niveau tilt voor Nederlandse Gebarentaal (NGT).

De kern van het advies: blijf de camera-verwerking (handen + gezicht) **on-device** doen, ook als de app "online" wordt. Gebruik een server niet voor de real-time herkenning zelf, maar voor alles eromheen: curriculum, accounts, modeldistributie en het verzamelen van trainingsdata. Hieronder de onderbouwing en een gefaseerd plan.

## 1. Waarom iOS nu wél kan (en waarom het nu `UNSUPPORTED` is)

De huidige `IosHandTrackerBridge` geeft bewust `UNSUPPORTED` terug omdat er nog geen MediaPipe-koppeling is gebouwd — niet omdat het niet zou kunnen. MediaPipe Tasks Vision heeft een volwaardige native iOS-SDK (`MediaPipeTasksVision`, via CocoaPods of Swift Package Manager) met dezelfde bouwstenen als op Android:

- **Hand Landmarker** in `liveStream`-modus, met dezelfde trackingoptimalisatie (geen palm-detectie op elk frame) als op Android. Werkt met `CVPixelBuffer`/`CMSampleBuffer`, dus goed te koppelen aan een AVFoundation-camerafeed.
- **Face Landmarker** met optionele **blendshapes**-output: 52 coëfficiënten die gezichtsuitdrukking beschrijven (o.a. `browDownLeft`, `mouthPucker`, `jawOpen`), standaard uit, met één vlag aan te zetten.

Beide zijn native, on-device en vereisen geen netwerk. De iOS-kant kan dus qua camera-tracking op hetzelfde niveau komen als Android, volledig offline-capable, via een dunne Swift-laag die vanuit `iosMain` wordt aangeroepen (zoals de `expect`/`actual`-structuur in dit project al voorbereidt).

Bron: MediaPipe iOS-documentatie (Hand Landmarker en Face Landmarker guides, developers.google.com/edge/mediapipe).

## 2. Gezichtsuitdrukking: wat er onderzoek naar is gedaan

Voor NGT (en gebarentalen in het algemeen) zijn non-manual markers — mondbeeld, wenkbrauwen, hoofdhouding — linguïstisch verplicht: ze veranderen de betekenis van een gebaar (bijvoorbeeld ja/nee-vragen, ontkenning, gradatie). Recent onderzoek naar het belang van gezichtsregio's voor gebarentaalherkenning (arXiv 2507.20884, "The Importance of Facial Features in Vision-based Sign Language Recognition") laat zien:

- Hand/lichaam blijft veruit de belangrijkste bron (80-84% top-1 accuracy alleen al met hand/lichaamslandmarks).
- **De mond is de belangrijkste gezichtsregio** — toevoegen aan handdata gaf een meetbare accuracy-boost, vooral bij gebaren met identieke handvorm maar verschillende mondbeelden.
- Volledig gezicht voegde nauwelijks iets toe bovenop mond alleen.
- Ogen droegen nauwelijks bij zodra handdata aanwezig was.

**Praktisch advies:** breid het datamodel uit met mond-regio-landmarks/blendshapes als eerste non-manual signaal (relatief goedkoop, hoge impact), en behandel volledig-gezicht-tracking als latere verfijning voor hoofdhouding/wenkbrauwen, niet als dag-1-vereiste.

## 3. Van "1 template vergelijken" naar een echt betrouwbaar model

De huidige `StaticGestureScorer`/`DynamicGestureScorer` vergelijkt een live pose met **één** opgeslagen "gouden" template via een afstandsmaat. Dat is een prima startpunt voor een demo met 4 gebaren, maar het schaalt niet: het README erkent zelf al dat de templates gekalibreerd moeten worden op meerdere handen, huidtinten en lichtomstandigheden. Eén vector per gebaar kan die variatie niet vangen, en dekt ook geen twee-handige gebaren (nu is er een limiet van één hand).

De literatuur over geïsoleerde gebarentaalherkenning (o.a. onderzoek naar lichte transformers/GRU's over landmark-sequenties in plaats van ruwe video) laat zien dat een **klein getraind classificatiemodel op landmark-sequenties** veel robuuster is dan template-matching, en toch klein genoeg blijft om on-device te draaien (landmarks zijn al sterk gecomprimeerde data vergeleken met video-frames).

Voor trainingsdata is er een concreet aanknopingspunt specifiek voor NGT: het **Corpus NGT** en de **NGT-dataset in Global Signbank** (Radboud Universiteit / CLS, in samenwerking met het Max Planck Instituut). Dit is geen klein hobbyproject: het bevat ruim 5700 gebaren met gestandaardiseerde fonologische annotatie (handvorm, locatie, beweging, oriëntatie), video vanuit meerdere camerahoeken, én voor circa 1000 gebaren al **3D motion-capture data van skelet, hand en gezicht** — expliciet verzameld met als doel automatische herkenning te trainen. Dat is precies het soort data dat nodig is om van "1 handmatig getekende template" naar een echt getrained model te gaan.

**Update — licentie is bevestigd.** De NGT-dataset in Global Signbank bevat 6862 openbare glossen (7469 totaal), gezamenlijk eigendom van Radboud Universiteit en de Universiteit van Amsterdam. De losse lexicale items (video + fonologische annotatie per gebaar) staan onder **CC BY 4.0** — vrij te gebruiken, óók commercieel, mits naamsvermelding. Eén belangrijke uitzondering: de voorbeeldzinnen uit het Corpus NGT (doorlopende zinnen in context — juist relevant voor non-manual markers zoals mondbeeld in natuurlijke zinnen) staan onder de strengere **CC BY-NC-SA 4.0**, dus niet-commercieel. Voor training op losse, geïsoleerde gebaren (waar dit project nu ook mee werkt) is dat geen probleem; voor training op gebaren-in-zinsverband moet apart met Radboud/UvA overlegd worden als de app commercieel wordt. Toegang tot de dataset vereist een gratis account op Global Signbank.

Verplichte naamsvermelding bij gebruik (releases van de dataset):

- Release 1 (2020): Crasborn, O., Bank, R., Zwitserlood, I., van der Kooij, E., Ormel, E., Ros, J., Schüller, A., de Meijer, A., van Zuilen, M., Nauta, Y. E., van Winsum, F., & Vonk, M. (2020). *NGT dataset in Global Signbank [release 1]*. Nijmegen: Radboud University, Centre for Language Studies. ISLRN: 976-021-358-388-6, DOI: 10.13140/RG.2.1.2839.1446.
- Release 2 (2025): Klomp, U., Bank, R., Crasborn, O., Gierman, L., van der Kooij, E., Manders, P., de Meijer, A., Nauta, E., Oomen, M., Ormel, E., Otterspeer, G., Pelupessy, R., de Ronde, T., Ros, J., Schüller, A., Stern, G., Venter, D., Vonk, M., van Winsum, F., Wubbolts, C., van Zuilen, M., Zwitserlood, I., & Roelofsen, F. (2025). *NGT dataset in Global Signbank [release 2]*. Amsterdam: University of Amsterdam.

## 4. De architectuurvraag: on-device, cloud, of hybride?

Op basis van hoe on-device versus cloud-inference zich in de praktijk verhouden:

- **Latency**: een gekwantiseerd landmark-model draait on-device in de orde van 5-15ms. Een cloud-call kost al snel 100-500ms alleen aan netwerk-overhead — merkbaar bij iets dat per camera-frame moet reageren tijdens het oefenen.
- **Privacy**: handen en gezicht zijn biometrische data. Zolang alleen landmark-coördinaten (getallen) het toestel verlaten — nooit ruwe camerabeelden — blijft dit AVG-vriendelijk, ongeacht of de app verder "online" is.
- **Kosten**: on-device inference heeft geen marginale kosten per gebruik; cloud-inference schaalt in kosten mee met elke oefensessie van elke gebruiker.

Conclusie: **er is geen goede reden om de frame-voor-frame herkenning naar een server te sturen.** Het model is klein genoeg om lokaal te draaien, en dat is ook nog eens sneller en privacyvriendelijker. "Niet per se offline" hoeft dus niet te betekenen "herkenning gebeurt op een server" — het betekent dat de app *daarnaast* een backend kan hebben voor alles wat geen milliseconden-latency nodig heeft:

- Curriculum-content (lessen, gebarenbibliotheek, spaced repetition — zoals Duolingo dat ook doet voor taallessen).
- Account/voortgang-synchronisatie tussen apparaten.
- **Modeldistributie**: een verbeterd classificatiemodel uitrollen zonder app-store release, gewoon door een nieuw modelbestand te downloaden.
- Optioneel, met expliciete toestemming: geanonimiseerde oefensessies verzamelen om het model continu te verbeteren (vergelijkbaar met hoe spraakherkenning-apps hun modellen bijscholen op gebruikersdata).
- Eventueel een menselijke review-laag: een NGT-moedertaalspreker/doventolk die twijfelgevallen beoordeelt, vooral belangrijk in een vroege fase omdat taalkundige correctheid iets anders is dan technische landmark-overeenkomst.

Dit is ook de architectuur die de meeste vergelijkbare producten gebruiken (zie bijv. SignAll's Ace ASL: AI-gedreven camera-herkenning met een app die verder online diensten aanbiedt).

## 5. Gefaseerd plan

**Fase 0 — Fundament (voordat er code wordt geschreven)**
- Account aanmaken op Global Signbank en de NGT-dataset (6862 openbare glossen, CC BY 4.0) verkennen/downloaden — licentie is al bevestigd (zie sectie 3), dit is nu puur uitvoering. Voor eventueel gebruik van de voorbeeldzinnen uit het Corpus NGT (CC BY-NC-SA 4.0, niet-commercieel) apart contact leggen met Radboud/UvA zodra dat relevant wordt.
- Een NGT-moedertaalspreker of doventolk betrekken als taalkundige klankbord — dit is net zo belangrijk voor "echte betrouwbaarheid" als de techniek.

**Fase 1 — iOS-pariteit (blijft volledig on-device/offline-capable) — CODE GESCHREVEN, NOG NIET GEBOUWD/GETEST**

Status (2026-09-02): alle onderdelen hieronder zijn geïmplementeerd, maar deze sessie had geen
Android SDK, geen Xcode/macOS en geen netwerktoegang om te bouwen of te testen. Alles is dus
geschreven op basis van documentatie/broncode-onderzoek, niet gecompileerd. Zie
[IOS_SETUP.md](./IOS_SETUP.md) voor het bouwstappenplan en bekende risicopunten, en meld
compile-/runtime-fouten terug zodra je op een Mac/Android Studio kunt bouwen.

- [x] Datamodellen uitgebreid: `HandPose` heeft nu `handedness`, `secondaryHandLandmarks` (tweede
  hand) en `faceBlendshapes` (non-manual/mondbeeld) - allemaal met defaults, dus backwards
  compatible met de bestaande templates.json en tests. `GestureRule` heeft twee nieuwe
  `RuleType`s: `FACE_BLENDSHAPE` en `TWO_HAND_PRESENCE`.
- [x] `StaticGestureScorer` weegt nu een eventuele tweede hand en non-manual/mondbeeld-regels mee
  (gewichten zijn MVP-startwaarden, zie het bestand voor de constanten - kalibreren zodra er
  echte data is, zie fase 2).
- [x] Android: `AndroidHandTrackerBridge` gebruikt `setNumHands(2)` en heeft een tweede,
  niet-blokkerende Face Landmarker (blendshapes aan) ernaast draaien. **Let op:** het
  `face_landmarker.task`-modelbestand ontbreekt nog in `shared/src/androidMain/assets/` - moet
  gedownload worden, zie README.md.
- [x] iOS: de `UNSUPPORTED`-stub is vervangen. Architectuurkeuze: **niet** rechtstreeks Kotlin/Native
  cinterop tegen MediaPipeTasksVision (te foutgevoelig om blind te schrijven zonder Xcode), maar
  een dunne, laag-risico Kotlin hook-laag (`IosPlatformHooks`, callback-gebaseerd i.p.v. Flow/
  coroutines) die door een Swift-implementatie in `iosApp/` wordt ingevuld. Die Swift-code is
  gebaseerd op Google's officiële MediaPipe iOS-voorbeeld-app, wat het risico op fouten kleiner
  maakt dan handgeschreven cinterop - en geeft jou bij het bouwen gewone Xcode/Swift-
  compilerfouten in plaats van cryptische Kotlin/Native cinterop-fouten.
- [ ] Xcode-project zelf aanmaken (bestond nog niet, alleen `Info.plist`), CocoaPods
  (`MediaPipeTasksVision`) koppelen, `face_landmarker.task` + `hand_landmarker.task` in de
  app-bundle krijgen, en de eerste build draaien — dit kan alleen op een Mac, zie IOS_SETUP.md.

**Update (2026-09-02, later): Android "blijft hangen op Camera starten..." — opgelost.**

Deze sessie kon (via een gekoppelde shell op jouw computer) rechtstreeks `adb`/`gradlew` gebruiken
om dit live te reproduceren en te debuggen, in plaats van alleen op geplakte logs te vertrouwen.
Bevindingen:

- De `StartPractice` → permissie-aanvraag → `HandLandmarker`-opstart-keten in
  `CoachViewModel`/`AndroidCameraPlatform` bleek zelf correct te werken (met wat extra logging
  toegevoegd ter verificatie) - dit was dus niet de oorzaak.
- De echte oorzaak: `AndroidCameraPlatform.bindCamera()` vroeg altijd `CameraSelector.DEFAULT_FRONT_CAMERA`
  op, maar de Webcam0-passthrough-camera van deze specifieke AVD ("Medium Phone API 37.1") meldt
  zich bij CameraX als **back-facing** (en is de enige camera). Daardoor gooide CameraX telkens
  `IllegalArgumentException: No available camera can be found`, wat werd gelogd maar nergens
  zichtbaar werd in de UI-status - de trackerstatus kon best op `RUNNING` staan terwijl er nooit
  een camerabeeld werd gebonden. Bij herhaalde snelle her-bind-pogingen (elke recompositie van
  `CameraPreview`) kon dit er in sommige gevallen toe leiden dat de app om andere redenen op
  `STARTING` bleef hangen.
- Fix: `bindCamera()` valt nu terug op de eerst beschikbare camera (`cameraProvider
  .availableCameraInfos.firstOrNull()`) als de voorkeurscamera (front/back) niet gevonden wordt,
  in plaats van stilzwijgend te falen. Getest: schone herinstallatie + volledige
  gebaar-selecteren → Start oefenen-flow doorloopt nu betrouwbaar STOPPED → STARTING → RUNNING,
  met "bindCamera: CameraX bound successfully (fallback camera)" in de logs.
- Bekende kleine vervolgpunt (niet blokkerend): omdat de fallback-camera niet per se front-facing
  is, kan het voorbeeldbeeld gespiegeld zijn t.o.v. wat je zou verwachten - puur cosmetisch, geen
  functionele blocker voor handtracking.

**Update (2026-09-02, nog later): tweede bug + omgevingsprobleem gevonden en opgelost.**

Na de fix hierboven bleef het scherm alsnog zwart. Twee vervolgoorzaken gevonden:

1. **Race tussen twee bindCamera()-aanroepen.** `attachPreview()` (zodra de preview-view klaar
   is) en de permissie-flow (zodra toestemming gegeven wordt) riepen allebei `bindCamera()` aan,
   en omdat `ProcessCameraProvider.getInstance()` async is, kon de tweede aanroep net klaar zijn
   nádat de eerste al succesvol had gebonden - waarna zijn `unbindAll()` de zojuist gestarte
   preview weer afbrak (zichtbaar als "Detaching [Preview, ImageAnalysis]" direct na "bound
   successfully"). **Fix:** een `bindInFlight`-guard in `AndroidCameraPlatform.bindCamera()` zodat
   er nooit twee bind-pogingen tegelijk lopen.
2. **Vastgelopen camera-service in de emulator zelf** (geen app-bug). Ook na fix 1 bleef Android's
   `CameraService::connect` de verbinding weigeren met `"Too many other clients connecting"`,
   terwijl `dumpsys media.camera` liet zien dat er `Active Camera Clients: []` waren (dus niemand
   hield de camera echt vast). Uit de service-event-log bleek dat dit al minstens 15 minuten en 8
   app-herstarts aan de gang was, sinds een `Binder died unexpectedly`-moment (veroorzaakt door
   een abrupte `am force-stop` tijdens een actieve camerasessie eerder in deze debug-sessie) - een
   interne client-teller in de camera-HAL van de emulator die niet meer klopte. Dit is dus **geen
   probleem in de app-code**, en ook niet iets dat met Razer-software te maken had (dat is
   getest/uitgesloten). **Fix:** de emulator zelf herstarten (`adb emu kill` + opnieuw opstarten),
   wat de interne HAL-state resette. Bevestigd werkend na herstart, zowel met de Webcam0-camera
   als met de "Emulated" testcamera.

**Praktische les voor vervolg-testsessies:** als de camera weer vastloopt na veel snel
achter-elkaar herstarten van de app tijdens het testen (vooral met `force-stop` midden in een
camerasessie), eerst de emulator zelf herstarten voordat je verder in de app-code gaat zoeken -
`adb shell dumpsys media.camera` laat zien of er recente "REJECT ... Too many other clients"-
regels zijn zonder bijbehorende succesvolle "CONNECT".

**Fase 2 — Betrouwbaarheid: van 1 template naar getraind model**
- Voorlopig, als tussenstap die weinig kost: per gebaar meerdere voorbeeldopnames vastleggen (i.p.v. 1 template) en tegen het dichtstbijzijnde voorbeeld matchen (multi-template/k-NN) — al een stuk robuuster dan nu, zonder dat er al een trainingspijplijn hoeft te staan.
- Parallel: een lichtgewicht landmark-sequence classifier trainen (kleine transformer of GRU, zoals in het aangehaalde onderzoek) zodra er voldoende data is (Corpus NGT/Global Signbank plus eigen verzamelde oefensessies). Geëxporteerd als klein on-device model (bijv. LiteRT/TFLite), dus blijft snel en werkt ook zonder verbinding.
- De huidige rule-based scorer laten bestaan als vangnet voor nieuwe gebaren die nog geen trainingsdata hebben.

**Fase 3 — Backend (het "hoeft niet offline"-deel)**
- Lichte backend voor curriculum, accounts/voortgang-sync en modeldistributie. Kotlin/Ktor sluit qua stack het beste aan bij het bestaande KMP-project; een BaaS zoals Supabase/Firebase is een snellere weg als tempo boven stack-consistentie gaat — dit is een concrete keuze om samen te maken (zie open vragen hieronder).
- Bewust géén real-time frame-herkenning naar de server sturen (zie sectie 4).
- Optioneel: een opt-in feedbackloop waarbij (geanonimiseerde) oefensessies worden gebruikt om het model te blijven verbeteren, plus een lichte moderatie/review-laag voor twijfelgevallen.

**Fase 4 — Validatie**
- Kalibratie en testen op diverse handen, huidtinten, belichting en cameratypes (README noemt dit terecht al als open punt voor de bestaande 4 gebaren — geldt des te meer zodra de vocabulaire groeit).
- Taalkundige validatie van scoring-regels door NGT-moedertaalsprekers, niet alleen technische accuracy-metrics.

## 6. Open beslissingen voor jou

- **Backend-technologie**: eigen Ktor-server (consistent met de KMP-stack, meer controle) versus een BaaS zoals Supabase/Firebase (sneller op te zetten, minder eigen infrastructuur-onderhoud)?
- **Toegang tot Corpus NGT/Global Signbank**: opgelost qua licentie — CC BY 4.0 voor de 6862 losse glossen (commercieel gebruik toegestaan met naamsvermelding, zie sectie 3), CC BY-NC-SA 4.0 voor de voorbeeldzinnen uit de corpus (niet-commercieel). Volgende stap is praktisch: een account aanmaken op Global Signbank en de dataset downloaden/verkennen — wil je dat ik dat voorbereid, of doe je de account-aanmaak zelf omdat daar mogelijk persoonlijke/institutionele gegevens bij horen?
- **Eigen trainingsdata nu al verzamelen**: beginnen met het opnemen van meerdere signers voor de bestaande 4 gebaren (los van of/wanneer de Corpus NGT-toegang rond is), zodat de multi-template-stap in Fase 2 eerder kan starten?
- **Tempo versus schaal**: eerst iOS-pariteit + mond-tracking shippen met de bestaande rule-based scorer (Fase 1 alleen), of meteen doorpakken naar een getraind model (Fase 1+2 samen) voordat er een nieuwe versie uitgaat?
