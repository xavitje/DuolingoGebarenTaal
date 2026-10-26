package io.github.xavitje.signcoach.ui.contribute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.platform.CameraPreview
import io.github.xavitje.signcoach.platform.PlatformVideoPlayer
import io.github.xavitje.signcoach.presentation.DemoError
import io.github.xavitje.signcoach.presentation.DemoStep
import io.github.xavitje.signcoach.presentation.DemoVideoViewModel
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.ui.common.PlatformBackHandler
import io.github.xavitje.signcoach.ui.theme.Plum
import org.jetbrains.compose.resources.stringResource

/**
 * Demovideo opnemen (vakman) of demovideo's van collega's beoordelen. Een opname gaat eerst in
 * een wachtrij; een andere vakman keurt hem goed voordat leerlingen hem zien.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoVideoScreen(
    viewModel: DemoVideoViewModel,
    gestureNames: Map<String, String>,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(Unit) { onDispose { viewModel.close() } }
    PlatformBackHandler(enabled = true, onBack = onBack)

    val reviewMode = state.step in setOf(DemoStep.REVIEW_LIST, DemoStep.REVIEWING, DemoStep.REVIEW_DONE)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (reviewMode) stringResource(Res.string.demo_review_title)
                        else stringResource(Res.string.demo_record_title, state.gestureName),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.coach_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Plum),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.step) {
                DemoStep.CONSENT -> {
                    Text(stringResource(Res.string.demo_consent_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.demo_tips_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(Res.string.demo_tips), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.consentGiven, onCheckedChange = viewModel::setConsent)
                        Text(stringResource(Res.string.demo_consent_check))
                    }
                    Button(onClick = viewModel::record, enabled = state.consentGiven, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(Res.string.demo_start))
                    }
                }
                DemoStep.COUNTDOWN, DemoStep.RECORDING -> {
                    Surface(Modifier.fillMaxWidth().height(420.dp), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                            CameraPreview(Modifier.fillMaxSize(), isFrontFacing = true)
                            if (state.step == DemoStep.COUNTDOWN) {
                                Text(state.countdown.toString(), color = Color.White, style = MaterialTheme.typography.displayLarge)
                            }
                        }
                    }
                    if (state.step == DemoStep.RECORDING) {
                        Text(stringResource(Res.string.demo_recording))
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                DemoStep.PREVIEW -> {
                    Text(stringResource(Res.string.demo_preview_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.localVideoUri?.let { uri ->
                        Surface(Modifier.fillMaxWidth().height(420.dp), shape = MaterialTheme.shapes.large) {
                            Box(Modifier.background(Color.Black)) { PlatformVideoPlayer(url = uri, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = viewModel::retake, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(Res.string.contrib_review_retake))
                        }
                        Button(onClick = viewModel::send, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(Res.string.contrib_review_send))
                        }
                    }
                }
                DemoStep.UPLOADING -> Busy(stringResource(Res.string.demo_uploading))
                DemoStep.SENT -> {
                    Text(stringResource(Res.string.demo_sent_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.demo_sent_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(Res.string.contrib_success_back)) }
                }

                DemoStep.REVIEW_LIST -> {
                    if (state.isBusy) {
                        Busy("")
                    } else {
                        if (state.ownPending > 0) {
                            Text(stringResource(Res.string.demo_own_pending, state.ownPending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.queue.isEmpty()) {
                            Text(stringResource(Res.string.demo_queue_empty), style = MaterialTheme.typography.titleSmall)
                        }
                        state.queue.forEach { video ->
                            Card(onClick = { viewModel.openVideo(video) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(gestureNames[video.gestureId] ?: video.gestureId, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(Res.string.demo_queue_item, video.createdAt?.take(10) ?: ""),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                DemoStep.REVIEWING -> {
                    val video = state.reviewing
                    var note by remember(video?.id) { mutableStateOf("") }
                    Text(
                        stringResource(Res.string.demo_reviewing_desc, video?.let { gestureNames[it.gestureId] ?: it.gestureId } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(Modifier.fillMaxWidth().height(420.dp), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                            val url = state.reviewUrl
                            if (url == null) CircularProgressIndicator() else PlatformVideoPlayer(url = url, modifier = Modifier.fillMaxSize())
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text(stringResource(Res.string.demo_note_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.decide(false, note) },
                            enabled = !state.isBusy && note.isNotBlank(),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(stringResource(Res.string.demo_reject), color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = { viewModel.decide(true, note) },
                            enabled = !state.isBusy && state.reviewUrl != null,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(stringResource(Res.string.demo_approve)) }
                    }
                    Text(stringResource(Res.string.demo_reject_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DemoStep.REVIEW_DONE -> {
                    Text(
                        stringResource(if (state.lastApproved) Res.string.demo_approved else Res.string.demo_rejected),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = viewModel::openQueue, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(Res.string.demo_next_in_queue))
                    }
                }
                DemoStep.ERROR -> {
                    Text(stringResource(Res.string.contrib_error_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(
                            when (state.error) {
                                DemoError.NOT_SUPPORTED -> Res.string.demo_error_not_supported
                                DemoError.NO_PERMISSION -> Res.string.error_camera_permission_denied
                                DemoError.RECORDING_FAILED -> Res.string.demo_error_recording
                                DemoError.OWN_VIDEO -> Res.string.demo_error_own
                                else -> Res.string.teacher_error_network
                            },
                        ),
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(Res.string.contrib_back)) }
                }
            }
        }
    }
}

@Composable
private fun Busy(label: String) {
    Column(
        Modifier.fillMaxWidth().height(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        if (label.isNotEmpty()) Text(label)
    }
}
