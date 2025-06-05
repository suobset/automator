package com.skushagra.selfselect.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog // Specific import
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.skushagra.selfselect.ChatViewModel
// UiState might not be needed here directly if viewModel handles all state changes

@Composable // Removed @OptIn(ExperimentalMaterial3Api::class) as AlertDialog is stable
fun ApiKeyEntryDialog(viewModel: ChatViewModel) {
    var apiKeyInput by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val geminiKeyUrl = "https://makersuite.google.com/app/apikey"

    AlertDialog(
        onDismissRequest = {
            // Making it non-dismissible by click outside or back press as per original plan.
            // If it needs to be dismissible, viewModel should handle the state change.
        },
        title = {
            Text("Enter Gemini API Key")
        },
        text = {
            Column {
                Text("Automator requires a Gemini API key to function. Please enter your key below.")
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    try { uriHandler.openUri(geminiKeyUrl) } catch (e: Exception) {
                        viewModel.updateUiStateToError("Could not open URL: ${e.message}")
                    }
                }) {
                    Text("How to get an API Key?")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.submitApiKey(apiKeyInput.text.trim(), context)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = {
                viewModel.updateUiStateToError("API Key not provided. Automator functionality may be limited.")
                // The dialog will disappear if the uiState is no longer ShowApiKeyDialog
            }) {
                Text("Cancel")
            }
        }
        // No modifier, properties, shape, colors, etc. specified to keep it simple and use defaults.
    )
}
