package at.aau.kuhhandel.app.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun MenuMusicPlayer(
    isGameStarted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val mediaPlayer =
        remember {
            createMenuMediaPlayer(context)
        }

    LaunchedEffect(isGameStarted) {
        mediaPlayer.managePlayback(isGameStarted)
    }

    DisposableEffect(Unit) {
        onDispose {
            releaseMediaPlayer(mediaPlayer)
        }
    }

    content()
}
