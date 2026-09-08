package com.example.signcoach.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.signcoach.CoachScreen
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.repository.StaticTemplateRepository
import com.example.signcoach.domain.coach.CoachIntent
import com.example.signcoach.platform.PlatformCameraController
import com.example.signcoach.presentation.AdminViewModel
import com.example.signcoach.presentation.AuthViewModel
import com.example.signcoach.presentation.CoachViewModel

private enum class MainTab(val label: String) {
    LEARN("Leren"), PROGRESS("Voortgang"), PROFILE("Profiel")
}

/**
 * Hoofd-scherm met bottom navigation (3 tabs): Leren, Voortgang, Profiel.
 * De CoachViewModel leeft hier zodat camera/tracker-state behouden blijft bij tab-wissels.
 */
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    sharedTemplates: List<SignTemplate>,
    onNavigateToAdmin: () -> Unit,
    onNavigateToContribute: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(MainTab.LEARN) }
    var coachViewModel by remember { mutableStateOf<CoachViewModel?>(null) }

    // Maak CoachViewModel aan zodra templates beschikbaar zijn.
    LaunchedEffect(sharedTemplates) {
        if (sharedTemplates.isNotEmpty() && coachViewModel == null) {
            val vm = CoachViewModel(
                repository = StaticTemplateRepository(sharedTemplates),
                cameraController = PlatformCameraController(),
            )
            vm.dispatch(CoachIntent.LoadTemplates)
            coachViewModel = vm
        }
    }

    DisposableEffect(Unit) {
        onDispose { coachViewModel?.clear() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.LEARN,
                    onClick = { selectedTab = MainTab.LEARN },
                    icon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Leren",
                        )
                    },
                    label = { Text("Leren") },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROGRESS,
                    onClick = { selectedTab = MainTab.PROGRESS },
                    icon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Voortgang",
                        )
                    },
                    label = { Text("Voortgang") },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILE,
                    onClick = { selectedTab = MainTab.PROFILE },
                    icon = {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Profiel",
                        )
                    },
                    label = { Text("Profiel") },
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TabContent",
            ) { tab ->
                when (tab) {
                    MainTab.LEARN -> {
                        val vm = coachViewModel
                        if (vm != null) {
                            CoachScreen(
                                viewModel = vm,
                                onContribute = onNavigateToContribute,
                            )
                        }
                    }
                    MainTab.PROGRESS -> ProgressScreen()
                    MainTab.PROFILE -> ProfileScreen(
                        authViewModel = authViewModel,
                        onNavigateToAdmin = onNavigateToAdmin,
                    )
                }
            }
        }
    }
}
