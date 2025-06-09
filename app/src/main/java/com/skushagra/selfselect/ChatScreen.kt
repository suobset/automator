package com.skushagra.selfselect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog // Import AlertDialog
import com.skushagra.selfselect.ui.ApiKeyEntryDialog // Import the ApiKeyEntryDialog

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    val chatHistory by chatViewModel.chatHistory.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val context = LocalContext.current // Get context for clipboard manager
    val actorViewModel = remember { ActorViewModel() }
    // State to control the dialog visibility and content
    var showYamlDialog by rememberSaveable { mutableStateOf(false) }
    var yamlToCopy by rememberSaveable { mutableStateOf("") }

    // Observe uiState for changes, and update dialog state
    LaunchedEffect(uiState) {
        when (val currentState = uiState) { // Assign uiState to currentState for smart casting
            is UiState.ShowYamlDialog -> {
                showYamlDialog = true
                yamlToCopy = currentState.yaml
            }
            is UiState.ShowApiKeyDialog -> {
                // This state is now handled by the main Column's when(uiState) block
                // No specific action needed here, but kept for clarity if future needs arise
            }
            else -> {
                showYamlDialog = false // Dismiss YAML dialog for other states
                // yamlToCopy = "" // Not strictly necessary to clear here as it's only used when showYamlDialog is true
           }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Display ApiKeyEntryDialog if the state is ShowApiKeyDialog
        if (uiState is UiState.ShowApiKeyDialog) {
            ApiKeyEntryDialog(viewModel = chatViewModel)
        } else {
            // Existing chat UI
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                reverseLayout = true
            ) {
                items(chatHistory.reversed()) { message ->
                    val isUser = message.sender == Sender.USER
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = if (isUser) "You" else "Automator",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium,
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .widthIn(max = 300.dp)
                        ) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                TextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Message Automator...") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                Button(
                    onClick = {
                        chatViewModel.sendMessage(prompt)
                        prompt = ""
                    },
                    enabled = prompt.isNotBlank(), // Disable button
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text("Send")
                }
            }
        }
    }

    // Show YAML dialog when showYamlDialog is true AND ApiKeyDialog is not showing
    if (showYamlDialog && yamlToCopy.isNotBlank() && uiState !is UiState.ShowApiKeyDialog) {
        AlertDialog(
            onDismissRequest = {
                showYamlDialog = false
                // yamlToCopy = "" // Not strictly needed here
                chatViewModel.onDialogResult(false) // Notify ViewModel of dismissal
            },
            title = { Text("YAML Content") },
            text = {
                Text(text = yamlToCopy, modifier = Modifier.padding(8.dp))
            },
            confirmButton = {
                Button(
                    onClick = {
                        actorViewModel.executeAction(context, yamlToCopy)
                        chatViewModel.onDialogResult(false) // Notify ViewModel of copy
                        showYamlDialog = false // Dismiss dialog
                        // yamlToCopy = ""
                    }) {
                    Text("Execute")
                }
            },
            dismissButton = {
                Button(onClick = {
                    // yamlToCopy = ""
                    actorViewModel.copyAction(context, yamlToCopy)
                    chatViewModel.onDialogResult(true) // Notify ViewModel of ignore
                    showYamlDialog = false
                }) {
                    Text("Copy")
                }
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun ChatScreenPreview() {
   ChatScreen()
}
