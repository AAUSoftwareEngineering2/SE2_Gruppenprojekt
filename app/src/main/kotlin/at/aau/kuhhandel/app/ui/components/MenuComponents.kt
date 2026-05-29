package at.aau.kuhhandel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.audio.rememberButtonClickSound

/**
 * This file also includes UI Elements which may also be used in the Game Views.
 *
 * Renders decorative items at specific offsets on the screen.
 */
@Composable
fun MenuDecor(
    drawableId: Int,
    x: Int,
    y: Int,
    size: Int,
) {
    Box(
        modifier =
            Modifier
                .offset { IntOffset(x, y) }
                .size(size.dp),
    ) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun MainBackground(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_grass_rec),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** A full-screen background with various decorative elements. */
@Composable
fun MenuBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        MainBackground()

        // Decorations (Bushes)
        MenuDecor(R.drawable.ig_tall_bush, -115, 129, 300)
        MenuDecor(R.drawable.ig_short_bush, 704, 183, 216)
        MenuDecor(R.drawable.ig_tall_bush, 772, 711, 288)
        MenuDecor(R.drawable.ig_tall_bush, -200, 1255, 264)
        MenuDecor(R.drawable.ig_short_bush, 749, 1681, 180)

        // Wrap inside box for alignment purposes
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/** A styled container card for menu content with an optional back button. */
@Composable
fun MenuCard(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                content = content,
            )
        }

        if (onBack != null) {
            BackButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .offset(x = (-20).dp, y = (-20).dp),
            )
        }
    }
}

/** A custom styled button that uses a drawable as its appearance. */
@Composable
fun MenuButton(
    drawableId: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val playClickSound = rememberButtonClickSound()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "buttonScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        label = "buttonAlpha",
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = contentDesc,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .alpha(alpha),
            contentScale = ContentScale.Fit,
        )

        // HITBOX
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    // scale button hitbox
                    .padding(horizontal = 0.dp, vertical = 40.dp)
                    // rotate button hitbox
                    .rotate(-30f)
                    // DEBUG-MODE: shows hitboxes
                    // .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            playClickSound()
                            onClick()
                        },
                    ),
        )
    }
}

@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playClickSound = rememberButtonClickSound()

    IconButton(
        onClick = {
            playClickSound()
            onClick()
        },
        modifier = modifier.size(64.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_back),
            contentDescription = "back",
            modifier = Modifier.fillMaxSize(),
        )
    }
}
