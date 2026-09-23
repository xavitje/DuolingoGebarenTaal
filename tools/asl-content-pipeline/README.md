# ASL-content-pijplijn (taak #55)

Verwerkt vooraf opgenomen ASL-videoclips tot `gesture_samples`-rijen, in exact hetzelfde
formaat als een live Contribute-opname (zie `ContributionViewModel.uploadClip()` /
`GestureContributionApi.kt`). Elke geüploade rij komt als **`status = 'pending'`** binnen
(server-side trigger `force_pending_on_insert`) en verschijnt dus gewoon in het bestaande
admin-reviewscherm — er is bewust geen route die rechtstreeks content goedkeurt.

## Waarom dit nodig was

Er bestond nog geen niet-live verwerkingsweg: de app haalt handlandmarks alleen live uit de
camera (`RunningMode.LIVE_STREAM`). Dit is de eerste batch-tool die dezelfde
`hand_landmarker.task` door `RunningMode.VIDEO` haalt voor losse videobestanden, en de
resulterende landmarks op precies dezelfde manier middelt (`averageLandmarks`) en van
handedness voorziet (`majorityHandedness`) als de live app doet.

## Brondata: ASL Citizen (Microsoft Research)

Na overleg is gekozen voor **ASL Citizen** i.p.v. het aanvankelijk genoemde PopSign (dat blijkt
een lesapp te zijn, geen dataset) en i.p.v. WLASL/MS-ASL/ASL-LEX (alle drie expliciet
non-commercial gelicenseerd, met ASL-LEX zelfs zonder herbruikbare video's).

- 84.000 clips, 2.731 glosses, alledaagse signers, IRB-consent.
- Directe download, geen aanvraagformulier nodig:
  `https://download.microsoft.com/download/b/8/8/b88c0bae-e6c1-43e1-8726-98cf5af36ca4/ASL_Citizen.zip`
  (**42,8 GB** — versie 1.0, 15-10-2024)
- Licentie: Microsoft Research-licentie, **non-commercial / non-revenue-generating / research
  purposes only**. Voor commercieel gebruik: **ASL_Citizen@microsoft.com** (contact voor
  toestemming — dit is bewust nog niet gedaan; dat doe je zelf, zoals afgesproken).
- **Belangrijk:** tot die toestemming binnen is, is deze content strikt genomen alleen bedoeld
  om de pijplijn te bouwen/valideren, niet om al live te zetten in een product met inkomsten.

### Waarom ik het niet zelf kon downloaden

`download.microsoft.com` wordt zowel vanuit mijn eigen cloud-omgeving als vanuit dit
Windows-apparaat (via de bureaublad-brug) geblokkeerd door het organisatie-egress-beleid
(`blocked-by-allowlist`, bevestigd met een directe test in beide omgevingen). Hetzelfde geldt
voor `storage.googleapis.com`, `huggingface.co` en de gebruikelijke CDN's — vrijwel alleen
pypi/npm/GitHub-achtige hosts staan open. Jij moet de zip dus zelf downloaden via een gewone
browser (die loopt niet via dit beleid) en ergens neerzetten waar dit script bij kan, bv.
`D:\DuolingoGebarenTaal\tools\asl-content-pipeline\data\ASL_Citizen\`.

Het `hand_landmarker.task`-modelbestand was dit probleem niet: dat staat al gewoon in de repo
(`shared/src/androidMain/assets/hand_landmarker.task`), dus dit script gebruikt gewoon dát
bestand — gegarandeerd hetzelfde model als de live app.

## Gebruik

> **Let op de exacte mediapipe-versie hierboven (0.10.21).** De nieuwste mediapipe (1.x)
> probeert altijd een GPU/EGL-context te laden (`libEGL.so.1`) zelfs als je CPU wilt
> gebruiken, en crasht daarop als die systeembibliotheek ontbreekt en je geen root hebt om
> hem te installeren (precies de situatie in de sandbox van dit apparaat — geconstateerd en
> opgelost door te pinnen op 0.10.21, wat prima op de CPU draait zonder EGL).


```bash
pip3 install "mediapipe==0.10.21" opencv-python-headless numpy requests

# 1. Zoek de 20 basiswoorden (zie AslFallbackCurriculum) in de dataset-CSV en kies per woord
#    een paar clips. <csv> is train.csv/val.csv/test.csv uit de uitgepakte zip.
python3 filter_glosses.py \
  --csv data/ASL_Citizen/splits/train.csv \
  --videos-dir data/ASL_Citizen/videos \
  --out matched_clips.json --per-word 3

# 2. Eerst een droge run — verwerkt alles lokaal, uploadt NIETS:
python3 run_pipeline.py \
  --matches matched_clips.json \
  --hand-model ../../shared/src/androidMain/assets/hand_landmarker.task \
  --dry-run --out results.json

#    Controleer results.json: frame_count (hoeveel frames een hand vond) en handedness per
#    clip. Een frame_count van 0 (clip zit dan niet in results.json, wel in
#    results_failures.json) betekent dat MediaPipe geen hand zag — waarschijnlijk een
#    verkeerd gekozen gloss-match, controleer dan filter_glosses.py's TARGET_WORDS.

# 3. Pas als dat er goed uitziet, echt naar Supabase uploaden (komt als 'pending' binnen):
python3 run_pipeline.py \
  --matches matched_clips.json \
  --hand-model ../../shared/src/androidMain/assets/hand_landmarker.task \
  --upload
```

## Attributie

Zodra er echte ASL Citizen-content live staat, hoort er een kort "Credits"-blokje bij (zie
plan, stap 7.4) op het privacy/over-scherm: "ASL Citizen dataset — Microsoft Research /
American Society for Deaf Children, gebruikt met toestemming." (pas de exacte tekst aan zodra
de toestemmingsmail van ASL_Citizen@microsoft.com terug is — die kan aanvullende eisen
bevatten).

## Bestanden

- `extract_pose.py` — kernlogica: video → per-frame handlandmarks (MediaPipe Tasks, VIDEO-mode)
  → gemiddelde pose + meerderheids-handedness + preview-JPEGs.
- `filter_glosses.py` — matcht de ASL Citizen-CSV tegen de 20 basiswoorden.
- `upload_sample.py` — POST naar Supabase (`gesture-videos`-bucket + `gesture_samples`-insert),
  met de publieke anon-key uit `SupabaseConfig.kt` (RLS-beschermd, mag alleen `pending` aanmaken).
- `run_pipeline.py` — orchestreert bovenstaande, met een verplichte `--dry-run`-stap voor je
  `--upload` gebruikt.

Getest: de MediaPipe Tasks-API (`HandLandmarker`, `VIDEO`-mode) is end-to-end doorlopen op een
synthetische testclip zonder fouten — dit bevestigt dat de API-aanroepen kloppen voor de
geïnstalleerde mediapipe-versie. Een echte inhoudelijke validatie (klopt de output visueel met
een live opname van hetzelfde gebaar, zoals genoemd in plan-stap "Verificatie #5") kan pas
zodra er een echte clip beschikbaar is.
