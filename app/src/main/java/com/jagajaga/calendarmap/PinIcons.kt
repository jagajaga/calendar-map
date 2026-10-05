package com.jagajaga.calendarmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/** Draws a speech-bubble pin with the event's time printed on it. */
object PinIcons {
    fun create(context: Context, lines: List<String>, color: Int): Drawable {
        val d = context.resources.displayMetrics.density
        val bg = color or 0xFF000000.toInt()
        val fg = if (ColorUtils.calculateLuminance(bg) > 0.55) Color.BLACK else Color.WHITE
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = fg
            textSize = 12 * d
        }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val padH = 8 * d
        val padV = 5 * d
        val lineH = text.fontSpacing
        val tail = 8 * d
        val shown = lines.take(3)
        val width = shown.withIndex().maxOf { (i, s) -> (if (i == 0) bold else text).measureText(s) } + 2 * padH
        val boxH = lineH * shown.size + 2 * padV
        val bmp = Bitmap.createBitmap(width.toInt() + 2, (boxH + tail).toInt() + 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = bg }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
        }
        val box = RectF(1f, 1f, width, boxH)
        val cx = (width + 1) / 2
        val path = Path().apply {
            addRoundRect(box, 6 * d, 6 * d, Path.Direction.CW)
            moveTo(cx - tail, boxH - 1)
            lineTo(cx, boxH + tail)
            lineTo(cx + tail, boxH - 1)
            close()
        }
        c.drawPath(path, fill)
        c.drawRoundRect(box, 6 * d, 6 * d, stroke)

        shown.forEachIndexed { i, s ->
            val baseline = padV + lineH * i - text.fontMetrics.ascent + 1
            c.drawText(s, padH + 1, baseline, if (i == 0) bold else text)
        }
        return BitmapDrawable(context.resources, bmp)
    }

    fun linesFor(place: Place): List<String> {
        val evs = place.events
        return if (evs.size == 1) {
            listOf(evs[0].title.take(28), TimeFormat.pinLabel(evs[0]))
        } else {
            listOf("${evs.size} events", TimeFormat.pinLabel(evs[0]), "…")
        }
    }
}
