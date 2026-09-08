package com.example.signcoach.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.signcoach.AdminScreen
import com.example.signcoach.ContributionScreen
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.repository.AssetTemplateRepository
import com.example.signcoach.data.repository.SyncingTemplateRepository
import com.example.signcoach.platform.PlatformCameraController
import com.example.signcoach.presentation.AdminViewModel
import com.example.signcoach.presentation.AuthStatus
import com.example.signcoach.presentation.AuthViewModel
import com.example.signcoach.presentation.ContributionViewModel
import com.example.signcoach.resources.Res
import com.example.signcoach.ui.auth.LoginScreen
import com.example.signcoach.ui.auth.RegisterScreen
import com.example.signcoach.ui.main.MainScreen
import org.jetbrains.compose.resources.ExperimentalResourceApi

private object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val MAIN = "main"
    const val ADMIN = "admin"
    const val CONTRIBUTE = "contribute"
}

/**
 * Hoofd-navigatiegraph. Bewaakt de auth-staat via [AuthViewModel] en stuurt naar de juiste
 * flow. De CoachViewModel en templates worden in [MainScreen] beheerd zodat hun state niet
 * verloren gaat bij tab-wissels.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun AppNavigation(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsState()
    val adminViewModel = remember { AdminViewModel() }

    // Templates gedeeld laden op root-niveau voor zowel CoachScreen als ContributionScreen.
    var sharedTemplates by remember { mutableStateOf<List<SignTemplate>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            val json = Res.readBytes("files/templates.json").decodeToString()
            val bundled = AssetTemplateRepository(json)
            SyncingTemplateRepository(bundled).syncAndLoad()
        }.onSuccess { templates ->
            sharedTemplates = templates
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        },
    ) {
        // Splash: sessie-check loopt in AuthViewModel.init{}; redirect zodra die klaar is.
        composable(Routes.SPLASH) {
            SplashGate(authState.status) { isLoggedIn ->
                if (isLoggedIn) {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                } else {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                authViewModel = authViewModel,
                adminViewModel = adminViewModel,
                sharedTemplates = sharedTemplates,
                onNavigateToAdmin = { navController.navigate(Routes.ADMIN) },
                onNavigateToContribute = { navController.navigate(Routes.CONTRIBUTE) },
            )
        }

        composable(Routes.ADMIN) {
            AdminScreen(
                viewModel = adminViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CONTRIBUTE) {
            val contributionViewModel = remember(sharedTemplates) {
                if (sharedTemplates.isEmpty()) null
                else ContributionViewModel(cameraController = PlatformCameraController()).also {
                    it.setAvailableGestures(sharedTemplates)
                }
            }
            if (contributionViewModel != null) {
                DisposableEffect(Unit) {
                    onDispose { /* ContributionViewModel heeft geen clear nodig */ }
                }
                ContributionScreen(
                    viewModel = contributionViewModel,
                    onBack = { navController.popBackStack() },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Automatische redirect naar Login zodra de gebruiker uitlogt.
    LaunchedEffect(authState.status) {
        if (authState.status == AuthStatus.LOGGED_OUT) {
            val current = navController.currentDestination?.route
            if (current != null && current != Routes.LOGIN && current != Routes.REGISTER && current != Routes.SPLASH) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Redirect naar MAIN na succesvol inloggen.
    LaunchedEffect(authState.status) {
        if (authState.status == AuthStatus.LOGGED_IN) {
            val current = navController.currentDestination?.route
            if (current == Routes.LOGIN || current == Routes.REGISTER) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
}

@Composable
private fun SplashGate(
    status: AuthStatus,
    onResolved: (isLoggedIn: Boolean) -> Unit,
) {
    when (status) {
        AuthStatus.LOADING -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthStatus.LOGGED_IN -> onResolved(true)
        AuthStatus.LOGGED_OUT -> onResolved(false)
    }
}
