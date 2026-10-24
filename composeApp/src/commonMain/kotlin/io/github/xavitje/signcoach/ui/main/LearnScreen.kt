package io.github.xavitje.signcoach.ui.main

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.data.model.CurriculumUnit
import io.github.xavitje.signcoach.data.model.LessonStatus
import io.github.xavitje.signcoach.presentation.LearnViewModel
import io.github.xavitje.signcoach.presentation.ProgressViewModel
import io.github.xavitje.signcoach.ui.theme.Clover
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.CoralDeep
import io.github.xavitje.signcoach.ui.theme.Gold
import io.github.xavitje.signcoach.ui.theme.Plum
import io.github.xavitje.signcoach.ui.theme.PlumDeep
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import kotlin.math.sin

/**
 * Leerpad-scherm — SignCoach Redesign (donkere variant, op verzoek): een slingerend pad op een
 * diepe Schemer-achtergrond, met een persoonlijke begroeting + reeks/gems bovenin in plaats van
 * een generieke titelbalk. Elke gebaar-node is een glanzend "3D"-badge met een echt icoon
 * (afspelen voor beschikbaar, slot voor vergrendeld) in plaats van een vlakke cirkel met een
 * letter erin.
 *
 * De eerste ontgrendelde node in het hele pad krijgt de "START"-behandeling (groter, pulserende
 * ring) zodat er altijd een duidelijke volgende stap is. Klikken op een ontgrendelde node
 * navigeert naar het oefen-scherm via [onStartLesson].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    progressViewModel: ProgressViewModel,
    onStartLesson: (gestureId: String) -> Unit,
    onContribute: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val progressState by progressViewModel.state.collectAsState()

    // START-label op de eerstvolgende niet-gehaalde les (bepaald door LessonUnlocker), niet
    // meer op "het eerste gebaar met een template".
    val currentGestureId = state.nextLessonId

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    fun showMessage(text: suspend () -> String) {
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text())
        }
    }

    Scaffold(
        containerColor = PlumDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Geen titelbalk — alleen de statusbalk-inzet vullen met de schermkleur zodat het
            // geheel doorloopt, net als op de SignCoach Redesign-artifact.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumDeep)
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                GreetingHeader(
                    displayName = progressState.profile?.displayName?.takeIf { it.isNotBlank() },
                    streak = progressState.profile?.currentStreak ?: 0,
                    totalXp = progressState.profile?.totalXp ?: 0,
                )
            }

            state.units.forEach { unit ->
                item { UnitHeader(unit = unit) }
                item {
                    WindingUnitPath(
                        gestures = unit.gestures,
                        currentGestureId = currentGestureId,
                        onClick = { index ->
                            val gesture = unit.gestures[index]
                            when (gesture.status) {
                                LessonStatus.AVAILABLE, LessonStatus.COMPLETED -> onStartLesson(gesture.id)
                                LessonStatus.COMING_SOON -> showMessage { getString(Res.string.learn_coming_soon_toast) }
                                LessonStatus.LOCKED -> {
                                    // Verwijs naar de les die eerst gehaald moet worden: de
                                    // laatste open les vóór deze (zelfde nummering als op het pad).
                                    val blocker = unit.gestures.subList(0, index)
                                        .indexOfLast { it.status == LessonStatus.AVAILABLE }
                                    val lessonNumber = (if (blocker >= 0) blocker else 0) + 1
                                    showMessage { getString(Res.string.learn_locked_toast, lessonNumber) }
                                }
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // CTA onderaan als er nog weinig templates zijn
            item {
                ContributeCta(
                    unlockedCount = state.units.sumOf { u -> u.gestures.count { it.hasTemplate } },
                    totalCount = state.units.sumOf { u -> u.gestures.size },
                    onContribute = onContribute,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Begroeting + reeks/gems ────────────────────────────────────────────────

@Composable
private fun GreetingHeader(displayName: String?, streak: Int, totalXp: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = if (displayName != null) stringResource(Res.string.learn_greeting_hi, displayName)
                       else stringResource(Res.string.learn_greeting_hi_fallback),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.learn_greeting_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(emoji = "🔥", value = streak.toString())
            StatPill(emoji = "💎", value = totalXp.toString())
        }
    }
}

@Composable
private fun StatPill(emoji: String, value: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(emoji, fontSize = 13.sp)
            Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Unit-header ─────────────────────────────────────────────────────────────

@Composable
private fun UnitHeader(unit: CurriculumUnit) {
    val unlockedCount = unit.gestures.count { it.hasTemplate }
    val total = unit.gestures.size
    val progress = if (total > 0) unlockedCount.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Plum)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.12f)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(unit.emoji, fontSize = 18.sp)
                }
            }
            Column {
                Text(
                    unit.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(Res.string.learn_gestures_available, unlockedCount, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = Gold,
            trackColor = Color.White.copy(alpha = 0.14f),
        )
    }
}

// ─── Slingerend leerpad ────────────────────────────────────────────────────

private fun pathXFraction(index: Int): Float =
    (0.5f + 0.26f * sin(index * 1.15f)).coerceIn(0.16f, 0.84f)

@Composable
private fun WindingUnitPath(
    gestures: List<CurriculumGesture>,
    currentGestureId: String?,
    onClick: (index: Int) -> Unit,
) {
    if (gestures.isEmpty()) return

    val nodeSpacing = 118.dp
    val topPadding = 32.dp
    val pathHeight = nodeSpacing * gestures.size + topPadding

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(pathHeight),
    ) {
        val roadBed = Color(0xFFC98858)
        val roadTop = Color(0xFFE3AC7E)

        Canvas(modifier = Modifier.matchParentSize()) {
            if (gestures.size > 1) {
                val spacingPx = nodeSpacing.toPx()
                val topPx = topPadding.toPx()
                fun pointFor(i: Int) = androidx.compose.ui.geometry.Offset(
                    x = pathXFraction(i) * size.width,
                    y = topPx + (i + 0.5f) * spacingPx,
                )
                val path = Path()
                val first = pointFor(0)
                path.moveTo(first.x, first.y)
                for (i in 1 until gestures.size) {
                    val prev = pointFor(i - 1)
                    val curr = pointFor(i)
                    path.cubicTo(
                        prev.x, prev.y + spacingPx * 0.5f,
                        curr.x, curr.y - spacingPx * 0.5f,
                        curr.x, curr.y,
                    )
                }
                drawPath(path, color = roadBed, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, color = roadTop, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        val footprint = 84.dp
        gestures.forEachIndexed { i, gesture ->
            val xDp = maxWidth * pathXFraction(i) - footprint / 2
            val yDp = topPadding + nodeSpacing * (i + 0.5f) - footprint / 2
            Box(
                modifier = Modifier.offset(x = xDp, y = yDp).size(footprint),
                contentAlignment = Alignment.Center,
            ) {
                PathNodeBadge(
                    status = gesture.status,
                    current = gesture.id == currentGestureId,
                    index = i,
                    onClick = { onClick(i) },
                )
            }
        }
    }
}

@Composable
private fun PathNodeBadge(status: LessonStatus, current: Boolean, index: Int, onClick: () -> Unit) {
    val locked = status == LessonStatus.LOCKED || status == LessonStatus.COMING_SOON
    val rimSize = if (current) 68.dp else 54.dp
    val ballSize = if (current) 54.dp else 42.dp

    val infiniteTransition = rememberInfiniteTransition(label = "path-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulseAlpha",
    )

    Box(contentAlignment = Alignment.Center) {
        // Grondschaduw — geeft de badge "gewicht" op het pad.
        Box(
            modifier = Modifier
                .offset(y = rimSize / 2 + 5.dp)
                .size(rimSize * 0.55f, 8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f)),
        )

        if (current) {
            Box(
                modifier = Modifier
                    .size(rimSize)
                    .scale(pulseScale)
                    .border(2.5.dp, Coral.copy(alpha = pulseAlpha), CircleShape),
            )
            Surface(
                color = Gold,
                shape = RoundedCornerShape(50),
                shadowElevation = 3.dp,
                modifier = Modifier.offset(y = -(rimSize / 2 + 22.dp)),
            ) {
                Text(
                    stringResource(Res.string.learn_path_start),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1533),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.size(rimSize),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(ballSize)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = when (status) {
                                    LessonStatus.COMPLETED -> listOf(Color(0xFF6FE0A0), Clover)
                                    LessonStatus.AVAILABLE -> listOf(Color(0xFFFFA482), CoralDeep)
                                    LessonStatus.LOCKED -> listOf(Color(0xFFF4F0FA), Color(0xFFCFC5E3))
                                    LessonStatus.COMING_SOON -> listOf(Color(0xFFE9E4F1), Color(0xFFB9AED0))
                                },
                            )
                        )
                        // Ook vergrendelde nodes zijn tikbaar: dan volgt een uitleg-melding
                        // in plaats van stilte (een kind denkt anders dat de app kapot is).
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    // Glans linksboven — suggereert een bolle, glazige badge.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = ballSize * 0.18f, y = ballSize * 0.14f)
                            .size(ballSize * 0.4f, ballSize * 0.22f)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.55f)),
                    )
                    Icon(
                        imageVector = when (status) {
                            LessonStatus.COMPLETED -> Icons.Default.Check
                            LessonStatus.AVAILABLE -> Icons.Default.PlayArrow
                            LessonStatus.LOCKED -> Icons.Default.Lock
                            LessonStatus.COMING_SOON -> Icons.Default.Schedule
                        },
                        contentDescription = stringResource(
                            when (status) {
                                LessonStatus.COMPLETED -> Res.string.learn_completed
                                LessonStatus.AVAILABLE -> Res.string.learn_practice
                                LessonStatus.LOCKED -> Res.string.learn_locked
                                LessonStatus.COMING_SOON -> Res.string.learn_coming_soon
                            }
                        ),
                        tint = if (locked) Plum.copy(alpha = 0.55f) else Color.White,
                        modifier = Modifier.size(if (current) 26.dp else 20.dp),
                    )
                }
            }
        }

        Text(
            (index + 1).toString(),
            modifier = Modifier.offset(y = rimSize / 2 + 18.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

// ─── Bijdragen-CTA ────────────────────────────────────────────────────────────

@Composable
private fun ContributeCta(unlockedCount: Int, totalCount: Int, onContribute: () -> Unit) {
    val locked = totalCount - unlockedCount
    if (locked <= 0) return

    Surface(
        onClick = onContribute,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1B3A2D),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("📹", fontSize = 32.sp)
            Text(
                stringResource(Res.string.learn_cta_waiting, locked),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.learn_cta_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Clover,
            ) {
                Text(
                    stringResource(Res.string.learn_cta_button),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
