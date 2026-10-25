package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
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

enum class TeacherError { NO_SEATS, LICENSE_EXPIRED, NETWORK, GENERIC }

data class TeacherUiState(
    val isLoading: Boolean = true,
    /** De school waar deze docent/schoolbeheerder bij hoort; null = nog niet gekoppeld. */
    val school: SchoolApi.MySchool? = null,
    val classes: List<SchoolApi.SchoolClass> = emptyList(),
    val selectedClass: SchoolApi.SchoolClass? = null,
    val students: List<SchoolApi.StudentProgress> = emptyList(),
    val isBusy: Boolean = false,
    /** Net aangemaakte leerlingen of een net gereset pin — één keer tonen om op te schrijven. */
    val newPins: List<SchoolApi.CreatedStudent>? = null,
    val error: TeacherError? = null,
    val seatsLeft: Int? = null,
)

/** Tabblad "Klas" voor docenten en schoolbeheerders. */
class TeacherViewModel(
    private val api: SchoolApi = SchoolApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(TeacherUiState())
    val state: StateFlow<TeacherUiState> = _state.asStateFlow()

    private fun token(): String? = SessionStore.load()?.accessToken

    fun load() {
        val token = token() ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            val school = api.mySchool(token).getOrNull()?.firstOrNull { !it.isStudent }
            val classes = if (school != null) {
                api.myClasses(token).getOrNull()?.filter { it.schoolId == school.schoolId }
            } else emptyList()
            _state.update {
                it.copy(
                    isLoading = false,
                    school = school,
                    classes = classes ?: emptyList(),
                    error = if (classes == null) TeacherError.NETWORK else null,
                )
            }
        }
    }

    fun createClass(name: String) {
        val token = token() ?: return
        val school = _state.value.school ?: return
        if (name.isBlank()) return
        _state.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            api.createClass(token, school.schoolId, name)
                .onSuccess { created ->
                    _state.update { it.copy(isBusy = false, classes = (it.classes + created).sortedBy { c -> c.name.lowercase() }) }
                    openClass(created)
                }
                .onFailure { _state.update { it.copy(isBusy = false, error = TeacherError.GENERIC) } }
        }
    }

    fun openClass(schoolClass: SchoolApi.SchoolClass) {
        _state.update { it.copy(selectedClass = schoolClass, students = emptyList(), error = null) }
        refreshStudents()
    }

    fun closeClass() {
        _state.update { it.copy(selectedClass = null, students = emptyList(), error = null) }
    }

    fun refreshStudents() {
        val token = token() ?: return
        val schoolClass = _state.value.selectedClass ?: return
        _state.update { it.copy(isBusy = true) }
        scope.launch {
            api.classOverview(token, schoolClass.id)
                .onSuccess { list -> _state.update { it.copy(isBusy = false, students = list) } }
                .onFailure { _state.update { it.copy(isBusy = false, error = TeacherError.NETWORK) } }
        }
    }

    /** Namen: één per regel (of gescheiden door komma's). */
    fun addStudents(namesText: String) {
        val token = token() ?: return
        val schoolClass = _state.value.selectedClass ?: return
        val names = namesText.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return
        _state.update { it.copy(isBusy = true, error = null, seatsLeft = null) }
        scope.launch {
            api.createStudents(token, schoolClass.id, names)
                .onSuccess { created ->
                    _state.update { it.copy(isBusy = false, newPins = created) }
                    refreshStudents()
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false).withError(e) } }
        }
    }

    fun resetPin(student: SchoolApi.StudentProgress) {
        val token = token() ?: return
        val schoolClass = _state.value.selectedClass ?: return
        _state.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            api.resetPin(token, schoolClass.id, student.userId)
                .onSuccess { pin ->
                    _state.update {
                        it.copy(isBusy = false, newPins = listOf(SchoolApi.CreatedStudent(student.userId, student.firstName, pin)))
                    }
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false).withError(e) } }
        }
    }

    fun removeStudent(student: SchoolApi.StudentProgress) {
        val token = token() ?: return
        val schoolClass = _state.value.selectedClass ?: return
        _state.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            api.removeStudent(token, schoolClass.id, student.userId)
                .onSuccess { refreshStudents() }
                .onFailure { e -> _state.update { it.copy(isBusy = false).withError(e) } }
        }
    }

    fun dismissPins() {
        _state.update { it.copy(newPins = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, seatsLeft = null) }
    }

    private fun TeacherUiState.withError(e: Throwable): TeacherUiState {
        val details = (e as? AppError.ServerError)?.details.orEmpty()
        return when {
            e is AppError.ServerError && e.statusCode == 409 && details.contains("no_seats") -> {
                val left = Regex("\"seats_left\"\\s*:\\s*(\\d+)").find(details)?.groupValues?.get(1)?.toIntOrNull()
                copy(error = TeacherError.NO_SEATS, seatsLeft = left)
            }
            e is AppError.ServerError && e.statusCode == 409 -> copy(error = TeacherError.LICENSE_EXPIRED)
            e is AppError.NetworkUnavailable || e is AppError.NetworkTimeout -> copy(error = TeacherError.NETWORK)
            else -> copy(error = TeacherError.GENERIC)
        }
    }
}
