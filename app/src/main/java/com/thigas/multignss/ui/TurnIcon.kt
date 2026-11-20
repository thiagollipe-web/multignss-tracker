package com.thigas.multignss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thigas.multignss.Turn
import kotlin.math.cos
import kotlin.math.sin

/** Seta de manobra desenhada em vetor (grade 64x64), estilo app de trânsito. */
@Composable
fun TurnIcon(turn: Turn, color: Color, size: Dp = 64.dp, description: String = "") {
    Canvas(Modifier.size(size).semantics { contentDescription = description }) {
        val u = this.size.width / 64f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val stroke = Stroke(width = 7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (turn) {
            Turn.ARRIVE -> {
                drawLine(color, p(32f, 30f), p(32f, 58f), 7f * u, StrokeCap.Round)
                drawCircle(color, 14f * u, p(32f, 22f))
                drawCircle(Color(0x66000000), 5f * u, p(32f, 22f))
            }
            Turn.ROUNDABOUT -> {
                drawCircle(color, 12f * u, p(32f, 30f), style = stroke)
                drawLine(color, p(32f, 58f), p(32f, 42f), 7f * u, StrokeCap.Round)
                drawLine(color, p(40.5f, 21.5f), p(48f, 14f), 7f * u, StrokeCap.Round)
                head(color, p(48f, 14f), 45f, u)
            }
            Turn.UTURN -> {
                val path = Path().apply {
                    moveTo(42f * u, 58f * u)
                    lineTo(42f * u, 28f * u)
                    arcTo(Rect(22f * u, 18f * u, 42f * u, 38f * u), 0f, -180f, false)
                    lineTo(22f * u, 40f * u)
                }
                drawPath(path, color, style = stroke)
                head(color, p(22f, 40f), 180f, u)
            }
            else -> {
                val a = Math.toRadians(turn.angle.toDouble())
                val joint = p(32f, 34f)
                val len = 18f * u
                val end = Offset(joint.x + (len * sin(a)).toFloat(), joint.y - (len * cos(a)).toFloat())
                drawLine(color, p(32f, 58f), joint, 7f * u, StrokeCap.Round)
                drawLine(color, joint, end, 7f * u, StrokeCap.Round)
                head(color, end, turn.angle, u)
            }
        }
    }
}

/** Ponta de seta na direção [angleDeg] (0 = cima, horário). */
private fun DrawScope.head(color: Color, at: Offset, angleDeg: Float, u: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
    val tip = Offset(at.x + dx * 9f * u, at.y + dy * 9f * u)
    val px = -dy; val py = dx
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(at.x + px * 11f * u - dx * 4f * u, at.y + py * 11f * u - dy * 4f * u)
        lineTo(at.x - px * 11f * u - dx * 4f * u, at.y - py * 11f * u - dy * 4f * u)
        close()
    }
    drawPath(path, color)
}
