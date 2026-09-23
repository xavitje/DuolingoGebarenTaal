package io.github.xavitje.signcoach.ui.badges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.ui.theme.GoldDeep
import io.github.xavitje.signcoach.ui.theme.GoldLight
import io.github.xavitje.signcoach.ui.theme.Plum

/**
 * Fase D — badge-iconen, herontworpen om dezelfde glanzende "3D-medaille"-taal te spreken als
 * de padknopen op het Leren-scherm (zie [io.github.xavitje.signcoach.ui.main] `PathNodeBadge`): een
 * witte cirkelrand met een zachte schaduw, daarbinnen een gradient-bol met een glans linksboven,
 * en een strak getekend icoon erin. Behaalde badges krijgen een warme goud-gradient (het voelt
 * als een medaille); nog niet behaalde badges krijgen dezelfde zacht-lila "vergrendeld"-gradient
 * als een gesloten pad-node, in plaats van een vlakke grijze schijf. De icoon-glyphs zelf
 * (vlam, A-Z, schild, etc.) blijven zelfgetekende Compose [Canvas]-vormen — geen externe assets
 * nodig — maar leven nu in de medaille in plaats van in een platte cirkel.
 */
@Composable
fun BadgeIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    size: Dp = 72.dp,
) {
    val glyphColor = if (locked) Plum.copy(alpha = 0.55f) else Color.White
    val glyphSoft = if (locked) Plum.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.72f)

    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = if (locked) 1.5.dp else 5.dp,
        modifier = modifier.size(size),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(size * 0.07f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (locked)
                                listOf(Color(0xFFF4F0FA), Color(0xFFCFC5E3))
                            else
                                listOf(GoldLight, GoldDeep),
                        )
                    ),
            ) {
                // Glans linksboven — zelfde trucje als de pad-badges: suggereert een bolle,
                // glazige medaille in plaats van een platte cirkel.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = size * 0.16f, y = size * 0.12f)
                        .size(size * 0.36f, size * 0.2f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (locked) 0.4f else 0.55f)),
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val inset = this.size.width * 0.24f
                    val glyphBox = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    when (iconKey) {
                        "eerste_stap", "bijdrager" -> drawHand(topLeft, glyphBox, glyphColor, plus = iconKey == "bijdrager")
                        "streak_7" -> drawFlame(topLeft, glyphBox, glyphColor, glyphSoft, bold = false)
                        "streak_30" -> drawFlame(topLeft, glyphBox, glyphColor, glyphSoft, bold = true, wreath = true)
                        "alfabet_compleet" -> drawAz(topLeft, glyphBox, glyphColor, glyphSoft)
                        "cijfers_compleet" -> drawAscendingBars(topLeft, glyphBox, glyphColor, glyphSoft)
                        "vroege_vogel" -> drawSunrise(topLeft, glyphBox, glyphColor)
                        "perfecte_les" -> drawShieldCheck(topLeft, glyphBox, glyphColor)
                        else -> drawStar(topLeft, glyphBox, glyphColor)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHand(topLeft: Offset, box: Size, color: Color, plus: Boolean) {
    val w = box.width
    val h = box.height
    val x0 = topLeft.x
    val y0 = topLeft.y
    // Handpalm: afgeronde rechthoek onderin.
    drawRoundRect(
        color = color,
        topLeft = Offset(x0 + w * 0.18f, y0 + h * 0.42f),
        size = Size(w * 0.64f, h * 0.5f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f),
    )
    // Vier vingers, uitwaaierend.
    val fingerWidth = w * 0.13f
    val fingerHeights = listOf(0.34f, 0.44f, 0.42f, 0.32f)
    val startXs = listOf(0.20f, 0.37f, 0.54f, 0.71f)
    fingerHeights.forEachIndexed { i, hf ->
        drawRoundRect(
            color = color,
            topLeft = Offset(x0 + w * startXs[i], y0 + h * (0.46f - hf)),
            size = Size(fingerWidth, h * hf),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(fingerWidth / 2f, fingerWidth / 2f),
        )
    }
    if (plus) {
        val plusColor = Color.White
        val cx = x0 + w * 1.0f
        val cy = y0 + h * 0.08f
        val arm = w * 0.16f
        val t = w * 0.05f
        drawRoundRect(plusColor, topLeft = Offset(cx - arm / 2f, cy - t / 2f), size = Size(arm, t), cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2f, t / 2f))
        drawRoundRect(plusColor, topLeft = Offset(cx - t / 2f, cy - arm / 2f), size = Size(t, arm), cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2f, t / 2f))
    }
}

