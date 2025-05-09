package com.skushagra.selfselect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource // Import painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skushagra.selfselect.R // Make sure to import your R file

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.CHAT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My App") },
                actions = {
                    // Navigation icons/buttons
                    IconButton(onClick = { currentScreen = AppScreen.CHAT }) {
                       Icon(
                            imageVector = Icons.Default.Edit, // Using a default icon as an example
                            contentDescription = "Chat" // Using Edit for Chat as an example
                        )
                    }
                    IconButton(onClick = { currentScreen = AppScreen.ACTOR }) {
                       Icon(
                            imageVector = Icons.Default.Person, // Using a default icon for actor
                            contentDescription = "Actor" // Content description for actor
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                AppScreen.CHAT -> ChatScreen() // Your existing ChatScreen composable
                AppScreen.ACTOR -> ActorScreen() // The new ActorScreen composable
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}