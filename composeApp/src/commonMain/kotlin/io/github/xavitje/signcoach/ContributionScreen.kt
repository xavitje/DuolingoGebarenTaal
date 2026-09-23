package io.github.xavitje.signcoach

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.platform.CameraPreview
import io.github.xavitje.signcoach.presentation.ContributionStep
import io.github.xavitje.signcoach.presentation.ContributionViewModel
import io.github.xavitje.signcoach.ui.common.toLocalizedString
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionScreen(viewModel: ContributionViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.contrib_title)) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = io.github.xavitje.signcoach.ui.theme.Plum,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.step) {
                ContributionStep.PICK_GESTURE -> GesturePickerStep(state.availableGestures, viewModel, onBack)
                ContributionStep.CONSENT -> ConsentStep(state.consentGiven, viewModel)
                ContributionStep.COUNTDOWN -> CountdownStep(state.countdownSeconds)
                ContributionStep.RECORDING -> RecordingStep(state.recordingProgress)
                ContributionStep.UPLOADING -> UploadingStep()
                ContributionStep.SUCCESS -> SuccessStep(viewModel, onBack)
                ContributionStep.ERROR -> ErrorStep(state.error, viewModel)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun GesturePickerStep(
    gestures: List<SignTemplate>,
    viewModel: ContributionViewModel,
    onBack: () -> Unit,
) {
    Text(stringResource(Res.string.contrib_picker_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.contrib_picker_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val letters = gestures.filter { it.category == "letter" || it.unitId == "alphabet" }
    val numbers = gestures.filter { it.category == "number" || it.unitId == "numbers" }
    val others = gestures.filter { !letters.contains(it) && !numbers.contains(it) }

    if (letters.isNotEmpty()) {
        Text(stringResource(Res.string.contrib_picker_alphabet), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            letters.forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.selectGesture(template.id) },
                    label = { Text(template.name) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (numbers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.contrib_picker_numbers), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            numbers.forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.selectGesture(template.id) },
                    label = { Text(template.name) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (others.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.contrib_picker_other), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            others.forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.selectGesture(template.id) },
                    label = { Text(template.name) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.contrib_back)) }
}

@Composable
private fun ConsentStep(consentGiven: Boolean, viewModel: ContributionViewModel) {
    Text(stringResource(Res.string.contrib_consent_title), style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.contrib_consent_p1))
            Text(stringResource(Res.string.contrib_consent_p2))
            Text(stringResource(Res.string.contrib_consent_p3))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = consentGiven, onCheckedChange = { viewModel.setConsent(it) })
        Text(stringResource(Res.string.contrib_consent_check))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.cancelToPicker() }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.contrib_cancel)) }
        Button(
            onClick = { viewModel.startRecording() },
            enabled = consentGiven,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(Res.string.contrib_start)) }
    }
}

@Composable
private fun CountdownStep(secondsLeft: Int) {
    Text(stringResource(Res.string.contrib_countdown_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.contrib_countdown_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            CameraPreview(Modifier.fillMaxSize(), isFrontFacing = true)
            Text(
                text = if (secondsLeft > 0) secondsLeft.toString() else stringResource(Res.string.contrib_countdown_go),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
            )
        }
    }
}

@Composable
private fun RecordingStep(progress: Float) {
    Text(stringResource(Res.string.contrib_recording_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.contrib_recording_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.background(Color.Black)) {
            CameraPreview(Modifier.fillMaxSize(), isFrontFacing = true)
        }
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun UploadingStep() {
    Column(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.contrib_uploading))
    }
}

@Composable
private fun SuccessStep(viewModel: ContributionViewModel, onBack: () -> Unit) {
    Text(stringResource(Res.string.contrib_success_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.contrib_success_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.contrib_success_again)) }
        Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.contrib_success_back)) }
    }
}

@Composable
private fun ErrorStep(error: io.github.xavitje.signcoach.domain.AppError?, viewModel: ContributionViewModel) {
    Text(stringResource(Res.string.contrib_error_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
    Text(error?.toLocalizedString() ?: stringResource(Res.string.contrib_error_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.contrib_error_retry)) }
}