private fun DrawScope.drawFlame(topLeft: Offset, box: Size, color: Color, soft: Color, bold: Boolean, wreath: Boolean = false) {
    val w = box.width
    val h = box.height
    val x0 = topLeft.x
    val y0 = topLeft.y
    val cx = x0 + w / 2f

    // Twee overlappende afgeronde driehoeken vormen de vlam.
    val outer = Path().apply {
        moveTo(cx, y0)
        quadraticTo(x0 + w * (if (bold) 0.98f else 0.9f), y0 + h * 0.62f, cx, y0 + h)
        quadraticTo(x0 + w * (if (bold) 0.02f else 0.1f), y0 + h * 0.62f, cx, y0)
        close()
    }
    drawPath(outer, color = color)

    val inner = Path().apply {
        moveTo(cx, y0 + h * 0.32f)
        quadraticTo(x0 + w * 0.68f, y0 + h * 0.66f, cx, y0 + h * 0.92f)
        quadraticTo(x0 + w * 0.32f, y0 + h * 0.66f, cx, y0 + h * 0.32f)
        close()
    }
    drawPath(inner, color = soft)

    if (wreath) {
        val strokeW = w * 0.045f
        drawArc(
            color = color,
            startAngle = 110f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(x0 - w * 0.12f, y0 - h * 0.02f),
            size = Size(w * 1.24f, h * 1.1f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -70f,
            sweepAngle = -140f,
            useCenter = false,
            topLeft = Offset(x0 - w * 0.12f, y0 - h * 0.02f),
            size = Size(w * 1.24f, h * 1.1f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawAz(topLeft: Offset, box: Size, color: Color, soft: Color) {
    val w = box.width
    val h = box.height
    val x0 = topLeft.x
    val y0 = topLeft.y
    val strokeW = w * 0.11f

    // Geometrische "A": twee schuine lijnen + dwarsbalk.
    val a = Path().apply {
        moveTo(x0 + w * 0.06f, y0 + h * 0.98f)
        lineTo(x0 + w * 0.4f, y0)
        lineTo(x0 + w * 0.74f, y0 + h * 0.98f)
    }
    drawPath(
        a,
        color = soft,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawLine(
        color = soft,
        start = Offset(x0 + w * 0.19f, y0 + h * 0.62f),
        end = Offset(x0 + w * 0.61f, y0 + h * 0.62f),
        strokeWidth = strokeW * 0.85f,
        cap = StrokeCap.Round,
    )

    // Geometrische "Z", overlappend rechtsonder.
    val z = Path().apply {
        moveTo(x0 + w * 0.42f, y0 + h * 0.22f)
        lineTo(x0 + w * 0.98f, y0 + h * 0.22f)
        lineTo(x0 + w * 0.42f, y0 + h * 1.0f)
        lineTo(x0 + w * 0.98f, y0 + h * 1.0f)
    }
    drawPath(
        z,
        color = color,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawAscendingBars(topLeft: Offset, box: Size, color: Color, soft: Color) {
    val w = box.width
    val h = box.height
    val x0 = topLeft.x
    val y0 = topLeft.y
    val barWidth = w * 0.2f
    val gap = w * 0.14f
    val heights = listOf(0.4f, 0.68f, 1.0f)
    val colors = listOf(soft, color, soft)
    heights.forEachIndexed { i, hf ->
        val barHeight = h * hf
        drawRoundRect(
            color = colors[i],
            topLeft = Offset(x0 + i * (barWidth + gap), y0 + (h - barHeight)),
            size = Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f, barWidth * 0.3f),
        )
    }
}

private fun DrawScope.drawSunrise(topLeft: Offset, box: Size, color: Color) {
    val w = box.width
    val h = box.height
    val x0 = topLeft.x
    val y0 = topLeft.y
    val horizonY = y0 + h * 0.62f

    // Halve cirkel (opkomende zon).
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(x0 + w * 0.14f, y0 + h * 0.12f),
        size = Size(w * 0.72f, w * 0.72f),
    )
    // Horizonlijn.
    drawLine(
        color = color,
        start = Offset(x0, horizonY),
        end = Offset(x0 + w, horizonY),
        strokeWidth = w * 0.07f,
        cap = StrokeCap.Round,
    )
    // Drie stralen.
    val rayWidth = w * 0.06f
    listOf(0.2f, 0.5f, 0.8f).forEach { fx ->
        drawLine(
            color = color,
            start = Offset(x0 + w * fx, y0 + h * 0.86f),
            end = Offset(x0 + w * fx, y0 + h * 1.02f),
            strokeWidth = rayWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawShieldCheck(topLeft: Offset, box: Size, color: Color) {
    val w = box.width
    val h = box.height
    val x0 = topLeft.x
    val y0 = topLeft.y
    val strokeW = w * 0.1f

    val shield = Path().apply {
        moveTo(x0 + w * 0.5f, y0)
        quadraticTo(x0 + w * 1.0f, y0 + h * 0.06f, x0 + w * 0.94f, y0 + h * 0.14f)
        lineTo(x0 + w * 0.94f, y0 + h * 0.52f)
        quadraticTo(x0 + w * 0.94f, y0 + h * 0.86f, x0 + w * 0.5f, y0 + h * 1.02f)
        quadraticTo(x0 + w * 0.06f, y0 + h * 0.86f, x0 + w * 0.06f, y0 + h * 0.52f)
        lineTo(x0 + w * 0.06f, y0 + h * 0.14f)
        quadraticTo(x0, y0 + h * 0.06f, x0 + w * 0.5f, y0)
        close()
    }
    drawPath(shield, color = color, style = Stroke(width = strokeW * 0.7f, join = StrokeJoin.Round))

    val check = Path().apply {
        moveTo(x0 + w * 0.28f, y0 + h * 0.52f)
        lineTo(x0 + w * 0.44f, y0 + h * 0.68f)
        lineTo(x0 + w * 0.74f, y0 + h * 0.34f)
    }
    drawPath(check, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawStar(topLeft: Offset, box: Size, color: Color) {
    val w = box.width
    val h = box.height
    val cx = topLeft.x + w / 2f
    val cy = topLeft.y + h / 2f
    val outerR = minOf(w, h) / 2f
    val innerR = outerR * 0.42f
    val path = Path()
    for (i in 0 until 10) {
        val angle = (Math.PI / 5.0 * i - Math.PI / 2.0)
        val r = if (i % 2 == 0) outerR else innerR
        val x = cx + (r * kotlin.math.cos(angle)).toFloat()
        val y = cy + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}
