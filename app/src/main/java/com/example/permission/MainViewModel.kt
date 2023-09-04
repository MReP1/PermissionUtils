package com.example.permission

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class MainUiScreenState {

    data class Failure(
        val refresh: () -> Unit
    ) : MainUiScreenState()

    data class Loading(
        val progress: Float
    ) : MainUiScreenState()

    data class Success(
        val states: List<MainUiContentState>
    ) : MainUiScreenState()

}

sealed class MainUiContentState

class InitServerAddressState(
    val onNextStep: () -> Unit
) : MainUiContentState() {

}

class MainViewModel : ViewModel() {

    private val initStepsCache = mutableListOf<MainUiContentState>()

    private val _uiState: MutableStateFlow<MainUiScreenState> =
        MutableStateFlow(MainUiScreenState.Loading(0F))
    val uiState = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    init {
        initStepsCache.add(InitServerAddressState(::stepTo))
        start()
    }

    private fun start() {
        _currentStep.value = 0
        _uiState.value = MainUiScreenState.Success(initStepsCache)
    }

    private fun stepTo(step: Int = _currentStep.value + 1) {
        if (step >= initStepsCache.size) {
            finish()
            return
        }
        _currentStep.value = step
    }

    private fun finish() {

    }
}