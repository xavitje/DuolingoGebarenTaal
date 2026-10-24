package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.presentation.AuthViewModel
import io.github.xavitje.signcoach.ui.badges.BadgesSection
import io.github.xavitje.signcoach.ui.common.toLocalizedString
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.xavitje.signcoach.ui.theme.Clover
import io.github.xavitje.signcoach.ui.theme.CloverLight
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.CoralDeep
import io.github.xavitje.signcoach.ui.theme.GoldDeep
import io.github.xavitje.signcoach.ui.theme.GoldLight
import io.github.xavitje.signcoach.ui.theme.Plum
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.platform.changeAppLanguage
import androidx.compose.material.icons.filled.Language

/**
 * Profiel-tab: avatar, naam, e-mail, badges, uitloggen en admin-knop (alleen voor admins).
 * Fase D: avatar-picker, wachtwoord-wijzigen (alleen e-mail-accounts) en account verwijderen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    progressViewModel: io.github.xavitje.signcoach.presentation.ProgressViewModel? = null,
    onNavigateToAdmin: () -> Unit,
    onNavigateToContribute: () -> Unit,
    onNavigateToPrivacy: () -> Unit = {},
    signLanguage: String = "ngt",
    onNavigateToSignLanguagePicker: () -> Unit = {},
) {
    val authState by authViewModel.state.collectAsState()
    val progressState = progressViewModel?.state?.collectAsState()?.value
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(SessionStore.getLanguage()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        progressViewModel?.loadProgress()
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(Res.string.profile_logout_confirm_title)) },
            text = { Text(stringResource(Res.string.profile_logout_confirm_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(Res.string.profile_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(Res.string.profile_cancel)) }
            },
        )
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            currentEmoji = progressState?.profile?.avatarEmoji ?: "🧑",
            onPick = { emoji ->
                progressViewModel?.updateAvatarEmoji(emoji)
                showAvatarPicker = false
            },
            onDismiss = { showAvatarPicker = false },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            authViewModel = authViewModel,
            onDismiss = { showChangePassword = false },
        )
    }

    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text(stringResource(Res.string.profile_delete_account_confirm_title)) },
            text = { Text(stringResource(Res.string.profile_delete_account_confirm_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccount = false
                        authViewModel.deleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(Res.string.profile_delete_account_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccount = false }) { Text(stringResource(Res.string.profile_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = io.github.xavitje.signcoach.ui.theme.Plum,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Avatar + naam
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Avatar-cirkel — glanzende medaille in dezelfde stijl als de pad-badges
                    // op het Leren-scherm (witte rand, gradient-bol, glans linksboven) in
                    // plaats van een platte cirkel. Tikken opent de emoji-picker (Fase D).
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors = listOf(Color(0xFFFFA482), CoralDeep))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = 11.dp, y = 8.dp)
                                    .size(20.dp, 11.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.5f)),
                            )
                            Text(
                                text = progressState?.profile?.avatarEmoji ?: "🤟",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = authState.email?.substringBefore("@") ?: stringResource(Res.string.profile_user_fallback),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            text = authState.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }

                    // Compacte knop: de tekstknop "Kies je avatar" drukte de naam eerder in een
                    // smalle kolom, waardoor naam en e-mail midden in een woord afbraken.
                    IconButton(onClick = { showAvatarPicker = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.profile_avatar_pick_title),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Stats-rij
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val streakDays = progressState?.profile?.currentStreak ?: 0
                val totalXp = progressState?.profile?.totalXp ?: 0
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🔥",
                    label = stringResource(Res.string.profile_streak_label),
                    value = if (streakDays == 1) stringResource(Res.string.profile_streak_one)
                            else stringResource(Res.string.profile_streak_many, streakDays),
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = "⭐",
                    label = stringResource(Res.string.progress_total_xp),
                    value = "$totalXp XP",
                )
            }

            // Badges (Fase D)
            val allBadges = progressState?.allBadges ?: emptyList()
            if (allBadges.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.profile_badges_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BadgesSection(
                    badges = allBadges,
                    earnedBadgeIds = progressState?.earnedBadgeIds ?: emptySet(),
                )
            }

            // Bijdragen Sectie
            HorizontalDivider()
            Text(
                stringResource(Res.string.profile_section_contribute),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = onNavigateToContribute,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(Res.string.profile_contribute),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Admin-sectie (alleen zichtbaar voor admins)
            if (authState.isAdmin) {
                HorizontalDivider()
                Text(
                    stringResource(Res.string.profile_section_admin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    onClick = onNavigateToAdmin,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(Res.string.profile_admin_panel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            HorizontalDivider()

            // Instellingen Sectie
            Text(
                stringResource(Res.string.profile_settings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = {
                    val newLang = if (currentLanguage == "nl") "en" else "nl"
                    SessionStore.setLanguage(newLang)
                    currentLanguage = newLang
                    changeAppLanguage(newLang)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(Res.string.profile_language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        if (currentLanguage == "nl") stringResource(Res.string.profile_language_dutch) else stringResource(Res.string.profile_language_english),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Gebarentaal — navigeert naar een los kiesscherm (ChooseSignLanguageScreen), i.p.v.
            // de inline-toggle-stijl van de UI-taalrij hierboven: met 2+ betekenisvolle opties
            // per stuk tekst is een apart scherm duidelijker dan een simpele in-place toggle.
            Surface(
                onClick = onNavigateToSignLanguagePicker,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🤟", style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.profile_sign_language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        signLanguage.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Privacybeleid — altijd zichtbaar, ongeacht account-type.
            Surface(
                onClick = onNavigateToPrivacy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.privacy_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Wachtwoord wijzigen — alleen zinvol voor e-mail-accounts (Google-gebruikers hebben
            // geen Supabase-wachtwoord om te wijzigen).
            if (authState.authProvider != "google") {
                Surface(
                    onClick = { showChangePassword = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(Res.string.profile_change_password),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Google-account koppelen — alleen zinvol als er nog geen Google-identiteit aan dit
            // account hangt. Hergebruikt dezelfde OAuth-flow als de inlogknop (beginGoogleSignIn +
            // BrowserLauncher); Supabase koppelt automatisch aan het huidige account zolang het
            // Google-account hetzelfde (geverifieerde) e-mailadres gebruikt als dit account.
            if ("google" in authState.linkedProviders) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("🔗", style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.profile_google_linked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                Surface(
                    onClick = {
                        val url = authViewModel.beginGoogleSignIn()
                        io.github.xavitje.signcoach.platform.BrowserLauncher.open(url)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("🔗", style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.profile_link_google),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(
                stringResource(Res.string.profile_section_account),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Uitloggen
            Surface(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        stringResource(Res.string.profile_logout),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Account verwijderen — bewust visueel losstaand van uitloggen, geen containerkleur
            // (destructief maar niet per ongeluk aantikbaar naast "Uitloggen").
            Surface(
                onClick = { showDeleteAccount = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.profile_delete_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: String, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Vaste, kleine set emoji's — geen fotobeheer nodig in v1 (zie PRODUCT_PLAN.md paragraaf 7).
 * Voorop de gebaren die bij een gebarentaal-app passen, dan een speelse mix van mensen en een
 * paar dieren — niet alleen een saaie rij "generieke persoon"-emoji's.
 */
