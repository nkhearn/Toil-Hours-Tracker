package com.nkhearn25.toiltracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ToilTrackerUiState(
    val config: ToilTrackerLogic.Config? = null,
    val metrics: Map<String, Any>? = null,
    val isLoading: Boolean = true
)

class ToilTrackerViewModel(private val logic: ToilTrackerLogic) : ViewModel() {

    private val _uiState = MutableStateFlow(ToilTrackerUiState())
    val uiState: StateFlow<ToilTrackerUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val config = logic.loadData()
            val metrics = logic.calculateMetrics(config)
            _uiState.value = ToilTrackerUiState(config, metrics, false)
        }
    }

    fun updateConfig(
        contractHours: Double,
        startDate: String,
        endMonth: Int,
        endDay: Int,
        defaultWeek: Map<String, Double>
    ) {
        viewModelScope.launch {
            val defaultWeekJson = com.google.gson.Gson().toJson(defaultWeek)
            val config = logic.updateConfig(contractHours, startDate, endMonth, endDay, defaultWeekJson)
            val metrics = logic.calculateMetrics(config)
            _uiState.value = ToilTrackerUiState(config, metrics, false)
        }
    }

    fun saveAdjustment(date: String, offset: Double, note: String) {
        viewModelScope.launch {
            val config = logic.saveAdjustment(date, offset, note)
            val metrics = logic.calculateMetrics(config)
            _uiState.value = ToilTrackerUiState(config, metrics, false)
        }
    }

    fun deleteAdjustment(date: String) {
        viewModelScope.launch {
            val config = logic.deleteAdjustment(date)
            val metrics = logic.calculateMetrics(config)
            _uiState.value = ToilTrackerUiState(config, metrics, false)
        }
    }
}
