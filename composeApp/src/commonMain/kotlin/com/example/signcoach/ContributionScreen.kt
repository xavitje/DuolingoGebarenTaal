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
import androidx.compose.foundation.rememberScrollState
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
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.platform.CameraPreview
import com.example.signcoach.presentation.ContributionStep
import com.example.signcoach.presentation.ContributionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionScreen(viewModel: ContributionViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Bijdragen aan de database") }) }) { padding ->
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
                ContributionStep.ERROR -> ErrorStep(state.errorMessage, viewModel)
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
    Text("Welk gebaar wil je opnemen?", style = MaterialTheme.typography.titleMedium)
    Text(
        "Elke bijdrage wordt eerst door een beoordelaar bekeken voordat andere gebruikers hem " +
            "als referentie krijgen — jouw opname gaat dus nooit direct live.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        gestures.forEach { template ->
            FilterChip(
                selected = false,
                onClick = { viewModel.selectGesture(template.id) },
                label = { Text(template.name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Terug naar oefenen") }
}

@Composable
private fun ConsentStep(consentGiven: Boolean, viewModel: ContributionViewModel) {
    Text("Toestemming", style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "We nemen een korte reeks beelden van je hand op, samen met de bewegingsdata " +
                    "(landmarks) die de app al gebruikt om gebaren te herkennen.",
            )
            Text(
                "De beelden zijn alleen zichtbaar voor beoordelaars, uitsluitend om te " +
                    "controleren of het gebaar correct is uitgevoerd. Ze worden niet gedeeld " +
                    "met andere gebruikers en niet gebruikt voor iets anders dan deze " +
                    "beoordeling.",
            )
            Text(
                "Na goedkeuring wordt alleen de bewegingsdata (geen beeld) toegevoegd aan de " +
                    "gedeelde referentiedatabase. Je kunt op elk moment vragen om je bijdrage " +
                    "te laten verwijderen.",
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = consentGiven, onCheckedChange = { viewModel.setConsent(it) })
        Text("Ik geef toestemming voor het opnemen en beoordelen van deze bijdrage.")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.cancelToPicker() }, modifier = Modifier.weight(1f)) { Text("Annuleren") }
        Button(
            onClick = { viewModel.startRecording() },
            enabled = consentGiven,
            modifier = Modifier.weight(1f),
        ) { Text("Start opname") }
    }
}

@Composable
private fun CountdownStep(secondsLeft: Int) {
    Text("Zet je hand klaar", style = MaterialTheme.typography.titleMedium)
    Text(
        "Zet je hand nu al in de vorm van het gebaar en houd hem stil in beeld. De opname " +
            "begint vanzelf zodra het aftellen klaar is — dan even stil blijven houden tot de " +
            "balk vol is.",
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
                text = if (secondsLeft > 0) secondsLeft.toString() else "Ga!",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
            )
        }
    }
}

@Composable
private fun RecordingStep(progress: Float) {
    Text("Opnemen…", style = MaterialTheme.typography.titleMedium)
    Text(
        "Nu opnemen — houd de handvorm stil tot de balk hieronder vol is. Niet meer bewegen " +
            "of van vorm veranderen tijdens het opnemen.",
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
        Text("Bijdrage versturen…")
    }
}

@Composable
private fun SuccessStep(viewModel: ContributionViewModel, onBack: () -> Unit) {
    Text("Bedankt voor je bijdrage!", style = MaterialTheme.typography.titleMedium)
    Text(
        "Een beoordelaar bekijkt je opname binnenkort. Zodra hij is goedgekeurd, helpt hij " +
            "andere gebruikers dit gebaar nauwkeuriger te herkennen.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.weight(1f)) { Text("Nog een gebaar opnemen") }
        Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Terug naar oefenen") }
    }
}

@Composable
private fun ErrorStep(message: String?, viewModel: ContributionViewModel) {
    Text("Er ging iets mis", style = MaterialTheme.typography.titleMedium)
    Text(message ?: "Onbekende fout.", color = MaterialTheme.colorScheme.error)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.cancelToPicker() }, modifier = Modifier.weight(1f)) { Text("Ander gebaar") }
        Button(onClick = { viewModel.startRecording() }, modifier = Modifier.weight(1f)) { Text("Opnieuw proberen") }
    }
}
