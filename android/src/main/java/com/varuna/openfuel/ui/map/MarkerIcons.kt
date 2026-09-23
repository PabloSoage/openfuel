package com.varuna.openfuel.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.varuna.openfuel.core.model.Brand

/**
 * Map and list icons, one per brand key: the logo inside a white disc when the
 * cascade found one, otherwise a disc in the brand colour with its initials.
 */
object MarkerIcons {

    fun forBrand(brand: Brand, logoPng: ByteArray?, sizePx: Int): Bitmap =
        logoPng?.let { logo(it, sizePx) } ?: badge(brand, sizePx)

    private fun logo(png: ByteArray, size: Int): Bitmap? {
        val source = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 18f
        paint.color = 0x33000000
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - paint.strokeWidth, paint)
        // Fit the logo inside the disc, keeping its aspect ratio.
        val inset = size * 0.18f
        val box = RectF(inset, inset, size - inset, size - inset)
        val scale = minOf(box.width() / source.width, box.height() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
        canvas.drawBitmap(source, Rect(0, 0, source.width, source.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun badge(brand: Brand, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = brand.color.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 16f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - paint.strokeWidth, paint)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brand.textColor.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = size * if (brand.initials.length <= 1) 0.5f else 0.36f
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2
        canvas.drawText(brand.initials, size / 2f, y, text)
        return out
    }
}
