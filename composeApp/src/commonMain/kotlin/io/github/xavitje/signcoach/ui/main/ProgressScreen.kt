package io.github.xavitje.signcoach.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import io.github.xavitje.signcoach.ui.badges.BadgesSection
import io.github.xavitje.signcoach.ui.theme.Clover
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.Gold
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
                title = { Text(stringResource(Res.string.tab_progress)) },
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
                val goal = profile.dailyGoalXp.coerceAtLeast(1)
                val progress = (todayXp.toFloat() / goal).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Zero Trust / offline-first: laat expliciet zien wanneer de getoonde
                    // cijfers nog lokaal zijn en nog niet bevestigd zijn door de server.
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

                    // Dagdoel: ring + wat er nog nodig is, zodat een kind weet waar het staat.
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 10.dp,
                                    color = if (progress >= 1f) Clover else Gold,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                )
                                Text(
                                    stringResource(Res.string.progress_xp_earned, todayXp, profile.dailyGoalXp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(Res.string.progress_daily_goal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (todayXp >= goal) stringResource(Res.string.progress_goal_done)
                                    else stringResource(Res.string.progress_goal_remaining, goal - todayXp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (todayXp >= goal) Clover else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            emoji = "\uD83D\uDD25",
                            value = stringResource(Res.string.progress_days_streak, profile.currentStreak),
                            label = stringResource(Res.string.progress_current_streak),
                            valueColor = Coral,
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            emoji = "\u2B50",
                            value = "${profile.totalXp}",
                            label = stringResource(Res.string.progress_total_xp),
                            valueColor = Gold,
                        )
                    }

                    // Reeks-bescherming uitgelegd in plaats van een los ijsblokje met een cijfer.
                    if (profile.streakFreezesAvailable > 0) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("\uD83E\uDDCA", style = MaterialTheme.typography.headlineMedium)
                                Column {
                                    Text(
                                        stringResource(Res.string.progress_freeze_title, profile.streakFreezesAvailable),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        stringResource(Res.string.progress_freeze_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (state.allBadges.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(Res.string.progress_badges_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(12.dp))
                                BadgesSection(badges = state.allBadges, earnedBadgeIds = state.earnedBadgeIds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    emoji: String,
    value: String,
    label: String,
    valueColor: Color,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
