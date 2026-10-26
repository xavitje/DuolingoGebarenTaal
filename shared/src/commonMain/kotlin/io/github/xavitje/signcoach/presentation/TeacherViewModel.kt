package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.FeedbackApi
import io.github.xavitje.signcoach.data.remote.SchoolApi
import io.github.xavitje.signcoach.domain.print.LoginCard
import io.github.xavitje.signcoach.domain.print.LoginCards
import io.github.xavitje.signcoach.platform.printHtml
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
    /** Pilotcijfers van de school, nieuwste week eerst (fase 5). */
    val weekStats: List<io.github.xavitje.signcoach.data.remote.PilotApi.WeekStats> = emptyList(),
    /** Tijd voor het feedbackformulier (14 dagen na start, daarna elke 4 weken). */
    val feedbackDue: Boolean = false,
    /** Net feedback verstuurd: bedankje tonen. */
    val feedbackSent: Boolean = false,
)

/** Tabblad "Klas" voor docenten en schoolbeheerders. */
class TeacherViewModel(
    private val api: SchoolApi = SchoolApi(),
    private val pilotApi: io.github.xavitje.signcoach.data.remote.PilotApi = io.github.xavitje.signcoach.data.remote.PilotApi(),
    private val feedbackApi: FeedbackApi = FeedbackApi(),
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
            val stats = if (school != null) pilotApi.schoolStats(token, school.schoolId).getOrNull().orEmpty() else emptyList()
            val due = school != null && feedbackApi.isDue(token).getOrDefault(false)
            _state.update {
                it.copy(
                    isLoading = false,
                    school = school,
                    classes = classes ?: emptyList(),
                    weekStats = stats,
                    feedbackDue = due,
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
                    // Pincodes staan altijd in de klaslijst: alleen de lijst bijwerken.
                    _state.update { s ->
                        s.copy(isBusy = false, students = s.students.map { if (it.userId == student.userId) it.copy(pin = pin) else it })
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

    /** Camera aan/uit voor één leerling (bv. als ouders geen camera willen). Zonder camera
     * oefent de leerling met de zelfcheck. */
    fun setCamera(student: SchoolApi.StudentProgress, allowed: Boolean) {
        val token = token() ?: return
        val schoolClass = _state.value.selectedClass ?: return
        // Direct tonen; bij een fout zet refreshStudents het terug.
        _state.update { s -> s.copy(students = s.students.map { if (it.userId == student.userId) it.copy(cameraAllowed = allowed) else it }) }
        scope.launch {
            api.setStudentCamera(token, schoolClass.id, student.userId, allowed)
                .onFailure { e -> _state.update { it.withError(e) }; refreshStudents() }
        }
    }

    /** Opent het printvenster met een inlogkaartje per leerling van de geopende klas. */
    fun printLoginCards(): Boolean {
        val schoolClass = _state.value.selectedClass ?: return false
        val cards = _state.value.students.map { LoginCard(it.firstName, it.pin) }
        if (cards.isEmpty()) return false
        return printHtml("SignCoach inlogkaartjes ${schoolClass.name}", LoginCards.html(schoolClass.name, schoolClass.code, cards))
    }

    fun submitFeedback(answers: FeedbackApi.Answers) {
        val token = token() ?: return
        _state.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            feedbackApi.submit(token, answers)
                .onSuccess { _state.update { it.copy(isBusy = false, feedbackDue = false, feedbackSent = true) } }
                .onFailure { e -> _state.update { it.copy(isBusy = false).withError(e) } }
        }
    }

    fun dismissFeedbackThanks() {
        _state.update { it.copy(feedbackSent = false) }
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
