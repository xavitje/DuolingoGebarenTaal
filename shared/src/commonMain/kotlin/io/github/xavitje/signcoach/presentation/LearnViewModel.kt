package io.github.xavitje.signcoach.presentation

import io.github.xavitje.signcoach.data.local.SessionStore
import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.data.model.CurriculumUnit
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.repository.CurriculumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnUiState(
    val units: List<CurriculumUnit> = emptyList(),
    val isLoading: Boolean = true,
    /** Het geselecteerde gebaar waarvoor het oefen-scherm geopend moet worden. Null = geen. */
    val selectedGesture: CurriculumGesture? = null,
)

/**
 * ViewModel voor de Leren-tab. Laadt het curriculum en beheert de geselecteerde les.
 *
 * [availableTemplates] wordt vanuit [MainScreen] doorgegeven zodra de SyncingTemplateRepository
 * klaar is — zo weet het leerpad welke gebaren al een herkenningstemplate hebben en oefenbaar zijn.
 */
class LearnViewModel(
    private val curriculumRepository: CurriculumRepository = CurriculumRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    fun loadCurriculum(availableTemplates: List<SignTemplate>) {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val language = SignLanguage.fromCode(SessionStore.getSignLanguage())
            val units = curriculumRepository.load(availableTemplates, language)
            _state.update { it.copy(units = units, isLoading = false) }
        }
    }

    fun selectGesture(gesture: CurriculumGesture) {
        _state.update { it.copy(selectedGesture = gesture) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedGesture = null) }
    }
}
