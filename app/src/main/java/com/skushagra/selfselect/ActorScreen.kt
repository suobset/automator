package com.skushagra.selfselect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ActorScreen() {
    var yamlInput by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val actorViewModel = remember { ActorViewModel() }

    val errorState by actorViewModel.errorState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Add some padding around the content
        horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally
    ) {
        // Large Text Box for YAML Input
        OutlinedTextField(
            value = yamlInput,
            onValueChange = { yamlInput = it },
            label = { Text("Enter YAML here...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Make the TextField take up available space
                .padding(bottom = 16.dp), // Add padding below the TextField
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.None // No specific action for the keyboard (like "Done" or "Next")
            )
        )

        // Uneditable Text Box for YAML Errors
        OutlinedTextField(
            value = errorState, // Display the errors
            onValueChange = { /* Do nothing, this is uneditable */ },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Make the TextField take up available space
                .padding(bottom = 16.dp), // Add padding below the TextField
            readOnly = true, // Make the TextField uneditable
            isError = errorState.isNotBlank() // Highlight if there are errors
        )

        // Button below the Text Box
        Button(
            onClick = {
                actorViewModel.executeAction(context, yamlInput)
            },
            // Enable the button only if there is some text in the input field
            enabled = yamlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth() // Make the button fill the width
        ) {
            Text("Start Action")
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun ActorScreenPreview() {
    ActorScreen()
}