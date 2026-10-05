package com.jagajaga.calendarmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object MyLocationDot {
    fun create(context: Context): Drawable {
        val d = context.resources.displayMetrics.density
        val size = (22 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawCircle(r, r, r - 3 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 115, 232) })
        return BitmapDrawable(context.resources, bmp)
    }
}
