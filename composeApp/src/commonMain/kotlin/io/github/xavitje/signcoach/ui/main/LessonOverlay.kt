package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.xavitje.signcoach.CoachScreen
import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.presentation.CoachViewModel

/**
 * Volledige overlay die eerst [GestureDemoScreen] toont (referentievideo "kijk eerst hoe het
 * moet") en pas daarna [CoachScreen] (de camera-oefening) — in plaats van direct de camera te
 * openen. Wordt als slide-up animatie getoond als de gebruiker een les aanklikt in [LearnScreen].
 * [onBack] wist de selectie in [LearnViewModel] waardoor de hele overlay verdwijnt.
 *
 * De demo-stap wordt hier lokaal bijgehouden (niet in [CoachViewModel]/de reducer) zodat de
 * bestaande oefen-statemachine ongemoeid blijft — dit is puur een scherm ervóór.
 */
@Composable
fun LessonOverlay(
    gesture: CurriculumGesture?,
    coachViewModel: CoachViewModel,
    onBack: () -> Unit,
) {
    // Per nieuw gebaar begint de overlay weer bij de demo — resetten op de gebaar-id zodat
    // een tweede poging aan hetzelfde gebaar niet opnieuw de video toont (alleen bij wisselen
    // van gebaar), maar wél opnieuw wanneer je terug naar het pad gaat en een ander gebaar kiest.
    var showingDemo by remember(gesture?.id) { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (showingDemo && gesture != null) {
            GestureDemoScreen(
                gestureName = gesture.name,
                demoVideoUrl = gesture.demoVideoUrl,
                onStartPractice = { showingDemo = false },
                onClose = onBack,
            )
        } else {
            // TopAppBar staat hier los zodat CoachScreen zijn eigen Scaffold kan hebben
            // maar we toch een consistente terugknop tonen.
            CoachScreen(
                viewModel = coachViewModel,
                onBack = onBack,
            )
        }
    }
}
