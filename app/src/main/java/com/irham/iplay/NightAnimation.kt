package com.irham.iplay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Animasi malam: bintang berkelip, bulan bergoyang, awan "Momo" tidur bernapas + Zzz. */
@Composable
fun NightAnimation(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "night")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "breathe")
    val bob by t.animateFloat(-8f, 8f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "bob")
    val stars = remember { List(45) { Triple(Random.nextFloat(), Random.nextFloat() * 0.65f, Random.nextFloat()) } }

    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF2B1B4F), Color(0xFF41306B))))
            stars.forEach { (x, y, o) ->
                val a = 0.25f + 0.75f * abs(sin((phase + o) * PI).toFloat())
                drawCircle(Color.White.copy(alpha = a), radius = (1.5f + o * 2f).dp.toPx(), center = Offset(x * size.width, y * size.height))
            }
            // Bulan sabit
            val r = size.minDimension * 0.09f
            val mc = Offset(size.width * 0.8f, size.height * 0.18f + bob.dp.toPx())
            drawCircle(Color(0x33FFE08A), r * 1.6f, mc)
            drawCircle(Color(0xFFFFE08A), r, mc)
            drawCircle(Color(0xFF0E1230), r * 0.82f, mc + Offset(r * 0.5f, -r * 0.25f))

            // Awan tidur
            val cx = size.width / 2f
            val cy = size.height * 0.6f
            val s = size.minDimension * 0.16f * breathe
            val white = Color(0xFFF4F6FF)
            drawCircle(Color(0x22FFFFFF), s * 2.1f, Offset(cx, cy + s * 0.2f))
            drawCircle(white, s, Offset(cx, cy))
            drawCircle(white, s * 0.72f, Offset(cx - s * 0.95f, cy + s * 0.25f))
            drawCircle(white, s * 0.72f, Offset(cx + s * 0.95f, cy + s * 0.25f))
            drawRoundRect(
                white, topLeft = Offset(cx - s * 1.6f, cy + s * 0.15f),
                size = Size(s * 3.2f, s * 0.8f), cornerRadius = CornerRadius(s * 0.4f),
            )
            val ink = Color(0xFF3A3A5A)
            val eye = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
            drawArc(ink, 0f, 180f, false, Offset(cx - s * 0.55f, cy + s * 0.02f), Size(s * 0.32f, s * 0.22f), style = eye)
            drawArc(ink, 0f, 180f, false, Offset(cx + s * 0.23f, cy + s * 0.02f), Size(s * 0.32f, s * 0.22f), style = eye)
            drawCircle(Color(0x88FF8FA3), s * 0.12f, Offset(cx - s * 0.68f, cy + s * 0.4f))
            drawCircle(Color(0x88FF8FA3), s * 0.12f, Offset(cx + s * 0.68f, cy + s * 0.4f))
            drawArc(ink, 0f, 180f, false, Offset(cx - s * 0.1f, cy + s * 0.36f), Size(s * 0.2f, s * 0.12f), style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
        }
        // Zzz melayang
        val w = maxWidth
        val h = maxHeight
        listOf(0f, 0.33f, 0.66f).forEachIndexed { i, d ->
            val p = (phase + d) % 1f
            Text(
                "Z",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (18 + i * 6).sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = w * 0.6f + (p * 40f).dp, y = h * 0.5f - (p * 110f).dp)
                    .alpha(1f - p),
            )
        }
    }
}
