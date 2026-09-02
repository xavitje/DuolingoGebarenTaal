package com.example.signcoach

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.model.TrackerStatus
import com.example.signcoach.data.repository.AssetTemplateRepository
import com.example.signcoach.domain.coach.CoachIntent
import com.example.signcoach.platform.CameraPreview
import com.example.signcoach.platform.PlatformCameraController
import com.example.signcoach.presentation.CoachViewModel
import com.example.signcoach.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        var viewModel by remember { mutableStateOf<CoachViewModel?>(null) }
        var startupError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            runCatching {
                val json = Res.readBytes("files/templates.json").decodeToString()
                CoachViewModel(
                    repository = AssetTemplateRepository(json),
                    cameraController = PlatformCameraController(),
                )
            }.onSuccess { created ->
                viewModel = created
                created.dispatch(CoachIntent.LoadTemplates)
            }.onFailure {
                startupError = "De lokale gebaren konden niet worden geopend."
            }
        }
        DisposableEffect(viewModel) {
            onDispose { viewModel?.clear() }
        }

        when {
            startupError != null -> CenteredMessage(startupError!!)
            viewModel == null -> CenteredMessage("Gebaren laden…")
            else -> CoachScreen(viewModel!!)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachScreen(viewModel: CoachViewModel) {
    val state by viewModel.state.collectAsState()
    val selected = state.templates.firstOrNull { it.id == state.selectedTemplateId }
    Scaffold(topBar = { TopAppBar(title = { Text("Sign Language Coach") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Kies een gebaar", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.templates.forEach { template ->
                    GestureChip(template, selected, viewModel)
                }
            }

            selected?.let { template ->
                Text(instructionFor(template.id), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                ) {
                    Box(Modifier.background(Color.Black)) {
                        CameraPreview(Modifier.fillMaxSize(), isFrontFacing = true)
                        Text(
                            text = statusText(state.status),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        )
                    }
                }
            }

            state.feedback?.let { feedback ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Score: ${(feedback.score * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                        LinearProgressIndicator(
                            progress = { feedback.score },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(feedback.feedbackMessages.joinToString(" "))
                    }
                }
            }

            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.status == TrackerStatus.UNSUPPORTED) {
                Text(
                    "Handtracking is op dit platform nog niet beschikbaar.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.dispatch(CoachIntent.StartPractice) },
                    enabled = selected != null && state.status !in setOf(TrackerStatus.STARTING, TrackerStatus.RUNNING),
                    modifier = Modifier.weight(1f),
                ) { Text("Start oefenen") }
                Button(
                    onClick = { viewModel.dispatch(CoachIntent.StopPractice) },
                    enabled = state.status == TrackerStatus.RUNNING || state.status == TrackerStatus.STARTING,
                ) { Text("Stop") }
            }
            Spacer(Modifier.height(18.dp))
        }
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
private fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

private fun statusText(status: TrackerStatus): String = when (status) {
    TrackerStatus.STOPPED -> "Klaar om te oefenen"
    TrackerStatus.STARTING -> "Camera starten…"
    TrackerStatus.RUNNING -> "Houd je hand in beeld"
    TrackerStatus.NO_PERMISSION -> "Cameratoegang nodig"
    TrackerStatus.UNSUPPORTED -> "Niet ondersteund"
    TrackerStatus.ERROR -> "Trackingfout"
}

private fun instructionFor(id: String): String = when (id) {
    "letter_a" -> "Maak een vuist en leg je duim langs de zijkant van je vingers."
    "letter_b" -> "Strek vier vingers omhoog en buig je duim over je handpalm."
    "letter_l" -> "Strek je wijsvinger omhoog en je duim opzij tot een L."
    "swipe_right" -> "Houd de handvorm vast en beweeg duidelijk van links naar rechts."
    else -> "Volg het voorbeeld en houd je hand volledig in beeld."
}
