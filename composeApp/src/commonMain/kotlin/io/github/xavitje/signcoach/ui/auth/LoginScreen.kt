package io.github.xavitje.signcoach.ui.auth
import org.jetbrains.compose.resources.getString

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }

    val networkError = state.error?.let { 
        if (it is AppError.NetworkTimeout || it is AppError.Unknown) it else null
    }
    
    LaunchedEffect(networkError) {
        networkError?.let {
            snackbarHostState.showSnackbar(getString(Res.string.error_network_generic))
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
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
                Spacer(Modifier.height(64.dp))

                // Logo
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(Amber500),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🤟", style = MaterialTheme.typography.headlineLarge)
                }
                Spacer(Modifier.height(20.dp))
                Text("SignCoach", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    stringResource(
                        // Volgt de gekozen gebarentaal, zodat er niet "Nederlandse Gebarentaal"
                        // staat terwijl de leerling ASL heeft gekozen.
                        if (io.github.xavitje.signcoach.data.local.SessionStore.getSignLanguage() == "asl") Res.string.login_subtitle_asl
                        else Res.string.login_subtitle
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(40.dp))

                // Formulier
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!showForgotPassword) {
                            Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.titleLarge)

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; viewModel.clearError() },
                                label = { Text(stringResource(Res.string.login_email_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                isError = state.error is AppError.FieldEmpty || state.error is AppError.InvalidCredentials
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; viewModel.clearError() },
                                label = { Text(stringResource(Res.string.login_password_label)) },
                                trailingIcon = {
                                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Text(if (passwordVisible) stringResource(Res.string.login_password_hide) else stringResource(Res.string.login_password_show), style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    viewModel.signIn(email, password)
                                }),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                isError = state.error is AppError.FieldEmpty || state.error is AppError.InvalidCredentials
                            )

                            val inlineError = state.error?.takeIf { it is AppError.FieldEmpty || it is AppError.InvalidCredentials }
                            if (inlineError != null) {
                                Text(
                                    text = inlineError.toLocalizedString(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            TextButton(
                                onClick = { showForgotPassword = true; forgotEmail = email; viewModel.clearError() },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(Res.string.login_forgot_password), style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = { focusManager.clearFocus(); viewModel.signIn(email, password) },
                                enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                // Ook uitgeschakeld leesbaar (standaard: 38% tekst op 12% vlak, nauwelijks zichtbaar op plum).
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                ),
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(Res.string.login_button), style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(stringResource(Res.string.login_or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }

                            GoogleSignInButton(
                                onClick = {
                                    val url = viewModel.beginGoogleSignIn()
                                    io.github.xavitje.signcoach.platform.BrowserLauncher.open(url)
                                },
                                enabled = !state.isLoading,
                            )

                        } else {
                            Text(stringResource(Res.string.forgot_password_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(Res.string.forgot_password_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = forgotEmail,
                                onValueChange = { forgotEmail = it; viewModel.clearError() },
                                label = { Text(stringResource(Res.string.login_email_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.resetPassword(forgotEmail) }),
                                singleLine = true,
                                isError = state.error is AppError.FieldEmpty
                            )
                            val inlineError = state.error?.takeIf { it is AppError.FieldEmpty }
                            if (inlineError != null) {
                                Text(
                                    text = inlineError.toLocalizedString(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            Button(
                                onClick = { focusManager.clearFocus(); viewModel.resetPassword(forgotEmail) },
                                enabled = !state.isLoading && forgotEmail.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                // Ook uitgeschakeld leesbaar (standaard: 38% tekst op 12% vlak, nauwelijks zichtbaar op plum).
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                ),
                            ) { Text(stringResource(Res.string.forgot_password_button)) }
                            TextButton(onClick = { showForgotPassword = false; viewModel.clearError() }) { Text(stringResource(Res.string.forgot_password_back)) }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                AnimatedVisibility(visible = !showForgotPassword, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.login_no_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onNavigateToRegister) { Text(stringResource(Res.string.login_register_button)) }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(stringResource(Res.string.login_google_button), style = MaterialTheme.typography.labelLarge)
    }
}
