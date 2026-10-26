package io.github.xavitje.signcoach.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.data.remote.SchoolApi
import io.github.xavitje.signcoach.presentation.AdminSchoolsMessage
import io.github.xavitje.signcoach.presentation.AdminSchoolsViewModel
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.ui.common.PlatformBackHandler
import io.github.xavitje.signcoach.ui.theme.Clover
import io.github.xavitje.signcoach.ui.theme.Plum
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Beheer van scholen, licenties en docenten (alleen admins). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSchoolsScreen(viewModel: AdminSchoolsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val snackbar = remember { SnackbarHostState() }
    var showNew by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(
            getString(
                when (msg) {
                    AdminSchoolsMessage.SAVED -> Res.string.admin_msg_saved
                    AdminSchoolsMessage.STAFF_ADDED -> Res.string.admin_msg_staff_added
                    AdminSchoolsMessage.STAFF_NOT_FOUND -> Res.string.admin_msg_staff_not_found
                    AdminSchoolsMessage.PURGED -> Res.string.admin_msg_purged
                    AdminSchoolsMessage.ERROR -> Res.string.admin_msg_error
                }
            )
        )
        viewModel.clearMessage()
    }

    val selected = state.selected
    PlatformBackHandler { if (selected != null) viewModel.closeSchool() else onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(selected?.name ?: stringResource(Res.string.admin_schools_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) viewModel.closeSchool() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.coach_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Plum),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.schools.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                selected == null -> SchoolList(state.schools, onOpen = viewModel::openSchool, onNew = { showNew = true })
                else -> SchoolDetail(
                    school = selected,
                    staff = state.staff,
                    busy = state.isBusy,
                    onSaveLicense = viewModel::updateLicense,
                    onAddStaff = viewModel::addStaff,
                    onRemoveStaff = viewModel::removeStaff,
                    onPurge = viewModel::purgeSchoolData,
                )
            }
        }
    }

    if (showNew) NewSchoolDialog(
        onDismiss = { showNew = false },
        onCreate = { name, brin, contact, email, seats, end ->
            viewModel.createSchool(name, brin, contact, email, seats, end)
            showNew = false
        },
    )
}

@Composable
private fun SchoolList(schools: List<SchoolApi.AdminSchool>, onOpen: (SchoolApi.AdminSchool) -> Unit, onNew: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(Res.string.admin_school_new))
            }
        }
        if (schools.isEmpty()) item { Text(stringResource(Res.string.admin_schools_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(schools, key = { it.id }) { s ->
            Card(onClick = { onOpen(s) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (s.licenseActive) Res.string.admin_school_active else Res.string.admin_school_expired),
                            color = if (s.licenseActive) Clover else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        stringResource(Res.string.admin_school_summary, s.seatsUsed, s.maxStudents, s.staffCount, s.licenseEnd),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolDetail(
    school: SchoolApi.AdminSchool,
    staff: List<SchoolApi.StaffMember>,
    busy: Boolean,
    onSaveLicense: (Int, String) -> Unit,
    onAddStaff: (String, String) -> Unit,
    onRemoveStaff: (SchoolApi.StaffMember) -> Unit,
    onPurge: () -> Unit,
) {
    var confirmPurge by remember(school.id) { mutableStateOf(false) }
    var seats by remember(school.id) { mutableStateOf(school.maxStudents.toString()) }
    var end by remember(school.id) { mutableStateOf(school.licenseEnd) }
    var email by remember(school.id) { mutableStateOf("") }
    var role by remember(school.id) { mutableStateOf("teacher") }
    val licenseValid = seats.toIntOrNull() != null && DATE.matches(end)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.admin_school_summary, school.seatsUsed, school.maxStudents, school.staffCount, school.licenseEnd),
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(Res.string.admin_school_license_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = seats, onValueChange = { seats = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(Res.string.admin_school_max_students)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = end, onValueChange = { end = it.take(10) },
            label = { Text(stringResource(Res.string.admin_school_license_end)) },
            isError = !DATE.matches(end), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSaveLicense(seats.toInt(), end) }, enabled = licenseValid && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.admin_school_save))
        }

        Text(stringResource(Res.string.admin_school_staff_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (staff.isEmpty()) Text(stringResource(Res.string.admin_school_staff_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        staff.forEach { member ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(member.email)
                    Text(
                        stringResource(if (member.staffRole == "school_admin") Res.string.admin_role_school_admin else Res.string.admin_role_teacher),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onRemoveStaff(member) }) {
                    Text(stringResource(Res.string.teacher_remove_student), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim() },
            label = { Text(stringResource(Res.string.admin_school_staff_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = role == "teacher", onClick = { role = "teacher" }, label = { Text(stringResource(Res.string.admin_role_teacher)) })
            FilterChip(selected = role == "school_admin", onClick = { role = "school_admin" }, label = { Text(stringResource(Res.string.admin_role_school_admin)) })
        }
        Button(onClick = { onAddStaff(email, role); email = "" }, enabled = email.contains('@') && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.admin_school_add_staff))
        }

        Text(stringResource(Res.string.admin_school_privacy_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_school_privacy_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmPurge = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.admin_school_purge), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text(stringResource(Res.string.admin_school_purge_confirm_title)) },
            text = { Text(stringResource(Res.string.admin_school_purge_confirm_text, school.name)) },
            confirmButton = {
                TextButton(onClick = { confirmPurge = false; onPurge() }) {
                    Text(stringResource(Res.string.admin_school_purge), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = false }) { Text(stringResource(Res.string.contrib_cancel)) }
            },
        )
    }
}

@Composable
private fun NewSchoolDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, Int, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brin by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var seats by remember { mutableStateOf("30") }
    var end by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && seats.toIntOrNull() != null && DATE.matches(end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_school_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.admin_school_name)) }, singleLine = true)
                OutlinedTextField(brin, { brin = it }, label = { Text(stringResource(Res.string.admin_school_brin)) }, singleLine = true)
                OutlinedTextField(contact, { contact = it }, label = { Text(stringResource(Res.string.admin_school_contact_name)) }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text(stringResource(Res.string.admin_school_contact_email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(seats, { seats = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(Res.string.admin_school_max_students)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(end, { end = it.take(10) }, label = { Text(stringResource(Res.string.admin_school_license_end)) }, singleLine = true)
                if (!valid) Text(stringResource(Res.string.admin_school_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, brin, contact, email, seats.toInt(), end) }, enabled = valid) {
                Text(stringResource(Res.string.teacher_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.teacher_cancel)) } },
    )
}