private val AVATAR_EMOJI_OPTIONS = listOf(
    "🤟", "✋", "🙌", "👋", "🧑", "👩", "👨", "🧕",
    "👱", "🧑‍🦱", "👧", "👦", "🐵", "🦊", "🐨", "🦄",
)

/** Elke medaille in de picker krijgt een eigen brand-gradient in plaats van één platte kleur. */
private val AVATAR_SWATCH_GRADIENTS = listOf(
    Color(0xFFFFA482) to CoralDeep,
    GoldLight to GoldDeep,
    CloverLight to Clover,
    Color(0xFFCFC5E3) to Plum,
)

@Composable
private fun AvatarPickerDialog(currentEmoji: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.profile_avatar_pick_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(240.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(AVATAR_EMOJI_OPTIONS) { index, emoji ->
                    val selected = emoji == currentEmoji
                    val (from, to) = AVATAR_SWATCH_GRADIENTS[index % AVATAR_SWATCH_GRADIENTS.size]
                    Surface(
                        onClick = { onPick(emoji) },
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = if (selected) 4.dp else 1.dp,
                        border = if (selected) BorderStroke(2.5.dp, Coral) else null,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors = listOf(from, to))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = 9.dp, y = 6.dp)
                                    .size(16.dp, 9.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.45f)),
                            )
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.profile_cancel)) }
        },
    )
}

@Composable
private fun ChangePasswordDialog(authViewModel: AuthViewModel, onDismiss: () -> Unit) {
    val authState by authViewModel.state.collectAsState()
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Sluit het dialoog automatisch zodra de wijziging gelukt is (infoMessage wordt gezet).
    androidx.compose.runtime.LaunchedEffect(authState.infoMessage) {
        if (authState.infoMessage != null) {
            onDismiss()
            authViewModel.clearError()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.profile_change_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(Res.string.profile_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(Res.string.profile_confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                authState.error?.let { err ->
                    Text(err.toLocalizedString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { authViewModel.changePassword(newPassword, confirmPassword) },
                enabled = !authState.isLoading,
            ) { Text(stringResource(Res.string.profile_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.profile_cancel)) }
        },
    )
}
