package io.github.xavitje.signcoach.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xavitje.signcoach.presentation.StudentLoginError
import io.github.xavitje.signcoach.presentation.StudentLoginStep
import io.github.xavitje.signcoach.presentation.StudentLoginViewModel
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.ui.common.PlatformBackHandler
import io.github.xavitje.signcoach.ui.theme.Coral
import io.github.xavitje.signcoach.ui.theme.Gold
import io.github.xavitje.signcoach.ui.theme.Plum
import io.github.xavitje.signcoach.ui.theme.PlumDeep
import org.jetbrains.compose.resources.stringResource

/**
 * Inloggen voor leerlingen: klascode → naam kiezen → pincode op een groot cijferblok.
 * Geen e-mail, geen wachtwoord: een kind van 9 moet dit zelf kunnen.
 */
@Composable
fun StudentLoginScreen(
    viewModel: StudentLoginViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    PlatformBackHandler {
        if (state.step == StudentLoginStep.CODE) onClose() else viewModel.back()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PlumDeep)
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (state.step == StudentLoginStep.CODE) onClose() else viewModel.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.student_back), tint = Color.White)
            }
            Text(stringResource(Res.string.student_login_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        when (state.step) {
            StudentLoginStep.CODE -> CodeStep(
                initial = state.classCode,
                isLoading = state.isLoading,
                error = state.error,
                onSubmit = viewModel::submitCode,
            )
            StudentLoginStep.NAME -> NameStep(
                names = state.roster,
                onPick = viewModel::selectStudent,
            )
            StudentLoginStep.PIN -> PinStep(
                name = state.selected?.firstName.orEmpty(),
                pin = state.pin,
                isLoading = state.isLoading,
                error = state.error,
                onDigit = viewModel::enterDigit,
                onDelete = viewModel::deleteDigit,
            )
        }
    }
}

@Composable
private fun ErrorText(error: StudentLoginError?) {
    val text = when (error) {
        StudentLoginError.INVALID_CODE -> stringResource(Res.string.student_error_code)
        StudentLoginError.WRONG_PIN -> stringResource(Res.string.student_error_pin)
        StudentLoginError.LOCKED -> stringResource(Res.string.student_error_locked)
        StudentLoginError.NETWORK -> stringResource(Res.string.student_error_network)
        null -> return
    }
    Text(text, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
private fun CodeStep(initial: String, isLoading: Boolean, error: StudentLoginError?, onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf(initial) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.student_code_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.student_code_hint), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(6) },
            label = { Text(stringResource(Res.string.student_code_label)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 6.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(code) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(code) },
            enabled = code.length == 6 && !isLoading,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Coral,
                contentColor = Color.White,
                disabledContainerColor = Coral.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.8f),
            ),
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(stringResource(Res.string.student_next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NameStep(names: List<io.github.xavitje.signcoach.data.remote.SchoolApi.RosterEntry>, onPick: (io.github.xavitje.signcoach.data.remote.SchoolApi.RosterEntry) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(Res.string.student_name_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(names, key = { it.userId }) { entry ->
                Surface(
                    onClick = { onPick(entry) },
                    shape = RoundedCornerShape(18.dp),
                    color = Plum,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(entry.firstName, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinStep(
    name: String,
    pin: String,
    isLoading: Boolean,
    error: StudentLoginError?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.student_pin_title, name), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) Gold else Color.White.copy(alpha = 0.2f)),
                )
            }
        }
        if (isLoading) CircularProgressIndicator(color = Coral) else ErrorText(error)

        val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', ' ', '0', '<')
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(0.85f)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        Box(Modifier.weight(1f).aspectRatio(1.4f)) {
                            when (key) {
                                ' ' -> Unit
                                '<' -> Surface(
                                    onClick = onDelete,
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(Res.string.student_delete_digit), tint = Color.White)
                                    }
                                }
                                else -> Surface(
                                    onClick = { onDigit(key) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = Plum,
                                    modifier = Modifier.fillMaxSize().semantics { contentDescription = key.toString() },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(key.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
