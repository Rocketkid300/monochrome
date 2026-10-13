package io.github.rocketkid300.monochromewatch.feature.player.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: DeviceDiagnostics,
) : ViewModel() {

    private val _report = MutableStateFlow(diagnostics.local())
    val report: StateFlow<DiagnosticsReport> = _report.asStateFlow()

    private var probe: Job? = null

    init {
        run()
    }

    /** Re-reads the local facts at once, then fills in endpoint reachability when the probe returns. */
    fun run() {
        probe?.cancel()
        _report.value = diagnostics.local()
        probe = viewModelScope.launch {
            val result = diagnostics.reachability()
            _report.update { it.copy(reachability = result) }
        }
    }
}
