package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.remote.SchoolApi
import io.github.xavitje.signcoach.domain.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudentLoginStep { CODE, NAME, PIN }

enum class StudentLoginError { INVALID_CODE, WRONG_PIN, LOCKED, NETWORK }

data class StudentLoginUiState(
    val step: StudentLoginStep = StudentLoginStep.CODE,
    val classCode: String = "",
    val roster: List<SchoolApi.RosterEntry> = emptyList(),
    val selected: SchoolApi.RosterEntry? = null,
    val pin: String = "",
    val isLoading: Boolean = false,
    val error: StudentLoginError? = null,
)

/**
 * Inloggen voor leerlingen in drie stappen: klascode → naam kiezen → 4-cijferige pin.
 * Bij een juiste pin geeft de server een gewone sessie terug, die [AuthViewModel] overneemt.
 */
class StudentLoginViewModel(
    private val authViewModel: AuthViewModel,
    private val api: SchoolApi = SchoolApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(StudentLoginUiState())
    val state: StateFlow<StudentLoginUiState> = _state.asStateFlow()

    fun submitCode(code: String) {
        val clean = code.trim().uppercase()
        if (clean.length != 6) {
            _state.update { it.copy(error = StudentLoginError.INVALID_CODE) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, classCode = clean) }
        scope.launch {
            api.classRoster(clean)
                .onSuccess { roster ->
                    _state.update {
                        if (roster.isEmpty()) it.copy(isLoading = false, error = StudentLoginError.INVALID_CODE)
                        else it.copy(isLoading = false, roster = roster, step = StudentLoginStep.NAME)
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false, error = StudentLoginError.NETWORK) } }
        }
    }

    fun selectStudent(entry: SchoolApi.RosterEntry) {
        _state.update { it.copy(selected = entry, pin = "", error = null, step = StudentLoginStep.PIN) }
    }

    fun enterDigit(digit: Char) {
        val current = _state.value
        if (current.isLoading || current.pin.length >= 4 || !digit.isDigit()) return
        val pin = current.pin + digit
        _state.update { it.copy(pin = pin, error = null) }
        if (pin.length == 4) login(pin)
    }

    fun deleteDigit() {
        _state.update { it.copy(pin = it.pin.dropLast(1), error = null) }
    }

    fun back() {
        _state.update {
            when (it.step) {
                StudentLoginStep.PIN -> it.copy(step = StudentLoginStep.NAME, pin = "", error = null)
                StudentLoginStep.NAME -> it.copy(step = StudentLoginStep.CODE, roster = emptyList(), error = null)
                StudentLoginStep.CODE -> it
            }
        }
    }

    private fun login(pin: String) {
        val s = _state.value
        val student = s.selected ?: return
        _state.update { it.copy(isLoading = true) }
        scope.launch {
            api.studentLogin(s.classCode, student.userId, pin)
                .onSuccess { session ->
                    authViewModel.completeExternalLogin(session)
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    val error = when {
                        e is AppError.Unauthorized -> StudentLoginError.WRONG_PIN
                        e is AppError.ServerError && e.statusCode == 423 -> StudentLoginError.LOCKED
                        else -> StudentLoginError.NETWORK
                    }
                    _state.update { it.copy(isLoading = false, pin = "", error = error) }
                }
        }
    }
}
