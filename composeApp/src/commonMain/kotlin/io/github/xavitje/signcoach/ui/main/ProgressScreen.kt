package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import io.github.xavitje.signcoach.presentation.ProgressViewModel
import io.github.xavitje.signcoach.ui.common.toLocalizedString
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

/**
 * Voortgang-tab: toont de dagelijkse XP, de huidige streak en de profiel details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voortgang") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = io.github.xavitje.signcoach.ui.theme.Plum,
                ),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp).align(Alignment.Center))
            } else if (state.error != null) {
                Column(
                    modifier = Modifier.padding(32.dp).align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = state.error?.toLocalizedString() ?: "Fout",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    // Zero Trust: een mislukte laadpoging is nooit een doodlopende weg — de
                    // gebruiker kan altijd zelf opnieuw proberen in plaats van vast te zitten.
                    Button(onClick = { viewModel.loadProgress() }) {
                        Text(stringResource(Res.string.progress_retry))
                    }
                }
            } else if (state.profile != null && state.profile!!.totalXp == 0 && state.profile!!.currentStreak == 0) {
                // Fase D: lege staat voor een gloednieuwe gebruiker — een 0%-ring en "0 dagen"
                // oogt als een kapot scherm, dus tonen we in plaats daarvan een uitnodiging.
                ProgressEmptyState(modifier = Modifier.align(Alignment.Center))
            } else if (state.profile != null) {
                val profile = state.profile!!
                val todayXp = state.todayXp
                val progress = (todayXp.toFloat()) / profile.dailyGoalXp.coerceAtLeast(1)
                val streak = profile.currentStreak
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Zero Trust / offline-first: laat expliciet zien wanneer de getoonde
                    // cijfers nog lokaal zijn en nog niet bevestigd zijn door de server, in
                    // plaats van stilzwijgend te doen alsof alles al gesynchroniseerd is.
                    if (state.isSyncing) {
                        Text(
                            stringResource(Res.string.progress_syncing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (state.pendingSyncCount > 0) {
                        Text(
                            stringResource(Res.string.progress_pending_sync, state.pendingSyncCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    // Daily Goal (XP Ring)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(Res.string.progress_daily_goal), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 8.dp,
                                    color = if (progress >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    stringResource(Res.string.progress_xp_earned, todayXp, profile.dailyGoalXp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Streak & Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Streak Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔥", style = MaterialTheme.typography.displaySmall)
                                Text(
                                    stringResource(Res.string.progress_days_streak, streak),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(stringResource(Res.string.progress_current_streak), style = MaterialTheme.typography.labelMedium)
                                if (profile.streakFreezesAvailable > 0) {
                                    Text(
                                        "🧊 ${profile.streakFreezesAvailable}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }

                        // XP Vandaag Card — toont de XP die vandaag behaald is (niet de all-time
                        // total, die staat op het Profiel-tabblad).
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("⚡", style = MaterialTheme.typography.displaySmall)
                                Text("$todayXp", style = MaterialTheme.typography.headlineMedium)
                                Text(stringResource(Res.string.progress_xp_today), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🤟", style = MaterialTheme.typography.displayLarge)
        Text(
            stringResource(Res.string.progress_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.progress_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
