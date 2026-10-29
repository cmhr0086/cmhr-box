package com.cmhr.box.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.cmhr.box.R

/**
 * Tints a white-on-transparent icon whose grey levels mean tones: grey becomes partial alpha of
 * [color], so overlapping shapes keep their own tone instead of stacking their alpha.
 */
fun shadeFilter(color: Int): ColorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, Color.red(color).toFloat(),
    0f, 0f, 0f, 0f, Color.green(color).toFloat(),
    0f, 0f, 0f, 0f, Color.blue(color).toFloat(),
    1f, 0f, 0f, 1f, -255f,
)))

/** The paper plane ([R.drawable.cmhr_ic_plane]) centred in the bounds; [fill] is the fraction of the bounds it spans. */
class PlaneDrawable(context: Context, color: Int, private val fill: Float = 1f) : Drawable() {
    private val plane = context.getDrawable(R.drawable.cmhr_ic_plane)!!.mutate()
    private var tint = 0

    var color: Int = color
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        val size = (minOf(bounds.width(), bounds.height()) * fill).toInt()
        val left = bounds.centerX() - size / 2
        val top = bounds.centerY() - size / 2
        plane.setBounds(left, top, left + size, top + size)
    }

    override fun draw(canvas: Canvas) {
        if (tint != color || plane.colorFilter == null) {
            tint = color
            plane.colorFilter = shadeFilter(color)
        }
        plane.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        plane.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
