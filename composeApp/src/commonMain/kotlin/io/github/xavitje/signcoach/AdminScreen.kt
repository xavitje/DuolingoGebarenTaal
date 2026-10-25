package io.github.xavitje.signcoach

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import io.github.xavitje.signcoach.data.remote.AdminApi
import io.github.xavitje.signcoach.data.remote.CrashAdminApi
import io.github.xavitje.signcoach.presentation.AdminStep
import io.github.xavitje.signcoach.presentation.AdminViewModel
import io.github.xavitje.signcoach.ui.common.toLocalizedString
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, onBack: () -> Unit, onOpenSchools: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initFromActiveSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.admin_title)) },
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
            // Fase 3: scholen, licenties en docenten beheren.
            if (state.step == AdminStep.LIST) {
                androidx.compose.material3.Button(
                    onClick = onOpenSchools,
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) { Text(stringResource(Res.string.admin_schools_button)) }
            }
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
                AdminStep.CRASH_LIST -> AdminCrashListStep(
                    viewModel = viewModel,
                    reports = state.crashReports,
                    isLoading = state.isLoadingCrashes,
                    listError = state.crashListError,
                )
                AdminStep.CRASH_DETAIL -> {
                    val crash = state.selectedCrash
                    if (crash != null) {
                        AdminCrashDetailStep(viewModel = viewModel, report = crash)
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
    loginError: io.github.xavitje.signcoach.domain.AppError?,
    onBack: () -> Unit,
) {
    Text(stringResource(Res.string.admin_login_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.admin_login_desc),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = email,
        onValueChange = viewModel::setLoginEmail,
        label = { Text(stringResource(Res.string.admin_email)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = viewModel::setLoginPassword,
        label = { Text(stringResource(Res.string.admin_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    loginError?.let { Text(it.toLocalizedString(), color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { viewModel.login() },
        enabled = !isLoggingIn && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (isLoggingIn) stringResource(Res.string.admin_logging_in) else stringResource(Res.string.admin_login_btn)) }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.admin_back)) }
}

@Composable
private fun AdminListStep(
    viewModel: AdminViewModel,
    samples: List<AdminApi.AdminSampleDto>,
    isLoading: Boolean,
    listError: io.github.xavitje.signcoach.domain.AppError?,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(Res.string.admin_list_title, samples.size), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { viewModel.loadPending() }) { Text(stringResource(Res.string.admin_list_refresh)) }
    }
    OutlinedButton(onClick = { viewModel.openCrashes() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.admin_crashes_btn))
    }
    listError?.let { Text(it.toLocalizedString(), color = MaterialTheme.colorScheme.error) }
    when {
        isLoading && samples.isEmpty() -> CircularProgressIndicator()
        samples.isEmpty() -> Text(
            stringResource(Res.string.admin_list_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            samples.forEach { sample ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(sample.gestureId, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(Res.string.admin_list_hand, sample.handedness),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { viewModel.selectSample(sample) }) { Text(stringResource(Res.string.admin_list_view)) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = { viewModel.logout(); onBack() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.admin_back))
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
    actionError: io.github.xavitje.signcoach.domain.AppError?,
) {
    Text(sample.gestureId, style = MaterialTheme.typography.titleMedium)
    val recordedOn = sample.createdAt?.let { stringResource(Res.string.admin_detail_recorded_on, it) } ?: ""
    Text(
        stringResource(Res.string.admin_detail_subtitle, sample.handedness, images.size, recordedOn),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when {
        isLoadingImages -> CircularProgressIndicator()
        images.isEmpty() -> Text(
            stringResource(Res.string.admin_detail_no_images),
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

    actionError?.let { Text(it.toLocalizedString(), color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = { viewModel.approve() },
        enabled = !isSubmitting && images.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (isSubmitting) stringResource(Res.string.admin_detail_submitting) else stringResource(Res.string.admin_detail_approve)) }

    OutlinedTextField(
        value = rejectNote,
        onValueChange = viewModel::setRejectNote,
        label = { Text(stringResource(Res.string.admin_detail_reject_note)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { viewModel.reject() },
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(Res.string.admin_detail_reject)) }

    OutlinedButton(onClick = { viewModel.backToList() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.admin_detail_back))
    }
}

@Composable
private fun AdminCrashListStep(
    viewModel: AdminViewModel,
    reports: List<CrashAdminApi.CrashReportDto>,
    isLoading: Boolean,
    listError: io.github.xavitje.signcoach.domain.AppError?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(Res.string.admin_crash_list_title, reports.size), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { viewModel.loadCrashes() }) { Text(stringResource(Res.string.admin_crash_list_refresh)) }
    }
    listError?.let { Text(it.toLocalizedString(), color = MaterialTheme.colorScheme.error) }
    when {
        isLoading && reports.isEmpty() -> CircularProgressIndicator()
        reports.isEmpty() -> Text(
            stringResource(Res.string.admin_crash_list_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reports.forEach { report ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                report.errorType ?: report.errorMessage ?: "—",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    Res.string.admin_crash_list_item_subtitle,
                                    report.deviceModel ?: "?",
                                    report.createdAt ?: "?",
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (report.isFatal) {
                                Text(
                                    stringResource(Res.string.admin_crash_fatal_badge),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Button(onClick = { viewModel.selectCrash(report) }) { Text(stringResource(Res.string.admin_list_view)) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = { viewModel.backToSamplesFromCrashes() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.admin_back_to_samples))
    }
}

@Composable
private fun AdminCrashDetailStep(
    viewModel: AdminViewModel,
    report: CrashAdminApi.CrashReportDto,
) {
    Text(stringResource(Res.string.admin_crash_detail_title), style = MaterialTheme.typography.titleMedium)
    Text(report.errorType ?: report.errorMessage ?: "—", style = MaterialTheme.typography.titleSmall)
    report.errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        report.userId?.let { stringResource(Res.string.admin_crash_detail_user, it) }
            ?: stringResource(Res.string.admin_crash_detail_user_unknown),
    )
    Text(stringResource(Res.string.admin_crash_detail_device, report.deviceModel ?: "?", report.osVersion ?: "?"))
    report.appVersion?.let { Text(stringResource(Res.string.admin_crash_detail_app_version, it)) }
    report.createdAt?.let { Text(stringResource(Res.string.admin_crash_detail_time, it)) }

    Text(stringResource(Res.string.admin_crash_detail_stack_trace), style = MaterialTheme.typography.titleSmall)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Text(
            report.stackTrace ?: "—",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    OutlinedButton(onClick = { viewModel.backToCrashList() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.admin_crash_detail_back))
    }
}
