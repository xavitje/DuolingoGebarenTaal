package io.github.xavitje.signcoach.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── SignCoach Redesign — kleurtokens ──────────────────────────────────────
// Speels, warm palet: koraal als merkkleur, schemer-plum voor diepte/camera,
// klaver voor gelukt, zon voor reeks & xp. Vervangt het oude indigo/amber.

val Coral      = Color(0xFFFF6B4A)
val CoralDeep  = Color(0xFFE4502F)
val CoralSoft  = Color(0xFFFFE8DC)

val Plum       = Color(0xFF2D2350)
val PlumDeep   = Color(0xFF1B1533)
val PlumMist   = Color(0xFFEDE7F7)

val Clover     = Color(0xFF2FB673)
val CloverLight = Color(0xFF6FE0A0)
val CloverSoft = Color(0xFFDFF5E9)

val Gold       = Color(0xFFFFB627)
val GoldSoft   = Color(0xFFFFF1D6)
val GoldLight  = Color(0xFFFFD37A)
val GoldDeep   = Color(0xFFE8974A)

val Cream      = Color(0xFFFFF8F0)
val Ink        = Color(0xFF241B3A)
val InkSoft    = Color(0xFF6C6383)
val LineLight  = Color(0xFFEEE3F3)

// ─── Backwards-compatible aliassen ─────────────────────────────────────────
// Bestaande schermen (badges, profiel, contributie, admin) verwijzen nog naar
// deze namen. Door ze te aliassen naar de nieuwe tokens krijgt de hele app
// automatisch het nieuwe palet zonder dat elk bestand apart aangepast hoeft.
val Indigo700 = Plum
val Indigo600 = Color(0xFF3D3168)
val Indigo500 = Color(0xFF4A3B72)
val Indigo300 = Color(0xFF9B8BC4)
val Indigo200 = Color(0xFFC9BEDD)
val Indigo100 = PlumMist
val Indigo50  = Color(0xFFF7F4FC)

val Amber500  = Gold
val Amber400  = Color(0xFFFFC966)
val Amber700  = Color(0xFFCC8A00)
val Coral500  = Coral

val Surface900 = PlumDeep
val Surface800 = Color(0xFF211A38)
val Surface50  = Cream
val OnSurface  = Color(0xFFF5F1FF)

// ─── Licht kleurschema ──────────────────────────────────────────────────────
val LightColorScheme = lightColorScheme(
    primary            = Coral,
    onPrimary          = Color.White,
    primaryContainer   = CoralSoft,
    onPrimaryContainer = CoralDeep,
    secondary          = Gold,
    onSecondary        = PlumDeep,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF7A5200),
    tertiary           = Clover,
    onTertiary         = Color.White,
    tertiaryContainer  = CloverSoft,
    onTertiaryContainer = Color(0xFF1B6B44),
    background         = Cream,
    onBackground       = Ink,
    surface            = Color.White,
    onSurface          = Ink,
    surfaceVariant     = PlumMist,
    onSurfaceVariant   = InkSoft,
    outline            = LineLight,
    error              = Color(0xFFBA1A1A),
    onError            = Color.White,
)

// ─── Donker kleurschema ─────────────────────────────────────────────────────
val DarkColorScheme = darkColorScheme(
    primary            = Coral,
    onPrimary          = PlumDeep,
    primaryContainer   = Color(0xFF3A2A34),
    onPrimaryContainer = CoralSoft,
    secondary          = Gold,
    onSecondary        = PlumDeep,
    secondaryContainer = Color(0xFF674614),
    onSecondaryContainer = GoldSoft,
    tertiary           = Clover,
    onTertiary         = PlumDeep,
    tertiaryContainer  = Color(0xFF1B3A2D),
    onTertiaryContainer = CloverSoft,
    background         = PlumDeep,
    onBackground       = Color(0xFFF5F1FF),
    surface            = Plum,
    onSurface          = Color(0xFFF5F1FF),
    surfaceVariant     = Color(0xFF2A2246),
    onSurfaceVariant   = Color(0xFFB6AAD4),
    // Zonder deze surfaceContainer-tinten vallen Cards, Surfaces met tonalElevation en dialogen
    // terug op Material's standaard-grijs (de grijze kaarten op het Profiel-scherm).
    surfaceContainerLowest  = PlumDeep,
    surfaceContainerLow     = Color(0xFF231C40),
    surfaceContainer        = Color(0xFF2A2246),
    surfaceContainerHigh    = Plum,
    surfaceContainerHighest = Color(0xFF362B5E),
    surfaceBright           = Color(0xFF3D3168),
    surfaceDim              = PlumDeep,
    // Licht genoeg om invoervelden en scheidingslijnen zichtbaar te maken op plum (was bijna
    // onzichtbaar: 0xFF382C56).
    outline            = Color(0xFF8E80B5),
    outlineVariant     = Color(0xFF4A3D6E),
    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005),
)
