package io.github.xavitje.signcoach

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.model.TrackerStatus
import io.github.xavitje.signcoach.domain.coach.CoachIntent
import io.github.xavitje.signcoach.domain.coach.CoachReducer
import io.github.xavitje.signcoach.navigation.AppNavigation
import io.github.xavitje.signcoach.platform.CameraPreview
import io.github.xavitje.signcoach.presentation.AuthViewModel
import io.github.xavitje.signcoach.presentation.CoachViewModel
import io.github.xavitje.signcoach.ui.common.toLocalizedString
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.Cream
import io.github.xavitje.signcoach.ui.theme.Gold
import io.github.xavitje.signcoach.ui.theme.Ink
import io.github.xavitje.signcoach.ui.theme.InkSoft
import io.github.xavitje.signcoach.ui.theme.Plum
import io.github.xavitje.signcoach.ui.theme.PlumDeep
import io.github.xavitje.signcoach.ui.theme.SignCoachTheme
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.platform.changeAppLanguage

@Composable
fun App() {
    LaunchedEffect(Unit) {
        changeAppLanguage(SessionStore.getLanguage())
    }
    // AuthViewModel is de enige singleton die de hele app-levenscyclus meegaat.
    val authViewModel = remember { AuthViewModel() }
    SignCoachTheme {
        AppNavigation(authViewModel = authViewModel)
    }
}

/**
 * Oefenscherm: camera + geselecteerd gebaar + scorefeedback.
 * SignCoach Redesign: donkere Schemer-chrome zodat de camera het middelpunt blijft, met een
 * gouden live-status-pil en een score-ring in plaats van een platte voortgangsbalk.
 * De Admin-knop is hier verwijderd — Admin is nu bereikbaar via Profiel-tab (alleen voor admins).
 * onAdmin-parameter is weggehaald; onContribute blijft voor de contributie-flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(viewModel: CoachViewModel, onBack: () -> Unit = {}, onNext: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsState()
    val selected = state.templates.firstOrNull { it.id == state.selectedTemplateId }

    LaunchedEffect(state.selectedTemplateId) {
        if (state.selectedTemplateId != null && state.status == TrackerStatus.STOPPED) {
            viewModel.dispatch(CoachIntent.StartPractice)
        }
    }

    Scaffold(
        containerColor = PlumDeep,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.coach_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(Res.string.coach_back),
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PlumDeep),
            )
        }
    ) { padding ->
        if (state.isCompleted) {
            Column(
                modifier = Modifier.fillMaxSize().background(Cream).padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                io.github.xavitje.signcoach.ui.common.CompletionCheckmark()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.coach_success_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Plum,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.coach_success_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkSoft,
                )
                state.earnedXp?.let { xp ->
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Gold,
                    ) {
                        Text(
                            stringResource(Res.string.coach_xp_earned, xp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                // Direct door naar het volgende gebaar: houdt het tempo erin, in plaats van elke
                // keer terug naar het pad te moeten.
                if (onNext != null) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    ) { Text(stringResource(Res.string.coach_next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.5.dp, Plum.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Plum),
                    ) { Text(stringResource(Res.string.coach_back_to_learn)) }
                } else {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    ) { Text(stringResource(Res.string.coach_back_to_learn), fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlumDeep)
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                selected?.let { template ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        instructionFor(template.id),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RepProgress(done = state.repsDone, required = state.repsRequired, waitingForRelease = state.waitingForRelease)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.Black),
                    ) {
                        CameraPreview(Modifier.fillMaxSize(), isFrontFacing = true)
                        Surface(
                            color = Color(0xFF140E26).copy(alpha = 0.72f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Gold))
                                Text(statusText(state.status), color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                state.feedback?.let { feedback ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF2A2246),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ScoreRing(score = feedback.score)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(Res.string.coach_score, (feedback.score * 100).toInt()),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    feedback.feedbackMessages.joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }

                state.error?.let {
                    Text(it.toLocalizedString(), color = Color(0xFFFFB4AB))
                }
                if (state.status == TrackerStatus.UNSUPPORTED) {
                    Text(
                        stringResource(Res.string.coach_unsupported),
                        color = Color(0xFFFFB4AB),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.dispatch(CoachIntent.StartPractice) },
                        enabled = selected != null && state.status !in setOf(TrackerStatus.STARTING, TrackerStatus.RUNNING),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Coral,
                            contentColor = Color.White,
                            disabledContainerColor = Coral.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f),
                        ),
                    ) { Text(stringResource(Res.string.coach_start), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { viewModel.dispatch(CoachIntent.StopPractice) },
                        enabled = state.status == TrackerStatus.RUNNING || state.status == TrackerStatus.STARTING,
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) { Text(stringResource(Res.string.coach_stop)) }
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** Herhalingen in deze les: bolletjes die groen kleuren + uitleg om los te laten tussendoor. */
@Composable
private fun RepProgress(done: Int, required: Int, waitingForRelease: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(required) { i ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (i < done) io.github.xavitje.signcoach.ui.theme.Clover else Color.White.copy(alpha = 0.18f)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(Res.string.coach_reps, done, required),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (waitingForRelease) {
            Text(stringResource(Res.string.coach_rep_release), color = Gold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Ronde live-scoreindicator — vervangt de platte voortgangsbalk in het feedbackkaartje. */
@Composable
private fun ScoreRing(score: Float) {
    val animatedScore by animateFloatAsState(targetValue = score.coerceIn(0f, 1f), label = "score-ring")
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = Gold,
                startAngle = -90f,
                sweepAngle = 360f * animatedScore,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            "${(animatedScore * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GestureChip(template: SignTemplate, selected: SignTemplate?, viewModel: CoachViewModel) {
    FilterChip(
        selected = selected?.id == template.id,
        onClick = { viewModel.dispatch(CoachIntent.SelectTemplate(template.id)) },
        label = { Text(template.name) },
    )
}

@Composable
fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun statusText(status: TrackerStatus): String = when (status) {
    TrackerStatus.STOPPED -> stringResource(Res.string.coach_status_stopped)
    TrackerStatus.STARTING -> stringResource(Res.string.coach_status_starting)
    TrackerStatus.RUNNING -> stringResource(Res.string.coach_status_running)
    TrackerStatus.NO_PERMISSION -> stringResource(Res.string.coach_status_no_permission)
    TrackerStatus.UNSUPPORTED -> stringResource(Res.string.coach_status_unsupported)
    TrackerStatus.ERROR -> stringResource(Res.string.coach_status_error)
}

@Composable
private fun instructionFor(id: String): String = when (id) {
    "letter_a" -> stringResource(Res.string.coach_instr_letter_a)
    "letter_b" -> stringResource(Res.string.coach_instr_letter_b)
    "letter_l" -> stringResource(Res.string.coach_instr_letter_l)
    "swipe_right" -> stringResource(Res.string.coach_instr_swipe_right)
    else -> stringResource(Res.string.coach_instr_default)
}
