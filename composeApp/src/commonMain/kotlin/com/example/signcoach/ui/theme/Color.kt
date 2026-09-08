package com.example.signcoach.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Primaire kleuren: diep petrolblauw/indigo
val Indigo700 = Color(0xFF2A3B5C)
val Indigo600 = Color(0xFF344D78)
val Indigo500 = Color(0xFF3D6094)
val Indigo200 = Color(0xFF9BAFD1)
val Indigo100 = Color(0xFFD0DAEE)
val Indigo50  = Color(0xFFEDF1F8)

// Accent: warm amber/koraal
val Amber500  = Color(0xFFE8974A)
val Amber400  = Color(0xFFEFAD6E)
val Amber700  = Color(0xFFC47A30)
val Coral500  = Color(0xFFE86B4A)

// Neutrals
val Surface900 = Color(0xFF0F1923)
val Surface800 = Color(0xFF1A2535)
val Surface50  = Color(0xFFF7F9FC)
val OnSurface  = Color(0xFFE8EDF5)

// Licht kleurschema
val LightColorScheme = lightColorScheme(
    primary            = Indigo700,
    onPrimary          = Color.White,
    primaryContainer   = Indigo100,
    onPrimaryContainer = Indigo700,
    secondary          = Amber500,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFFCEDD8),
    onSecondaryContainer = Amber700,
    tertiary           = Coral500,
    background         = Surface50,
    onBackground       = Color(0xFF0F1923),
    surface            = Color.White,
    onSurface          = Color(0xFF1A2535),
    surfaceVariant     = Indigo50,
    onSurfaceVariant   = Color(0xFF4A5568),
    outline            = Indigo200,
    error              = Color(0xFFBA1A1A),
    onError            = Color.White,
)

// Donker kleurschema
val DarkColorScheme = darkColorScheme(
    primary            = Indigo200,
    onPrimary          = Indigo700,
    primaryContainer   = Indigo600,
    onPrimaryContainer = Indigo100,
    secondary          = Amber400,
    onSecondary        = Color(0xFF5A3200),
    secondaryContainer = Amber700,
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary           = Coral500,
    background         = Surface900,
    onBackground       = OnSurface,
    surface            = Surface800,
    onSurface          = OnSurface,
    surfaceVariant     = Color(0xFF243247),
    onSurfaceVariant   = Indigo200,
    outline            = Color(0xFF4A5E7A),
    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005),
)
