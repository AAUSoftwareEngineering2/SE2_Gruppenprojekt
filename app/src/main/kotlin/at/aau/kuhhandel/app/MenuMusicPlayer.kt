package at.aau.kuhhandel.app

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun MenuMusicPlayer(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val mediaPlayer =
        remember {
            MediaPlayer.create(context, R.raw.mainmenu)
        }

    LaunchedEffect(Unit) {
        mediaPlayer.isLooping = true
        mediaPlayer.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    content()
}
