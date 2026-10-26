package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.xavitje.signcoach.CoachScreen
import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.presentation.CoachViewModel
import io.github.xavitje.signcoach.presentation.LearnViewModel
import io.github.xavitje.signcoach.ui.common.PlatformBackHandler
import io.github.xavitje.signcoach.ui.theme.Coral
import kotlinx.coroutines.launch

/**
 * Volledige overlay voor één les: eerst [GestureDemoScreen] ("kijk eerst hoe het moet"), daarna
 * de oefening. Heeft het gebaar een herkenningstemplate, dan is dat de camera-oefening
 * ([CoachScreen]); zo niet, dan de zelfcheck ([SelfCheckScreen]) — zodat ook lessen met alleen
 * een demovideo te halen zijn. [onBack] wist de selectie in [LearnViewModel] waardoor de hele
 * overlay verdwijnt. De systeem-terugknop sluit de les (en niet de app).
 */
@Composable
fun LessonOverlay(
    gesture: CurriculumGesture?,
    coachViewModel: CoachViewModel?,
    learnViewModel: LearnViewModel,
    /** False = deze leerling oefent zonder camera (ingesteld door de docent): altijd de zelfcheck. */
    useCamera: Boolean = true,
    onBack: () -> Unit,
) {
    var showingDemo by remember(gesture?.id) { mutableStateOf(true) }
    var selfCheckCompleted by remember(gesture?.id) { mutableStateOf(false) }
    var selfCheckXp by remember(gesture?.id) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    PlatformBackHandler(enabled = gesture != null, onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            gesture == null -> Unit
            showingDemo -> GestureDemoScreen(
                gestureName = gesture.name,
                demoVideoUrl = gesture.demoVideoUrl,
                onStartPractice = { showingDemo = false },
                onClose = onBack,
            )
            gesture.hasTemplate && useCamera -> if (coachViewModel != null) {
                CoachScreen(
                    viewModel = coachViewModel,
                    onBack = onBack,
                    onNext = if (learnViewModel.hasNextLesson(gesture)) {
                        { learnViewModel.openNextLesson(gesture) }
                    } else null,
                )
            } else {
                // Het CoachViewModel wordt in MainScreen net na de selectie aangemaakt.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
            }
            else -> SelfCheckScreen(
                gestureName = gesture.name,
                completed = selfCheckCompleted,
                earnedXp = selfCheckXp,
                onDone = {
                    scope.launch {
                        selfCheckXp = learnViewModel.completeWithSelfCheck(gesture)
                        selfCheckCompleted = true
                    }
                },
                onWatchAgain = { showingDemo = true },
                onClose = onBack,
                onNext = if (learnViewModel.hasNextLesson(gesture)) {
                    { learnViewModel.openNextLesson(gesture) }
                } else null,
            )
        }
    }
}
