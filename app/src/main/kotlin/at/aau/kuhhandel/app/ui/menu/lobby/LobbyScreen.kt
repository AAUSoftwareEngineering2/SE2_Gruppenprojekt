package at.aau.kuhhandel.app.ui.menu.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.audio.LocalButtonClickSound
import at.aau.kuhhandel.app.audio.rememberSoundEffect
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.DefaultPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.app.ui.theme.WhitePurple

@Composable
fun LobbyScreen(
    modifier: Modifier = Modifier,
    uiState: LobbyUiState,
    onStartGame: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    val playClickSound = LocalButtonClickSound.current
    val playPlayerJoinedSound = rememberSoundEffect(R.raw.lobby_player_joined)
    val playPlayerLeftSound = rememberSoundEffect(R.raw.lobby_player_left)
    var previousPlayerCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.players.size) {
        val currentPlayerCount = uiState.players.size
        val previousCount = previousPlayerCount

        if (previousCount != null && previousCount > 0) {
            when {
                currentPlayerCount > previousCount -> playPlayerJoinedSound()
                currentPlayerCount < previousCount -> playPlayerLeftSound()
            }
        }

        previousPlayerCount = currentPlayerCount
    }

    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium,
                            ).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Lobby Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            uiState.lobbyCode,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        "Share this code",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = uiState.connectionStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (!uiState.isError) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.isError && uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            playClickSound()
                            onDismissError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Hide Errors")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Players (${uiState.players.size})",
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.players) { player ->
                        PlayerListItem(player)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.canStartGame) {
                        Button(
                            onClick = {
                                playClickSound()
                                onStartGame()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Start Game")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            playClickSound()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerListItem(player: PlayerDisplayItem) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        if (player.isMe) {
                            WhitePurple
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    shape = RoundedCornerShape(24.dp),
                ).then(
                    if (player.isMe) {
                        Modifier.border(
                            width = 2.dp,
                            color = DefaultPurple,
                            shape = RoundedCornerShape(24.dp),
                        )
                    } else {
                        Modifier
                    },
                ).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DefaultPurple),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    player.name.firstOrNull()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (player.isMe) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        color = LightPurple,
                                        shape = RoundedCornerShape(8.dp),
                                    ).padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "(You)",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkPurple,
                            )
                        }
                    }
                }
                Text(
                    if (player.isHost) "Host" else "Player",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (player.isReady) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Ready",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
