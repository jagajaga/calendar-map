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
    private val cache = android.util.LruCache<Triple<List<String>, Int, Boolean>, Drawable>(400)

    fun get(context: Context, lines: List<String>, color: Int, highlighted: Boolean): Drawable {
        val key = Triple(lines, color, highlighted)
        return cache.get(key) ?: create(context, lines, color, highlighted).also { cache.put(key, it) }
    }

    private fun create(context: Context, lines: List<String>, color: Int, highlighted: Boolean): Drawable {
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
            this.color = if (highlighted) Color.BLACK else Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (if (highlighted) 3.5f else 1.5f) * d
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

    /**
     * @param selected keys of events picked for a route ("✓" mark).
     * @param order route position by event key ("2 ·" prefix).
     */
    fun linesFor(place: Place, selected: Set<String>, order: Map<String, Int>): List<String> {
        val evs = place.events
        val stops = evs.mapNotNull { order[it.key] }.sorted()
        val prefix = when {
            stops.isNotEmpty() -> stops.joinToString(",") + " · "
            evs.any { it.key in selected } -> "✓ "
            else -> ""
        }
        return if (evs.size == 1) {
            listOf(prefix + evs[0].title.take(28), TimeFormat.pinLabel(evs[0]))
        } else {
            val picked = evs.count { it.key in selected }
            val head = if (picked > 0 && stops.isEmpty()) "$picked of ${evs.size} events" else "${evs.size} events"
            listOf(prefix + head, TimeFormat.pinLabel(evs[0]), "…")
        }
    }
}
