package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.remote.SchoolApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminSchoolsMessage { SAVED, STAFF_ADDED, STAFF_NOT_FOUND, PURGED, ERROR }

data class AdminSchoolsUiState(
    val isLoading: Boolean = true,
    val schools: List<SchoolApi.AdminSchool> = emptyList(),
    val selected: SchoolApi.AdminSchool? = null,
    val staff: List<SchoolApi.StaffMember> = emptyList(),
    val isBusy: Boolean = false,
    val message: AdminSchoolsMessage? = null,
)

/** Beheerscherm voor scholen, licenties en docenten (alleen admins; de server controleert dat). */
class AdminSchoolsViewModel(
    private val api: SchoolApi = SchoolApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(AdminSchoolsUiState())
    val state: StateFlow<AdminSchoolsUiState> = _state.asStateFlow()

    private fun token(): String? = SessionStore.load()?.accessToken

    fun load() {
        val token = token() ?: return
        _state.update { it.copy(isLoading = true) }
        scope.launch {
            api.adminListSchools(token)
                .onSuccess { list ->
                    _state.update { s ->
                        s.copy(isLoading = false, schools = list, selected = s.selected?.let { sel -> list.firstOrNull { it.id == sel.id } })
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false, message = AdminSchoolsMessage.ERROR) } }
        }
    }

    fun createSchool(name: String, brin: String, contactName: String, contactEmail: String, maxStudents: Int, licenseEnd: String) {
        val token = token() ?: return
        _state.update { it.copy(isBusy = true) }
        scope.launch {
            api.adminCreateSchool(token, name, brin, contactName, contactEmail, maxStudents, licenseEnd)
                .onSuccess { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.SAVED) }; load() }
                .onFailure { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.ERROR) } }
        }
    }

    fun openSchool(school: SchoolApi.AdminSchool) {
        _state.update { it.copy(selected = school, staff = emptyList()) }
        loadStaff()
    }

    fun closeSchool() {
        _state.update { it.copy(selected = null, staff = emptyList()) }
    }

    private fun loadStaff() {
        val token = token() ?: return
        val school = _state.value.selected ?: return
        scope.launch {
            api.adminListStaff(token, school.id)
                .onSuccess { list -> _state.update { it.copy(staff = list) } }
                .onFailure { _state.update { it.copy(message = AdminSchoolsMessage.ERROR) } }
        }
    }

    fun updateLicense(maxStudents: Int, licenseEnd: String) {
        val token = token() ?: return
        val school = _state.value.selected ?: return
        _state.update { it.copy(isBusy = true) }
        scope.launch {
            api.adminUpdateSchool(token, school.id, maxStudents, licenseEnd)
                .onSuccess { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.SAVED) }; load() }
                .onFailure { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.ERROR) } }
        }
    }

    fun addStaff(email: String, staffRole: String) {
        val token = token() ?: return
        val school = _state.value.selected ?: return
        if (email.isBlank()) return
        _state.update { it.copy(isBusy = true) }
        scope.launch {
            api.adminAddStaff(token, school.id, email, staffRole)
                .onSuccess { result ->
                    val msg = if (result == "ok") AdminSchoolsMessage.STAFF_ADDED else AdminSchoolsMessage.STAFF_NOT_FOUND
                    _state.update { it.copy(isBusy = false, message = msg) }
                    loadStaff(); load()
                }
                .onFailure { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.ERROR) } }
        }
    }

    fun removeStaff(member: SchoolApi.StaffMember) {
        val token = token() ?: return
        val school = _state.value.selected ?: return
        scope.launch {
            api.adminRemoveStaff(token, school.id, member.userId)
                .onSuccess { loadStaff(); load() }
                .onFailure { _state.update { it.copy(message = AdminSchoolsMessage.ERROR) } }
        }
    }

    /** Wist alle leerlinggegevens van de geopende school. Onomkeerbaar; de UI vraagt eerst om bevestiging. */
    fun purgeSchoolData() {
        val token = token() ?: return
        val school = _state.value.selected ?: return
        _state.update { it.copy(isBusy = true) }
        scope.launch {
            api.adminPurgeSchool(token, school.id)
                .onSuccess { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.PURGED) }; loadStaff(); load() }
                .onFailure { _state.update { it.copy(isBusy = false, message = AdminSchoolsMessage.ERROR) } }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
