package com.skushagra.selfselect

/**
 * A sealed hierarchy describing the state of the text generation.
 */
sealed interface UiState {

    /**
     * Empty state when the screen is first shown
     */
    object Initial : UiState

    /**
     * Still loading
     */
    object Loading : UiState

    /**
     * Text has been generated
     */
    data class Success(val outputText: String) : UiState

    /**
     * There was an error generating text
     */
    data class Error(val errorMessage: String) : UiState

    /**
     * Show the YAML dialog
     */
    data class ShowYamlDialog(val yaml: String) : UiState

    /**
     * Show the API key dialog (first run, if no existing keys exist)
     */
    data object ShowApiKeyDialog : UiState
}