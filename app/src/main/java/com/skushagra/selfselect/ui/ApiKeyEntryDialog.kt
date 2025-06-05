package com.skushagra.selfselect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.skushagra.selfselect.ChatViewModel

const val geminiKeyUrl = "https://makersuite.google.com/app/apikey"

@Composable
fun ApiKeyEntryDialog(viewModel: ChatViewModel) {
    val apiKey = remember { mutableStateOf(TextFieldValue("")) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* Non-dismissible externally */ },
        title = { Text("Enter API Key") },
        text = {
            Column(horizontalAlignment = Alignment.Start) {
                Text("This app requires a Gemini API key to function. Please enter your key below.")
                Spacer(modifier = Modifier.height(8.dp))
                val annotatedLinkString = buildAnnotatedString {
                    append("You can obtain a key from ")
                    pushStringAnnotation(tag = "URL", annotation = geminiKeyUrl)
                    withStyle(style = SpanStyle(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)) {
                        append("Google AI Studio")
                    }
                    pop()
                    append(".")
                }
                ClickableText(
                    text = annotatedLinkString,
                    onClick = { offset ->
                        annotatedLinkString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKey.value,
                    onValueChange = { apiKey.value = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.submitApiKey(apiKey.value.text, context)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // Update UI state to error or a specific state that MainActivity can use to close the app or show a message
                viewModel.updateUiStateToError("API Key entry cancelled. The app cannot function without an API Key.")
                // Optionally, to signal MainActivity to finish:
                // (context as? Activity)?.finish()
            }) {
                Text("Cancel")
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.Center
    )
}
