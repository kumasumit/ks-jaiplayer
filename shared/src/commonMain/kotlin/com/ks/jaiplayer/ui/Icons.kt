package com.ks.jaiplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The app's own icons, drawn on a 24 × 24 grid. Tinted by the component that shows them. */
object JaiIcons {
    private fun icon(name: String, filled: PathBuilder.() -> Unit = {}, stroked: PathBuilder.() -> Unit = {}) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White), pathBuilder = filled)
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = stroked,
            )
        }.build()

    val Play = icon("play", filled = {
        moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close()
    })

    val Pause = icon("pause", filled = {
        moveTo(6f, 4.5f); lineTo(10f, 4.5f); lineTo(10f, 19.5f); lineTo(6f, 19.5f); close()
        moveTo(14f, 4.5f); lineTo(18f, 4.5f); lineTo(18f, 19.5f); lineTo(14f, 19.5f); close()
    })

    private fun PathBuilder.speaker() {
        moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f); lineTo(7f, 15f); lineTo(3f, 15f); close()
    }

    val Volume = icon("volume", filled = { speaker() }, stroked = {
        moveTo(15.5f, 9f); curveTo(17f, 10.5f, 17f, 13.5f, 15.5f, 15f)
        moveTo(18f, 6.5f); curveTo(21f, 9.5f, 21f, 14.5f, 18f, 17.5f)
    })

    val Muted = icon("muted", filled = { speaker() }, stroked = {
        moveTo(15.5f, 9.5f); lineTo(20.5f, 14.5f)
        moveTo(20.5f, 9.5f); lineTo(15.5f, 14.5f)
    })

    val Back10 = icon("back", stroked = {
        moveTo(11f, 7f); lineTo(5f, 12f); lineTo(11f, 17f)
        moveTo(19f, 7f); lineTo(13f, 12f); lineTo(19f, 17f)
    })

    val Forward10 = icon("forward", stroked = {
        moveTo(13f, 7f); lineTo(19f, 12f); lineTo(13f, 17f)
        moveTo(5f, 7f); lineTo(11f, 12f); lineTo(5f, 17f)
    })

    val Previous = icon("previous", filled = {
        moveTo(18f, 5f); lineTo(9f, 12f); lineTo(18f, 19f); close()
        moveTo(6f, 5f); lineTo(8f, 5f); lineTo(8f, 19f); lineTo(6f, 19f); close()
    })

    val Next = icon("next", filled = {
        moveTo(6f, 5f); lineTo(15f, 12f); lineTo(6f, 19f); close()
        moveTo(16f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(16f, 19f); close()
    })

    val Episodes = icon("episodes", stroked = {
        moveTo(9f, 6f); lineTo(20f, 6f); moveTo(9f, 12f); lineTo(20f, 12f); moveTo(9f, 18f); lineTo(20f, 18f)
    }, filled = {
        moveTo(3.5f, 4.5f); lineTo(6.5f, 6f); lineTo(3.5f, 7.5f); close()
        moveTo(3.5f, 10.5f); lineTo(6.5f, 12f); lineTo(3.5f, 13.5f); close()
        moveTo(3.5f, 16.5f); lineTo(6.5f, 18f); lineTo(3.5f, 19.5f); close()
    })

    val Subtitles = icon("subtitles", stroked = {
        moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
        moveTo(7f, 12.5f); lineTo(11f, 12.5f); moveTo(13.5f, 12.5f); lineTo(17f, 12.5f)
        moveTo(7f, 15.5f); lineTo(14f, 15.5f)
    })

    val Audio = icon("audio", stroked = {
        moveTo(4f, 10f); lineTo(4f, 14f)
        moveTo(8f, 7f); lineTo(8f, 17f)
        moveTo(12f, 4f); lineTo(12f, 20f)
        moveTo(16f, 8f); lineTo(16f, 16f)
        moveTo(20f, 10.5f); lineTo(20f, 13.5f)
    })

    /** Audio effects: three sliders with their knobs. */
    val Effects = icon("effects", stroked = {
        moveTo(6f, 4f); lineTo(6f, 20f)
        moveTo(12f, 4f); lineTo(12f, 20f)
        moveTo(18f, 4f); lineTo(18f, 20f)
        moveTo(3.5f, 14f); lineTo(8.5f, 14f)
        moveTo(9.5f, 8f); lineTo(14.5f, 8f)
        moveTo(15.5f, 16f); lineTo(20.5f, 16f)
    })

    val FullScreen = icon("fullscreen", stroked = {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    })

    val ExitFullScreen = icon("exitfullscreen", stroked = {
        moveTo(9f, 4f); lineTo(9f, 9f); lineTo(4f, 9f)
        moveTo(15f, 4f); lineTo(15f, 9f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(15f, 15f); lineTo(15f, 20f)
        moveTo(4f, 15f); lineTo(9f, 15f); lineTo(9f, 20f)
    })

    val ArrowBack = icon("arrowback", stroked = {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    })

    val Close = icon("close", stroked = {
        moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
    })

    val Settings = icon("settings", stroked = {
        moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 17f); lineTo(20f, 17f)
    }, filled = {
        addCircle(15f, 7f, 2.5f); addCircle(8f, 12f, 2.5f); addCircle(13f, 17f, 2.5f)
    })

    val Film = icon("film", stroked = {
        moveTo(4f, 4.5f); lineTo(20f, 4.5f); lineTo(20f, 19.5f); lineTo(4f, 19.5f); close()
        moveTo(8f, 4.5f); lineTo(8f, 19.5f); moveTo(16f, 4.5f); lineTo(16f, 19.5f)
        moveTo(4f, 12f); lineTo(8f, 12f); moveTo(16f, 12f); lineTo(20f, 12f)
    })

    val Folder = icon("folder", stroked = {
        moveTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8f); lineTo(20.5f, 8f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
    })

    private fun PathBuilder.addCircle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }
}
