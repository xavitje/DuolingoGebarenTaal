package io.github.xavitje.signcoach.navigation

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.xavitje.signcoach.AdminScreen
import io.github.xavitje.signcoach.ContributionScreen
import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.remote.ProfileApi
import io.github.xavitje.signcoach.data.repository.AssetTemplateRepository
import io.github.xavitje.signcoach.data.repository.CurriculumRepository
import io.github.xavitje.signcoach.data.repository.SyncingTemplateRepository
import io.github.xavitje.signcoach.data.repository.TemplateCache
import io.github.xavitje.signcoach.platform.PlatformCameraController
import io.github.xavitje.signcoach.presentation.AdminViewModel
import io.github.xavitje.signcoach.presentation.AuthStatus
import io.github.xavitje.signcoach.presentation.AuthViewModel
import io.github.xavitje.signcoach.presentation.ContributionViewModel
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.ui.auth.LoginScreen
import io.github.xavitje.signcoach.ui.auth.RegisterScreen
import io.github.xavitje.signcoach.ui.main.MainScreen
import io.github.xavitje.signcoach.ui.legal.PrivacyPolicyScreen
import io.github.xavitje.signcoach.ui.onboarding.ChooseSignLanguageScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

private object Routes {
    const val SPLASH = "splash"
    const val CHOOSE_SIGN_LANGUAGE = "onboarding/choose_sign_language"
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val MAIN = "main"
    const val ADMIN = "admin"
    const val CONTRIBUTE = "contribute"
    const val PRIVACY = "privacy"
    const val SIGN_LANGUAGE_PICKER = "settings/sign_language"
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
    val coroutineScope = rememberCoroutineScope()
    val profileApi = remember { ProfileApi() }

    // De actieve gebarentaal-keuze leeft hier op root-niveau (niet alleen in SessionStore) zodat
    // wijzigen in Instellingen (SIGN_LANGUAGE_PICKER) meteen het curriculum/templates opnieuw
    // laadt — SessionStore alleen is niet observeerbaar door Compose.
    var activeSignLanguage by remember { mutableStateOf(SessionStore.getSignLanguage()) }

