"""
Orchestreert de volledige ASL-content-pijplijn (taak #55): matched_clips.json (uit
filter_glosses.py) -> MediaPipe hand-landmarks -> `gesture_samples`.

Gebruik:
    # Stap 1 - alleen lokaal verwerken en wegschrijven, NIETS naar Supabase (aanrader eerst):
    python3 run_pipeline.py --matches matched_clips.json --hand-model <pad>/hand_landmarker.task \
        --dry-run --out results.json

    # Stap 2 - na handmatige controle van results.json, echt uploaden:
    python3 run_pipeline.py --matches matched_clips.json --hand-model <pad>/hand_landmarker.task \
        --upload

Elke geüploade rij komt als 'status=pending' binnen (server-side trigger) en verschijnt dus
gewoon in het bestaande admin-reviewscherm - er is bewust geen bypass die rechtstreeks
'approved' zet.
"""
from __future__ import annotations

import argparse
import dataclasses
import json
from pathlib import Path

from extract_pose import extract_clip
from upload_sample import upload_clip_result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matches", required=True, type=Path, help="output van filter_glosses.py")
    parser.add_argument("--hand-model", required=True, type=Path,
                         help="pad naar shared/src/androidMain/assets/hand_landmarker.task")
    parser.add_argument("--out", type=Path, default=Path("results.json"))
    parser.add_argument("--upload", action="store_true", help="daadwerkelijk naar Supabase uploaden")
    parser.add_argument("--dry-run", action="store_true", help="alleen lokaal verwerken (default als --upload ontbreekt)")
    parser.add_argument("--sign-language", default="asl")
    args = parser.parse_args()

    if not args.hand_model.exists():
        raise SystemExit(f"hand_landmarker.task niet gevonden op {args.hand_model}")

    matches: dict[str, list[dict]] = json.loads(args.matches.read_text(encoding="utf-8"))

    all_results = []
    failures = []

    for gesture_id, clips in matches.items():
        for clip_info in clips:
            video_path = Path(clip_info["video_path"])
            if not video_path.exists():
                failures.append({"gesture_id": gesture_id, "video_path": str(video_path), "reason": "video niet gevonden"})
                continue
            print(f"[{gesture_id}] verwerk {video_path.name} ...")
            try:
                result = extract_clip(video_path, gesture_id, args.hand_model)
            except Exception as exc:  # noqa: BLE001 - we willen doorgaan met de rest van de batch
                failures.append({"gesture_id": gesture_id, "video_path": str(video_path), "reason": str(exc)})
                continue

            if result is None:
                failures.append({"gesture_id": gesture_id, "video_path": str(video_path), "reason": "geen hand gedetecteerd in enig frame"})
                continue

            print(f"  -> {result.frame_count} frames met hand, handedness={result.handedness}")
            all_results.append(result)

            if args.upload:
                upload_clip_result(result, sign_language=args.sign_language)
                print(f"  -> geüpload als pending gesture_samples-rij")

    # Preview-JPEGs niet meeschrijven naar results.json (te groot/onleesbaar) - alleen de
    # landmark-data + metadata, zodat je die eerst rustig kunt inspecteren.
    serializable = [
        {k: v for k, v in dataclasses.asdict(r).items() if k != "preview_frames_jpeg"}
        for r in all_results
    ]
    args.out.write_text(json.dumps(serializable, indent=2), encoding="utf-8")

    print(f"\nKlaar: {len(all_results)} samples verwerkt, {len(failures)} mislukt.")
    print(f"Resultaten (zonder preview-jpegs): {args.out}")
    if failures:
        fail_path = args.out.with_name(args.out.stem + "_failures.json")
        fail_path.write_text(json.dumps(failures, indent=2), encoding="utf-8")
        print(f"Mislukte clips: {fail_path}")
    if not args.upload:
        print("\n(--upload niet meegegeven: er is NIETS naar Supabase geschreven. "
              "Controleer results.json en draai daarna opnieuw met --upload.)")


if __name__ == "__main__":
    main()
