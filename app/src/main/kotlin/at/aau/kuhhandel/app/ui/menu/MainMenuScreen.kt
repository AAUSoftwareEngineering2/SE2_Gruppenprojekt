package at.aau.kuhhandel.app.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.theme.DarkPurple

@Composable
fun MainMenuScreen(modifier: Modifier = Modifier) {
    val currentScreen = remember { mutableStateOf<MenuScreenState>(MenuScreenState.Main) }

    when (currentScreen.value) {
        MenuScreenState.Main ->
            MainMenuContent(
                modifier = modifier,
                onCreateLobby = { currentScreen.value = MenuScreenState.RoomCreation },
                onJoinLobby = { currentScreen.value = MenuScreenState.RoomJoining },
                onRules = { currentScreen.value = MenuScreenState.Rules },
            )
        MenuScreenState.RoomCreation ->
            RoomCreationScreen(
                modifier = modifier,
                onBack = { currentScreen.value = MenuScreenState.Main },
                onLobbyCreated = { lobbyCode ->
                    currentScreen.value = MenuScreenState.Lobby(lobbyCode)
                },
            )
        MenuScreenState.RoomJoining ->
            RoomJoiningScreen(
                modifier = modifier,
                onBack = { currentScreen.value = MenuScreenState.Main },
                onLobbyJoined = { lobbyCode ->
                    currentScreen.value = MenuScreenState.Lobby(lobbyCode)
                },
            )
        is MenuScreenState.Lobby ->
            LobbyScreen(
                modifier = modifier,
                lobbyCode = (currentScreen.value as MenuScreenState.Lobby).lobbyCode,
                onBack = { currentScreen.value = MenuScreenState.Main },
            )
        MenuScreenState.Rules ->
            RulesScreen(
                modifier = modifier,
                onBack = { currentScreen.value = MenuScreenState.Main },
            )
    }
}

@Composable
private fun MainMenuContent(
    modifier: Modifier = Modifier,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
    onRules: () -> Unit,
) {
    // ==========================================================
    // PARAMETERS
    // ==========================================================

    val signSize = 1.5f
    val signOffsetX = 170.dp
    val signOffsetY = 220.dp

    val buttonOffsetX = -0.8f
    val buttonOffsetY = -0.9f
    val buttonWidth = 0.40f
    val buttonSpacing = (-75).dp

    // ==========================================================

    Box(modifier = modifier.fillMaxSize()) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.bg_grass),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Title
        //TODO: Fancy drawable later
        Text(
            text = "Kuhhandel",
            style = MaterialTheme.typography.displayLarge,
            color = DarkPurple,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        )

        // 2. CONTAINER (Sign + Buttons)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = signOffsetX, y = signOffsetY)
                .scale(signSize)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {

            // Sign
            Image(
                painter = painterResource(id = R.drawable.mm_sign_no_buttons),
                contentDescription = "Sign",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )

            // Buttons
            Column(
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = buttonOffsetX, verticalBias = buttonOffsetY))
                    .fillMaxWidth(buttonWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                MenuButton(R.drawable.mm_create_room_button, "Create", onCreateLobby)
                MenuButton(R.drawable.mm_join_room_button, "Join", onJoinLobby)
                MenuButton(R.drawable.mm_rules_button, "Rules", onRules)
            }
        }
    }
}

@Composable
private fun MenuButton(drawableId: Int, contentDesc: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "buttonScale")
    val alpha by animateFloatAsState(targetValue = if (isPressed) 0.7f else 1f, label = "buttonAlpha")

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = contentDesc,
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .alpha(alpha),
            contentScale = ContentScale.Fit
        )

        // HITBOX
        Box(
            modifier = Modifier
                .matchParentSize()
                // scale button hitbox
                .padding(horizontal = 0.dp, vertical = 40.dp)

                // rotate button hitbox
                .rotate(-30f)

                // DEBUG-MODE: shows hitboxes
                //.background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.4f))

                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        )
    }
}

sealed class MenuScreenState {
    data object Main : MenuScreenState()
    data object RoomCreation : MenuScreenState()
    data object RoomJoining : MenuScreenState()
    data class Lobby(val lobbyCode: String) : MenuScreenState()
    data object Rules : MenuScreenState()
}
