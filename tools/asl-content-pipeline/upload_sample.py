"""
Uploadt één verwerkte clip (ClipResult) naar Supabase, exact zoals ContributionViewModel.
uploadClip() dat voor een live opname doet:
  1. Preview-frames naar de private 'gesture-videos'-bucket (voor de reviewer).
  2. Eén rij in `gesture_samples` (status wordt door de `force_pending_on_insert`-trigger
     server-side altijd op 'pending' gezet - deze content doorloopt dus gewoon het bestaande
     admin-reviewscherm, net als crowdsourced NGT-bijdragen).

Gebruikt de PUBLIEKE Supabase anon-key uit shared/.../data/remote/SupabaseConfig.kt - dezelfde
sleutel die al in de geshipte app zit en door Row Level Security wordt afgeschermd (mag alleen
'pending' rijen aanmaken, nooit lezen/overschrijven van reviewstatus). Dit is dus geen geheime
credential; het is de normale, publieke manier waarop de client-app al met dit project praat.
"""
from __future__ import annotations

import json
import random
import string
import sys
from dataclasses import asdict
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).parent))
from extract_pose import ClipResult  # noqa: E402

SUPABASE_URL = "https://zjedxlvdeedpkbxzqkpo.supabase.co"
SUPABASE_ANON_KEY = (
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpqZWR4bHZkZWVkcGtieHpx"
    "a3BvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg4NzYzNDIsImV4cCI6MjEwNDQ1MjM0Mn0."
    "qAs07pzMWGuQsqY1SFV2VGvs_UT0h0m6-1ftj14Lcp4"
)


def _random_id(length: int = 12) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=length))


def upload_clip_result(clip: ClipResult, sign_language: str = "asl", source_tag: str = "asl_citizen") -> None:
    headers_common = {
        "apikey": SUPABASE_ANON_KEY,
        "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
    }

    folder = f"{clip.gesture_id}/{source_tag}_{_random_id()}"

    # 1. Preview-frames (zelfde pad-conventie als uploadReviewClip: "<folder>/frame_NN.jpg")
    for i, jpeg_bytes in enumerate(clip.preview_frames_jpeg):
        path = f"{folder}/frame_{i:02d}.jpg"
        resp = requests.post(
            f"{SUPABASE_URL}/storage/v1/object/gesture-videos/{path}",
            headers={**headers_common, "Content-Type": "image/jpeg"},
            data=jpeg_bytes,
            timeout=60,
        )
        if not resp.ok:
            raise RuntimeError(f"Frame-upload mislukt voor {path}: {resp.status_code} {resp.text}")

    # 2. gesture_samples-rij (NewSampleDto-vorm - contributor_id blijft null: geen menselijke
    #    bijdrager, wel traceerbaar via video_path/source_tag welke dataset dit is).
    body = {
        "gesture_id": clip.gesture_id,
        "landmarks": clip.landmarks,
        "handedness": clip.handedness,
        "video_path": folder,
        "contributor_id": None,
        "sign_language": sign_language,
    }
    resp = requests.post(
        f"{SUPABASE_URL}/rest/v1/gesture_samples",
        headers={**headers_common, "Content-Type": "application/json", "Prefer": "return=minimal"},
        data=json.dumps(body),
        timeout=30,
    )
    if not resp.ok:
        raise RuntimeError(f"gesture_samples-insert mislukt voor {clip.gesture_id}: {resp.status_code} {resp.text}")
