package io.github.xavitje.signcoach.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.CoralSoft
import io.github.xavitje.signcoach.ui.theme.Plum
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Taalkiezer voor gebarentalen — gedeeld tussen de eerste-installatie-onboarding
 * ([io.github.xavitje.signcoach.navigation] route CHOOSE_SIGN_LANGUAGE) en de instellingen-rij
 * in [io.github.xavitje.signcoach.ui.main.ProfileScreen] (route SIGN_LANGUAGE_PICKER).
 *
 * Toont alleen [SignLanguage.SELECTABLE] (NGT + ASL) — BSL bestaat al als enum-waarde voor
 * later, maar heeft nog geen bruikbare referentiedatabron (zie het architectuurplan) en wordt
 * daarom bewust niet aangeboden.
 *
 * [onBack]: null in de onboarding-flow (geen vorig scherm om naar terug te gaan); een lambda
 * in de instellingen-context (toont een terug-pijl in de topbalk).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseSignLanguageScreen(
    currentSelection: SignLanguage?,
    onConfirm: (SignLanguage) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var selected by remember(currentSelection) { mutableStateOf(currentSelection) }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.profile_sign_language_picker_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Plum),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(if (onBack != null) 8.dp else 40.dp))

            if (onBack == null) {
                // Alleen in onboarding: eigen titel/subtitel (settingscontext heeft al een topbar).
                Text(
                    stringResource(Res.string.onboarding_sign_language_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(Res.string.onboarding_sign_language_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            SignLanguage.SELECTABLE.forEach { language ->
                SignLanguageCard(
                    language = language,
                    isSelected = selected == language,
                    onClick = { selected = language },
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
            ) {
                Text(
                    if (onBack != null) {
                        stringResource(Res.string.profile_save)
                    } else {
                        stringResource(Res.string.onboarding_sign_language_continue)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class SignLanguageMeta(
    val nameRes: StringResource,
    val descriptionRes: StringResource,
    val flag: String,
)

private fun metaFor(language: SignLanguage): SignLanguageMeta = when (language) {
    SignLanguage.NGT -> SignLanguageMeta(
        nameRes = Res.string.sign_language_ngt_name,
        descriptionRes = Res.string.sign_language_ngt_description,
        flag = "🇳🇱",
    )
    SignLanguage.ASL -> SignLanguageMeta(
        nameRes = Res.string.sign_language_asl_name,
        descriptionRes = Res.string.sign_language_asl_description,
        flag = "🇺🇸",
    )
    SignLanguage.BSL -> SignLanguageMeta(
        nameRes = Res.string.sign_language_bsl_name,
        descriptionRes = Res.string.sign_language_bsl_description,
        flag = "🇬🇧",
    )
}

@Composable
private fun SignLanguageCard(
    language: SignLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val meta = metaFor(language)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) CoralSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Coral else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(meta.flag, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(meta.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(meta.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .background(Coral, shape = RoundedCornerShape(50))
                        .padding(4.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}
