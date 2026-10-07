package com.holaoluwakintan.ranti.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val x: Float, val delay: Float, val speed: Float, val wobble: Float, val rot: Float, val w: Float, val h: Float, val color: Color)

/** v1.0: a one-shot confetti burst for "it's their day". Cheap: one Canvas, ~60 rectangles, 2.6 s. */
@Composable
fun Confetti(modifier: Modifier = Modifier, seed: Int = 7) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val colors = listOf(C.Coral, C.Gold, Color(0xFFFF8FB1), Color(0xFF8B6CFF), C.Mint, Color.White)
    val pieces = remember {
        val r = Random(seed)
        List(60) {
            Piece(
                x = r.nextFloat(), delay = r.nextFloat() * 0.35f, speed = 0.8f + r.nextFloat() * 0.5f, wobble = 4f + r.nextFloat() * 8f,
                rot = r.nextFloat() * 360f, w = 8f + r.nextFloat() * 10f, h = 5f + r.nextFloat() * 6f, color = colors[r.nextInt(colors.size)],
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        pieces.forEach { c ->
            if (p < c.delay) return@forEach
            val t = ((p - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
            val x = c.x * size.width + sin(t * c.wobble) * 28f
            val y = -30f + t * (size.height + 60f) * c.speed
            rotate(c.rot + t * 540f, pivot = Offset(x, y)) {
                drawRect(c.color, topLeft = Offset(x, y), size = Size(c.w, c.h), alpha = (1f - t * 0.6f).coerceIn(0f, 1f))
            }
        }
    }
}
