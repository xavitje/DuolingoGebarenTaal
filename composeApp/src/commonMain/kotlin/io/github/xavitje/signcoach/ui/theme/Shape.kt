package io.github.xavitje.signcoach.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─── SignCoach Redesign — radiusschaal ─────────────────────────────────────
// sm 8 · md 14 · lg 18 · xl 24 · pill 999 — zie het tokenoverzicht in de
// SignCoach Redesign-artifact ("Ruimte, vorm & beweging").
val RadiusSm  = RoundedCornerShape(8.dp)
val RadiusMd  = RoundedCornerShape(14.dp)
val RadiusLg  = RoundedCornerShape(18.dp)
val RadiusXl  = RoundedCornerShape(24.dp)
val RadiusPill = RoundedCornerShape(50)

val SignCoachShapes = Shapes(
    extraSmall = RadiusSm,
    small      = RadiusMd,
    medium     = RadiusLg,
    large      = RadiusXl,
    extraLarge = RoundedCornerShape(28.dp),
)
