package com.babelmanager.app.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin

private val Bg = Color(0xFF2F2A6B)
private val RingColors = listOf(Color(0xFFC8C1FF), Color(0xFF8F83F0), Color(0xFF5B4BD6), Color(0xFF1B1545))
private val Gold = Color(0xFFFFC857)
private val OuterStar = Color(0xFF5B4BD6)
private val MidStar = Color(0xFFE3DFFF)
private val GlyphColor = Color(0xFFC8C1FF)

private const val END_MS = 2300f

// Instantes (ms) dos impactos e a força do tremor de cada um.
private val Impacts = listOf(490f to 2.2f, 680f to 2.6f, 870f to 3.0f, 1060f to 3.4f, 1330f to 3.6f)

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

private fun local(ms: Float, start: Float, duration: Float) = ((ms - start) / duration).coerceIn(0f, 1f)

private fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

/** Curva que passa do alvo e volta (impacto). */
private fun backOut(p: Float, c1: Float): Float {
    val q = p - 1f
    return 1f + (c1 + 1f) * q * q * q + c1 * q * q
}

/**
 * Ícone do Babel Manager desenhado em código, que se monta por impactos: o fundo surge, os anéis hexagonais batem
 * um a um, a fechadura encaixa e por fim aparecem os asteriscos e as letras de alfabetos diferentes.
 * Com as animações do sistema desligadas, aparece já pronto. Mude [replayKey] para tocar de novo.
 */
@Composable
fun BabelMark(modifier: Modifier = Modifier, replayKey: Int = 0) {
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val time = remember { Animatable(if (reduced) END_MS else 0f) }
    LaunchedEffect(replayKey) {
        if (!reduced) {
            time.snapTo(0f)
            time.animateTo(END_MS, tween(END_MS.toInt(), easing = LinearEasing))
        }
    }
    val ringPaths = remember { BabelMarkData.rings.map(::parse) }
    val keyPath = remember { parse(BabelMarkData.keyhole) }
    val stars = remember { (BabelMarkData.outerStars.map { it to OuterStar } + BabelMarkData.midStars.map { it to MidStar }).map { (s, c) -> Triple(parse(s.path), s, c) } }
    val glyphs = remember { BabelMarkData.glyphs.map { parse(it.path) to it } }

    Canvas(modifier.aspectRatio(1f)) {
        val s = size.width / 108f
        val ms = time.value
        var dx = 0f
        var dy = 0f
        for ((at, amp) in Impacts) {
            val u = (ms - at) / 200f
            if (u in 0f..1f) {
                val k = (1f - u) * amp
                dx += -k * sin(u * 2f * PI.toFloat() * 1.5f)
                dy += k * 0.7f * sin(u * 2f * PI.toFloat() * 1.5f + 1f)
            }
        }
        withTransform({ scale(s, s, Offset.Zero); translate(dx, dy) }) {
            val pb = local(ms, 0f, 320f)
            val bgScale = 0.5f + 0.5f * backOut(pb, 1.7f)
            withTransform({ scale(bgScale, bgScale, Offset(54f, 54f)) }) {
                drawRoundRect(Bg, size = Size(108f, 108f), cornerRadius = CornerRadius(26f), alpha = (pb * 3f).coerceAtMost(1f))
            }
            ringPaths.forEachIndexed { i, path ->
                val p = local(ms, 260f + 190f * i, 340f)
                if (p > 0f) {
                    val sc = 1.7f - 0.7f * backOut(p, 1.4f)
                    withTransform({ scale(sc, sc, Offset(54f, 54f)) }) { drawPath(path, RingColors[i], alpha = (p * 4f).coerceAtMost(1f)) }
                }
            }
            val pk = local(ms, 1060f, 460f)
            if (pk > 0f) {
                val sc = backOut(pk, 2.6f)
                withTransform({ scale(sc, sc, Offset(54f, 56f)); rotate(-40f * (1f - easeOut(pk)), Offset(54f, 56f)) }) {
                    drawPath(keyPath, Gold, alpha = (pk * 4f).coerceAtMost(1f))
                }
            }
            stars.forEachIndexed { i, (path, star, color) ->
                val p = local(ms, 1420f + 45f * i, 300f)
                if (p > 0f) {
                    val sc = backOut(p, 2.6f)
                    val pivot = Offset(star.x, star.y)
                    withTransform({ scale(sc, sc, pivot); rotate(-90f * (1f - easeOut(p)), pivot) }) {
                        drawPath(path, color, alpha = p, style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }
                }
            }
            glyphs.forEachIndexed { j, (path, glyph) ->
                val p = local(ms, 1900f + 70f * j, 260f)
                if (p > 0f) {
                    val k = 1f - easeOut(p)
                    val ox = (if (glyph.x < 54f) -10f else 10f) * k
                    val oy = (if (glyph.y < 54f) -10f else 10f) * k
                    withTransform({ translate(ox, oy) }) { drawPath(path, GlyphColor, alpha = easeOut(p)) }
                }
            }
        }
    }
}
