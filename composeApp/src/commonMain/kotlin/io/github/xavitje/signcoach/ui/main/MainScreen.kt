package io.github.xavitje.signcoach.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.xavitje.signcoach.CoachScreen
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.repository.StaticTemplateRepository
import io.github.xavitje.signcoach.domain.coach.CoachIntent
import io.github.xavitje.signcoach.platform.PlatformCameraController
import io.github.xavitje.signcoach.presentation.AdminViewModel
import io.github.xavitje.signcoach.presentation.AuthViewModel
import io.github.xavitje.signcoach.presentation.CoachViewModel
import io.github.xavitje.signcoach.presentation.LearnViewModel
import io.github.xavitje.signcoach.ui.common.PlatformBackHandler

import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

private enum class MainTab {
    LEARN, PROGRESS, PROFILE
}

/**
 * Hoofd-scherm met bottom navigation (3 tabs): Leren, Voortgang, Profiel.
 *
 * De Leren-tab toont [LearnScreen] (curriculum/leerpad). Zodra de gebruiker een les selecteert
 * schuift [CoachScreen] als overlay omhoog — zo voelt het als een geneste navigatie zonder dat
 * we een extra NavHost nodig hebben. De [CoachViewModel] wordt aangemaakt zodra het geselecteerde
 * gebaar een template heeft en wordt weggegooid zodra de gebruiker teruggaat.
 */
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    sharedTemplates: List<SignTemplate>,
    onNavigateToAdmin: () -> Unit,
    onNavigateToContribute: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    signLanguage: String = "ngt",
    onNavigateToSignLanguagePicker: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(MainTab.LEARN) }

    // LearnViewModel leeft op MainScreen-niveau zodat het leerpad bewaard blijft bij tab-wissels.
    val learnViewModel = remember { LearnViewModel() }
    val learnState by learnViewModel.state.collectAsState()

    // ProgressViewModel leeft ook op MainScreen-niveau
    val progressViewModel = remember { io.github.xavitje.signcoach.presentation.ProgressViewModel() }

    // CoachViewModel wordt alleen aangemaakt als er een les geselecteerd is met een template.
    var coachViewModel by remember { mutableStateOf<CoachViewModel?>(null) }

    // Laad het curriculum zodra sharedTemplates wijzigt. BELANGRIJK: niet gaten op
    // "sharedTemplates.isNotEmpty()" — dat was hier lange tijd veilig omdat er altijd minstens de
    // 4 gebundelde NGT-templates waren, maar sinds gebarentalen zonder eigen content (bv. ASL,
    // waarvoor de content-pijplijn nog niet gebouwd is) een LEGITIEM lege lijst kunnen opleveren,
    // zorgde die guard ervoor dat loadCurriculum() nooit werd aangeroepen — en dus dat
    // LearnViewModel.state.isLoading voor altijd op de standaardwaarde `true` bleef staan (de
    // Leren-tab bleef oneindig laden). loadCurriculum() met een lege lijst is prima: het toont dan
    // gewoon het leerpad met alles vergrendeld, in plaats van voor altijd te blijven laden.
    LaunchedEffect(sharedTemplates) {
        learnViewModel.loadCurriculum(sharedTemplates)
    }

    // Maak/vernieuw CoachViewModel wanneer een les geselecteerd wordt.
    LaunchedEffect(learnState.selectedGesture) {
        val gesture = learnState.selectedGesture
        if (gesture != null && gesture.hasTemplate) {
            // Filter de templates op het geselecteerde gebaar zodat CoachScreen alleen
            // die ene les toont.
            val filtered = sharedTemplates.filter { it.id == gesture.id }
            val vm = CoachViewModel(
                repository = StaticTemplateRepository(filtered),
                cameraController = PlatformCameraController(),
            )
            vm.dispatch(CoachIntent.LoadTemplates)
            coachViewModel = vm
        } else {
            coachViewModel?.clear()
            coachViewModel = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { coachViewModel?.clear() }
    }

    // Terugknop op een ander tabblad gaat eerst terug naar Leren in plaats van de app te sluiten.
    // (In een les vangt LessonOverlay de terugknop zelf af.)
    PlatformBackHandler(enabled = selectedTab != MainTab.LEARN && learnState.selectedGesture == null) {
        selectedTab = MainTab.LEARN
    }

    val navItemColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
        selectedIconColor = io.github.xavitje.signcoach.ui.theme.Coral,
        selectedTextColor = io.github.xavitje.signcoach.ui.theme.Coral,
        unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
        unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = io.github.xavitje.signcoach.ui.theme.Plum) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.LEARN,
                    onClick = { selectedTab = MainTab.LEARN },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(Res.string.tab_learn)) },
                    label = { Text(stringResource(Res.string.tab_learn)) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROGRESS,
                    onClick = { selectedTab = MainTab.PROGRESS },
                    icon = { Icon(Icons.Default.Star, contentDescription = stringResource(Res.string.tab_progress)) },
                    label = { Text(stringResource(Res.string.tab_progress)) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILE,
                    onClick = { selectedTab = MainTab.PROFILE },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = stringResource(Res.string.tab_profile)) },
                    label = { Text(stringResource(Res.string.tab_profile)) },
                    colors = navItemColors,
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Tab-inhoud ───────────────────────────────────────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TabContent",
            ) { tab ->
                when (tab) {
                    MainTab.LEARN -> LearnScreen(
                        viewModel = learnViewModel,
                        progressViewModel = progressViewModel,
                        onStartLesson = { gestureId ->
                            val gesture = learnState.units
                                .flatMap { it.gestures }
                                .find { it.id == gestureId }
                            if (gesture != null) learnViewModel.selectGesture(gesture)
                        },
                        onContribute = onNavigateToContribute,
                    )
                    MainTab.PROGRESS -> {
                        LaunchedEffect(Unit) { progressViewModel.loadProgress() }
                        ProgressScreen(viewModel = progressViewModel)
                    }
                    MainTab.PROFILE -> ProfileScreen(
                        authViewModel = authViewModel,
                        progressViewModel = progressViewModel,
                        onNavigateToAdmin = onNavigateToAdmin,
                        onNavigateToContribute = onNavigateToContribute,
                        onNavigateToPrivacy = onNavigateToPrivacy,
                        signLanguage = signLanguage,
                        onNavigateToSignLanguagePicker = onNavigateToSignLanguagePicker,
                    )
                }
            }

            // ── CoachScreen overlay (schuift omhoog als een les geselecteerd is) ──
            AnimatedVisibility(
                // Elke geopende les toont de overlay — ook zonder herkenningstemplate (dan volgt
                // na de demo de zelfcheck). Voorheen opende zo'n les helemaal niet.
                visible = learnState.selectedGesture != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                LessonOverlay(
                    gesture = learnState.selectedGesture,
                    coachViewModel = coachViewModel,
                    learnViewModel = learnViewModel,
                    onBack = { learnViewModel.clearSelection() },
                )
            }
        }
    }
}
