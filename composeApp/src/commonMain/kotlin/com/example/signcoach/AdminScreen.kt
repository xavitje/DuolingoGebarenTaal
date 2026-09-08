package com.example.signcoach

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.signcoach.data.remote.AdminApi
import com.example.signcoach.presentation.AdminStep
import com.example.signcoach.presentation.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Beheer — bijdragen beoordelen") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.step) {
                AdminStep.LOGIN -> AdminLoginStep(viewModel, state.loginEmail, state.loginPassword, state.isLoggingIn, state.loginError, onBack)
                AdminStep.LIST -> AdminListStep(viewModel, state.pendingSamples, state.isLoadingList, state.listError, onBack)
                AdminStep.DETAIL -> {
                    val sample = state.selectedSample
                    if (sample != null) {
                        AdminDetailStep(
                            viewModel = viewModel,
                            sample = sample,
                            images = state.selectedImages,
                            isLoadingImages = state.isLoadingImages,
                            isSubmitting = state.isSubmittingDecision,
                            rejectNote = state.rejectNote,
                            actionError = state.actionError,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AdminLoginStep(
    viewModel: AdminViewModel,
    email: String,
    password: String,
    isLoggingIn: Boolean,
    loginError: String?,
    onBack: () -> Unit,
) {
    Text("Inloggen als beheerder", style = MaterialTheme.typography.titleMedium)
    Text(
        "Alleen het admin-account kan hier inloggen — andere gebruikers zien niets, ook niet " +
            "als ze dit scherm openen.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = email,
        onValueChange = viewModel::setLoginEmail,
        label = { Text("E-mailadres") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = viewModel::setLoginPassword,
        label = { Text("Wachtwoord") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { viewModel.login() },
        enabled = !isLoggingIn && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (isLoggingIn) "Bezig met inloggen…" else "Inloggen") }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Terug naar oefenen") }
}

@Composable
private fun AdminListStep(
    viewModel: AdminViewModel,
    samples: List<AdminApi.AdminSampleDto>,
    isLoading: Boolean,
    listError: String?,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Te beoordelen (${samples.size})", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { viewModel.loadPending() }) { Text("Ververs") }
    }
    listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when {
        isLoading && samples.isEmpty() -> CircularProgressIndicator()
        samples.isEmpty() -> Text(
            "Niets te beoordelen op dit moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            samples.forEach { sample ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(sample.gestureId, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "hand: ${sample.handedness}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { viewModel.selectSample(sample) }) { Text("Bekijken") }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = { viewModel.logout(); onBack() }, modifier = Modifier.fillMaxWidth()) {
        Text("Uitloggen en terug naar oefenen")
    }
}

@Composable
private fun AdminDetailStep(
    viewModel: AdminViewModel,
    sample: AdminApi.AdminSampleDto,
    images: List<ImageBitmap>,
    isLoadingImages: Boolean,
    isSubmitting: Boolean,
    rejectNote: String,
    actionError: String?,
) {
    Text(sample.gestureId, style = MaterialTheme.typography.titleMedium)
    Text(
        "hand: ${sample.handedness} · ${images.size} beelden" +
            if (sample.createdAt != null) " · opgenomen op ${sample.createdAt}" else "",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when {
        isLoadingImages -> CircularProgressIndicator()
        images.isEmpty() -> Text(
            "Geen review-beelden gevonden voor deze bijdrage.",
            color = MaterialTheme.colorScheme.error,
        )
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(images) { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.width(140.dp).height(140.dp),
                )
            }
        }
    }

    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = { viewModel.approve() },
        enabled = !isSubmitting && images.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (isSubmitting) "Bezig…" else "Goedkeuren") }

    OutlinedTextField(
        value = rejectNote,
        onValueChange = viewModel::setRejectNote,
        label = { Text("Reden voor afkeuren (optioneel)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { viewModel.reject() },
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Afkeuren") }

    OutlinedButton(onClick = { viewModel.backToList() }, modifier = Modifier.fillMaxWidth()) {
        Text("Terug naar wachtrij")
    }
}
