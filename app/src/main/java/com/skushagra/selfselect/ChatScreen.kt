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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog // Import AlertDialog

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    val chatHistory by chatViewModel.chatHistory.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val context = LocalContext.current // Get context for clipboard manager

    // State to control the dialog visibility and content
    var showYamlDialog by rememberSaveable { mutableStateOf(false) }
    var yamlToCopy by rememberSaveable { mutableStateOf("") }

    // Only send the initial message when the composable is first opened
    LaunchedEffect(Unit) {
        chatViewModel.sendInitialMessage()
    }

    // Observe uiState for changes, and update dialog state
    LaunchedEffect(uiState) {
        when (uiState) {
            is UiState.ShowYamlDialog -> {
                showYamlDialog = true
                yamlToCopy = (uiState as UiState.ShowYamlDialog).yaml
            }
            else -> {
                showYamlDialog = false // Dismiss dialog for other states
                yamlToCopy = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
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
                    .padding(end = 8.dp)
            )
            Button(
                onClick = {
                    chatViewModel.sendMessage(prompt)
                    prompt = ""
                },
                enabled = prompt.isNotBlank(),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text("Send")
            }
        }
    }

    // Show dialog when showYamlDialog is true
    if (showYamlDialog && yamlToCopy.isNotBlank()) {
        AlertDialog(
            onDismissRequest = {
                showYamlDialog = false
                yamlToCopy = ""
                chatViewModel.onDialogResult(false) // Notify ViewModel of dismissal
            },
            title = { Text("YAML Content") },
            text = {
                // Use a SelectableText to allow copying
                Text(text = yamlToCopy, modifier = Modifier.padding(8.dp))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboardManager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("YAML", yamlToCopy)
                        clipboardManager.setPrimaryClip(clipData)
                        // Consider showing a Toast here, or let the ViewModel handle it
                        chatViewModel.onDialogResult(true) // Notify ViewModel of copy
                        showYamlDialog = false // Dismiss dialog
                        yamlToCopy = ""

                    }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showYamlDialog = false
                    yamlToCopy = ""
                    chatViewModel.onDialogResult(false) // Notify ViewModel of ignore
                }) {
                    Text("Ignore")
                }
            },
            // Prevent dialog from being dismissed by tapping outside.
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen()
}
