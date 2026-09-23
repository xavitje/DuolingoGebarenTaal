package io.github.xavitje.signcoach.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.ui.theme.Amber500
import io.github.xavitje.signcoach.ui.theme.Indigo700

/**
 * Fase D — afrondings-animatie. Bewust géén confetti-library: een simpele, eigen geanimeerde
 * vinkje-in-cirkel met een kleur- en schaal-transitie (Compose [Animatable] + [Canvas]), zoals
 * PRODUCT_PLAN.md §4 vraagt. Speelt één keer af bij het tonen (LaunchedEffect(Unit)) — geen
 * externe assets, geen extra dependency.
 */
@Composable
fun CompletionCheckmark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 120.dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier.size(size)) {
        val p = progress.value.coerceIn(0f, 1f)
        val scale = 0.55f + 0.45f * p
        val circleColor = lerp(Indigo700.copy(alpha = 0.15f), Amber500, p)

        val radius = (this.size.minDimension / 2f) * scale
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = circleColor, radius = radius, center = center)

        val w = this.size.width
        val h = this.size.height
        val checkPath = Path().apply {
            moveTo(w * 0.30f, h * 0.53f)
            lineTo(w * 0.45f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        val measure = PathMeasure()
        measure.setPath(checkPath, false)
        val length = measure.length
        // Vinkje verschijnt pas nadat de cirkel al grotendeels ingekleurd is, en tekent zichzelf
        // daarna met een eigen (iets vertraagde) voortgang — geeft een duidelijk "twee stappen"
        // gevoel zonder een tweede Animatable nodig te hebben.
        val checkProgress = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
        if (checkProgress > 0f) {
            val animatedPath = Path()
            measure.getSegment(0f, length * checkProgress, animatedPath, true)
            drawPath(
                animatedPath,
                color = androidx.compose.ui.graphics.Color.White,
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
