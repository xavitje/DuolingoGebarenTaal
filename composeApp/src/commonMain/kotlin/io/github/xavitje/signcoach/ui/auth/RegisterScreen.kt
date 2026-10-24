package io.github.xavitje.signcoach.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.jetbrains.compose.resources.stringResource
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.presentation.AuthViewModel
import io.github.xavitje.signcoach.ui.theme.Amber500
import io.github.xavitje.signcoach.ui.theme.Indigo700
import io.github.xavitje.signcoach.ui.common.toLocalizedString
import io.github.xavitje.signcoach.domain.AppError

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val networkError = state.error?.let { 
        if (it is AppError.NetworkTimeout || it is AppError.Unknown) it else null
    }
    
    LaunchedEffect(networkError) {
        networkError?.let {
            snackbarHostState.showSnackbar("Er is een netwerkfout opgetreden.") // We mapped this
            viewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Indigo700, MaterialTheme.colorScheme.background),
                        startY = 0f,
                        endY = 600f,
                    )
                )
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))

                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Amber500),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🤟", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.register_title_main), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    stringResource(
                        // Volgt de gekozen gebarentaal, zodat er niet "Nederlandse Gebarentaal"
                        // staat terwijl de leerling ASL heeft gekozen.
                        if (io.github.xavitje.signcoach.data.local.SessionStore.getSignLanguage() == "asl") Res.string.register_subtitle_asl
                        else Res.string.register_subtitle
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(32.dp))

                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(Res.string.register_title), style = MaterialTheme.typography.titleLarge)

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; viewModel.clearError() },
                            label = { Text(stringResource(Res.string.login_email_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = state.error is AppError.FieldEmpty
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; viewModel.clearError() },
                            label = { Text(stringResource(Res.string.register_password_label)) },
                            trailingIcon = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(if (passwordVisible) stringResource(Res.string.login_password_hide) else stringResource(Res.string.login_password_show), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = state.error is AppError.FieldEmpty || state.error is AppError.PasswordTooShort
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; viewModel.clearError() },
                            label = { Text(stringResource(Res.string.register_confirm_password_label)) },
                            trailingIcon = {
                                TextButton(onClick = { confirmVisible = !confirmVisible }) {
                                    Text(if (confirmVisible) stringResource(Res.string.login_password_hide) else stringResource(Res.string.login_password_show), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                viewModel.signUp(email, password, confirmPassword)
                            }),
                            isError = (confirmPassword.isNotEmpty() && password != confirmPassword) || state.error is AppError.PasswordsDoNotMatch,
                            supportingText = if ((confirmPassword.isNotEmpty() && password != confirmPassword) || state.error is AppError.PasswordsDoNotMatch) {
                                { Text(stringResource(Res.string.error_passwords_do_not_match), color = MaterialTheme.colorScheme.error) }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        
                        val inlineError = state.error?.takeIf { it is AppError.FieldEmpty || it is AppError.PasswordTooShort || it is AppError.InvalidCredentials }
                        if (inlineError != null) {
                            Text(
                                text = inlineError.toLocalizedString(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.signUp(email, password, confirmPassword)
                            },
                            enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(Res.string.register_button), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.register_already_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onNavigateToLogin) { Text(stringResource(Res.string.register_login_button)) }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
