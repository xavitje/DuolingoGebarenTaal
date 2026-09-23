"""
Filtert de ASL Citizen data-CSV (train/val/test) op de 20 ASL-basiswoorden die het leerpad al
verwacht (zie AslFallbackCurriculum in CurriculumRepository.kt) en kiest per woord een klein
aantal clips om te verwerken.

CSV-formaat (zonder header-gebruik, kolomvolgorde vastgesteld uit de officiële baseline-code
microsoft/ASL-citizen-code/I3D/aslcitizen_dataset.py):
    kolom 0 = Participant ID
    kolom 1 = Video file name (relatief t.o.v. de 'videos/'-map in de dataset)
    kolom 2 = Gloss (de ASL-woordlabel, bv. "HELLO")

Gebruik:
    python3 filter_glosses.py --csv <pad-naar-splits.csv> --videos-dir <pad-naar-videos/> \
        --out matched_clips.json --per-word 3

Het script drukt ook alle glosses af die NIET matchen maar er dicht bij liggen (voor handmatige
controle - ASL Citizen-glosses zijn hoofdletter-Engelse mnemonics en spellen een woord soms
net anders dan onze curriculum-id, bv. "FINISH" i.p.v. "FINISHED").
"""
from __future__ import annotations

import argparse
import csv
import json
import re
from collections import defaultdict
from pathlib import Path

# gesture_id (zoals in AslFallbackCurriculum) -> mogelijke ASL Citizen-gloss-varianten.
# De eerste match wint; voeg varianten toe zodra je de echte glosslijst uit de CSV hebt gezien.
TARGET_WORDS: dict[str, list[str]] = {
    "asl_hello": ["HELLO", "HI"],
    "asl_please": ["PLEASE"],
    "asl_thankyou": ["THANK-YOU", "THANKYOU", "THANKS", "THANK YOU"],
    "asl_sorry": ["SORRY"],
    "asl_yes": ["YES"],
    "asl_no": ["NO"],
    "asl_help": ["HELP"],
    "asl_more": ["MORE"],
    "asl_again": ["AGAIN", "REPEAT"],
    "asl_finished": ["FINISH", "FINISHED", "DONE"],
    "asl_eat": ["EAT", "FOOD"],
    "asl_drink": ["DRINK"],
    "asl_water": ["WATER"],
    "asl_want": ["WANT"],
    "asl_like": ["LIKE"],
    "asl_love": ["LOVE"],
    "asl_happy": ["HAPPY"],
    "asl_sad": ["SAD"],
    "asl_friend": ["FRIEND"],
    "asl_play": ["PLAY"],
}


def _normalize(gloss: str) -> str:
    return re.sub(r"[^A-Z]", "", gloss.upper())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True, type=Path, help="pad naar de ASL Citizen splits-CSV")
    parser.add_argument("--videos-dir", required=True, type=Path, help="map met de .mp4-bestanden")
    parser.add_argument("--out", required=True, type=Path, help="output JSON met matches")
    parser.add_argument("--per-word", type=int, default=3, help="max aantal clips per woord")
    args = parser.parse_args()

    variant_lookup: dict[str, str] = {}
    for gesture_id, variants in TARGET_WORDS.items():
        for v in variants:
            variant_lookup[_normalize(v)] = gesture_id

    matches: dict[str, list[dict]] = defaultdict(list)
    seen_glosses: set[str] = set()

    with args.csv.open(newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)  # header overslaan (zelfde als de officiële dataset-loader)
        for row in reader:
            if len(row) < 3:
                continue
            participant, fname, gloss = row[0].strip(), row[1].strip(), row[2].strip()
            seen_glosses.add(gloss)
            gesture_id = variant_lookup.get(_normalize(gloss))
            if gesture_id is None:
                continue
            if len(matches[gesture_id]) >= args.per_word:
                continue
            video_path = args.videos_dir / fname
            matches[gesture_id].append({
                "gesture_id": gesture_id,
                "gloss": gloss,
                "participant": participant,
                "video_path": str(video_path),
                "exists": video_path.exists(),
            })

    args.out.write_text(json.dumps(matches, indent=2), encoding="utf-8")

    total = sum(len(v) for v in matches.values())
    print(f"Gematcht: {total} clips over {len(matches)}/{len(TARGET_WORDS)} woorden -> {args.out}")
    missing = [g for g in TARGET_WORDS if g not in matches]
    if missing:
        print(f"GEEN match gevonden voor: {missing}")
        print("Controleer de echte gloss-spelling in de CSV, bv. met:")
        print("  python3 -c \"import csv; print(sorted(set(r[2] for r in csv.reader(open('%s')))))\"" % args.csv)


if __name__ == "__main__":
    main()
