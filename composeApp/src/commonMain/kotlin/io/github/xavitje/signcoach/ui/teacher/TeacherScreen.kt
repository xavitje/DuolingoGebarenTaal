package io.github.xavitje.signcoach.ui.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xavitje.signcoach.data.remote.SchoolApi
import io.github.xavitje.signcoach.presentation.TeacherError
import io.github.xavitje.signcoach.presentation.TeacherViewModel
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.ui.common.PlatformBackHandler
import io.github.xavitje.signcoach.ui.theme.Clover
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.Gold
import io.github.xavitje.signcoach.ui.theme.Plum
import org.jetbrains.compose.resources.stringResource

/**
 * Tabblad "Klas" voor docenten en schoolbeheerders: klassen met klascode, leerlingen toevoegen
 * (pincodes één keer tonen), voortgang per leerling, pincode resetten en leerling verwijderen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(viewModel: TeacherViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    var showNewClass by remember { mutableStateOf(false) }
    var showAddStudents by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<SchoolApi.StudentProgress?>(null) }

    val selected = state.selectedClass
    PlatformBackHandler(enabled = selected != null) { viewModel.closeClass() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.name ?: stringResource(Res.string.tab_class)) },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = viewModel::closeClass) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.coach_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { if (selected != null) viewModel.refreshStudents() else viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.teacher_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Plum),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val school = state.school
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Coral)
                school == null -> EmptyMessage(
                    title = stringResource(Res.string.teacher_no_school_title),
                    desc = stringResource(Res.string.teacher_no_school_desc),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!school.licenseActive) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    stringResource(Res.string.teacher_license_expired, school.schoolName, school.licenseEnd),
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                    state.error?.let { error ->
                        item {
                            Text(errorText(error, state.seatsLeft), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral) }

                    if (selected == null) {
                        item {
                            Text(school.schoolName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        if (state.classes.isEmpty()) {
                            item { Text(stringResource(Res.string.teacher_no_classes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(state.classes, key = { it.id }) { schoolClass ->
                            Card(onClick = { viewModel.openClass(schoolClass) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp)) {
                                    Text(schoolClass.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(stringResource(Res.string.teacher_class_code, schoolClass.code), color = Gold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = { showNewClass = true },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.height(0.dp))
                                Text(stringResource(Res.string.teacher_new_class), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    } else {
                        item {
                            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        selected.code,
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 6.sp,
                                        color = Gold,
                                    )
                                    Text(stringResource(Res.string.teacher_class_code_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(Res.string.teacher_students_count, state.students.size), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = { showAddStudents = true },
                                enabled = school.licenseActive && !state.isBusy,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                            ) { Text(stringResource(Res.string.teacher_add_students)) }
                        }
                        if (state.students.isEmpty() && !state.isBusy) {
                            item { Text(stringResource(Res.string.teacher_no_students), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(state.students, key = { it.userId }) { student ->
                            StudentRow(
                                student = student,
                                onResetPin = { viewModel.resetPin(student) },
                                onRemove = { confirmRemove = student },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewClass) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewClass = false },
            title = { Text(stringResource(Res.string.teacher_new_class)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(Res.string.teacher_class_name_label)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.createClass(name); showNewClass = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(Res.string.teacher_create))
                }
            },
            dismissButton = { TextButton(onClick = { showNewClass = false }) { Text(stringResource(Res.string.teacher_cancel)) } },
        )
    }

    if (showAddStudents) {
        var names by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddStudents = false },
            title = { Text(stringResource(Res.string.teacher_add_students)) },
            text = {
                OutlinedTextField(
                    value = names,
                    onValueChange = { names = it },
                    label = { Text(stringResource(Res.string.teacher_add_students_hint)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addStudents(names); showAddStudents = false }, enabled = names.isNotBlank()) {
                    Text(stringResource(Res.string.teacher_create))
                }
            },
            dismissButton = { TextButton(onClick = { showAddStudents = false }) { Text(stringResource(Res.string.teacher_cancel)) } },
        )
    }

    confirmRemove?.let { student ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(Res.string.teacher_remove_confirm_title, student.firstName)) },
            text = { Text(stringResource(Res.string.teacher_remove_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeStudent(student); confirmRemove = null }) {
                    Text(stringResource(Res.string.teacher_remove_student), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(Res.string.teacher_cancel)) } },
        )
    }

    state.newPins?.let { pins ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.teacher_pins_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.teacher_pins_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    selected?.let {
                        Text(stringResource(Res.string.teacher_class_code, it.code), fontWeight = FontWeight.Bold, color = Gold)
                    }
                    pins.forEach { p ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.firstName, style = MaterialTheme.typography.titleMedium)
                            Text(p.pin, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissPins) { Text(stringResource(Res.string.teacher_done)) } },
        )
    }
}

@Composable
private fun StudentRow(student: SchoolApi.StudentProgress, onResetPin: () -> Unit, onRemove: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(student.firstName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.teacher_student_stats, student.completedLessons, student.totalXp, student.currentStreak),
                color = if (student.completedLessons > 0) Clover else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                student.lastPracticeDate?.let { stringResource(Res.string.teacher_last_practice, it) }
                    ?: stringResource(Res.string.teacher_never_practiced),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onResetPin) { Text(stringResource(Res.string.teacher_reset_pin)) }
                TextButton(onClick = onRemove) { Text(stringResource(Res.string.teacher_remove_student), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String, desc: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun errorText(error: TeacherError, seatsLeft: Int?): String = when (error) {
    TeacherError.NO_SEATS -> if (seatsLeft != null) stringResource(Res.string.teacher_error_no_seats, seatsLeft)
        else stringResource(Res.string.teacher_error_no_seats_unknown)
    TeacherError.LICENSE_EXPIRED -> stringResource(Res.string.teacher_error_license)
    TeacherError.NETWORK -> stringResource(Res.string.teacher_error_network)
    TeacherError.GENERIC -> stringResource(Res.string.teacher_error_generic)
}