    // Templates gedeeld laden op root-niveau voor zowel CoachScreen als ContributionScreen.
    // Herlaadt automatisch zodra activeSignLanguage wijzigt (onboarding of instellingen).
    var sharedTemplates by remember { mutableStateOf<List<SignTemplate>>(emptyList()) }
    LaunchedEffect(activeSignLanguage) {
        val language = SignLanguage.fromCode(activeSignLanguage)
        // Offline-first: toon meteen de laatst gecachete set (lokale schijf, geen netwerk-
        // wachttijd) zodat het leerpad nooit op een netwerk-rondrit hoeft te wachten — dit was
        // voorheen leeg tot de volledige sync hieronder klaar was, wat bij elke herstart (ook een
        // Android Activity-recreate, bv. na het wisselen van de app-taal) een langzame/lege
        // laadstaat gaf. De volledige sync hieronder ververst dit zodra dat lukt.
        TemplateCache.load(language)?.let { cached -> sharedTemplates = cached }
        runCatching {
            val json = Res.readBytes("files/templates.json").decodeToString()
            val bundled = AssetTemplateRepository(json)
            SyncingTemplateRepository(bundled).syncAndLoad(language)
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
                    // Eerste-installatie: nog geen taalkeuze gemaakt -> eerst de kiezer tonen,
                    // die na een keuze zelf doorstuurt naar LOGIN (zie hieronder). Een reeds
                    // afgeronde onboarding (of een teruggekeerde ingelogde gebruiker die net is
                    // uitgelogd) gaat gewoon meteen naar LOGIN zoals voorheen.
                    val destination = if (io.github.xavitje.signcoach.data.local.SessionStore.isOnboardingComplete()) {
                        Routes.LOGIN
                    } else {
                        Routes.CHOOSE_SIGN_LANGUAGE
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.CHOOSE_SIGN_LANGUAGE) {
            ChooseSignLanguageScreen(
                currentSelection = SignLanguage.fromCode(activeSignLanguage),
                onConfirm = { language ->
                    SessionStore.setSignLanguage(language.code)
                    SessionStore.setOnboardingComplete(true)
                    activeSignLanguage = language.code
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHOOSE_SIGN_LANGUAGE) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SIGN_LANGUAGE_PICKER) {
            ChooseSignLanguageScreen(
                currentSelection = SignLanguage.fromCode(activeSignLanguage),
                onBack = { navController.popBackStack() },
                onConfirm = { language ->
                    SessionStore.setSignLanguage(language.code)
                    // Best-effort: de lokale keuze is meteen leidend (offline-first), het profiel
                    // in Supabase volgt zodra er een sessie + netwerk is — geen blokkerende wachttijd.
                    val session = SessionStore.load()
                    if (session != null) {
                        coroutineScope.launch {
                            profileApi.updateSignLanguage(session.accessToken, session.userId, language.code)
                        }
                    }
                    activeSignLanguage = language.code
                    navController.popBackStack()
                },
            )
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
                onNavigateToPrivacy = { navController.navigate(Routes.PRIVACY) },
                signLanguage = activeSignLanguage,
                onNavigateToSignLanguagePicker = { navController.navigate(Routes.SIGN_LANGUAGE_PICKER) },
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN) {
            AdminScreen(
                viewModel = adminViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CONTRIBUTE) {
            // Taalbewust: de picker moet de gebaren van de ACTIEVE gebarentaal tonen (bv. de
            // ASL-basiswoorden i.p.v. het NGT-alfabet), en elke ingediende bijdrage moet met de
            // juiste sign_language in de database belanden (zie ContributionViewModel) — anders
            // is er geen manier voor gebruikers om zelf ASL-referentiemateriaal aan te leveren
            // zolang de content-pijplijn er nog niet is.
            val contributionViewModel = remember(activeSignLanguage) {
                ContributionViewModel(cameraController = PlatformCameraController(), signLanguage = activeSignLanguage)
            }
            LaunchedEffect(sharedTemplates, activeSignLanguage) {
                val language = SignLanguage.fromCode(activeSignLanguage)
                val units = CurriculumRepository().load(sharedTemplates, language)
                val allGestures = buildList {
                    units.forEach { unit ->
                        unit.gestures.forEach { gesture ->
                            val existing = sharedTemplates.find { it.id == gesture.id }
                            add(existing ?: SignTemplate(
                                id = gesture.id,
                                name = gesture.name,
                                frames = emptyList(),
                                rules = emptyList(),
                                category = gesture.category,
                                orderIndex = gesture.orderIndex,
                                unitId = gesture.unitId,
                                language = language.code,
                            ))
                        }
                    }
                    // Voeg eventuele overige templates toe die niet in het curriculum van deze
                    // taal zitten (bv. al goedgekeurde bijdragen die nog geen curriculum-rij
                    // hebben) zodat er niets uit het zicht verdwijnt.
                    sharedTemplates.forEach { template ->
                        if (template.language == language.code && find { it.id == template.id } == null) {
                            add(template)
                        }
                    }
                }
                contributionViewModel.setAvailableGestures(allGestures)
            }
            DisposableEffect(Unit) {
                onDispose { /* ContributionViewModel heeft geen clear nodig */ }
            }
            ContributionScreen(
                viewModel = contributionViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }

    // Vangt de Google-OAuth deep-link callback op (zie MainActivity + OAuthCallbackBus) en wisselt
    // de meegegeven code in voor een sessie.
    val pendingOAuthCode by io.github.xavitje.signcoach.data.local.OAuthCallbackBus.pendingCode.collectAsState()
    LaunchedEffect(pendingOAuthCode) {
        pendingOAuthCode?.let { code ->
            authViewModel.handleOAuthCallback(code)
            io.github.xavitje.signcoach.data.local.OAuthCallbackBus.consume()
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
