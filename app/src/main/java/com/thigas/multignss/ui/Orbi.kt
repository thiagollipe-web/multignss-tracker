package com.thigas.multignss.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.thigas.multignss.R
import kotlinx.coroutines.delay

enum class OrbiMood { HAPPY, SLEEP, ALERT }

private const val BLINK_EVERY_MS = 3200L
private const val BLINK_MS = 160L

/** Mascote Orbi; feliz pisca sozinho. */
@Composable
fun Orbi(mood: OrbiMood, size: Dp, description: String = "Orbi, o mascote") {
    var closed by remember { mutableStateOf(false) }
    if (mood == OrbiMood.HAPPY) {
        LaunchedEffect(Unit) {
            while (true) { delay(BLINK_EVERY_MS); closed = true; delay(BLINK_MS); closed = false }
        }
    }
    val res = when (mood) {
        OrbiMood.SLEEP -> R.drawable.orbi_sleep
        OrbiMood.ALERT -> R.drawable.orbi_alert
        OrbiMood.HAPPY -> if (closed) R.drawable.orbi_blink else R.drawable.orbi
    }
    Image(painterResource(res), contentDescription = description, modifier = Modifier.size(size))
}
