package com.example.androidapp.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainMenuScreen(modifier: Modifier = Modifier) {
    // Column ordnet die Elemente untereinander an
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Der Titel des Spiels
        Text(
            text = "Kuhhandel",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Der Start-Button
        Button(
            onClick = { /* TODO: Navigation zur Lobby/Spielersuche */ },
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Spiel starten")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ein Button für die Spielregeln
        OutlinedButton(
            onClick = { /* TODO: Navigation zu den Regeln */ },
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Regeln")
        }
    }
}
