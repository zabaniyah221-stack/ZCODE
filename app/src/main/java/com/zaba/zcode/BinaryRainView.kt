package com.zaba.zcode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.ceil

/** Lightweight process-transition view. Every vertical trail repeats ASCII binary ZCODE. */
class BinaryRainView(
    context: Context,
    private val status: String = "Memulai ulang Python…",
) : View(context) {
    private val sequence = "0101101001000011010011110100010001000101"
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 205, 194)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private var startedAt = SystemClock.uptimeMillis()
    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 42L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startedAt = SystemClock.uptimeMillis()
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(5, 8, 6))
        val cell = (resources.displayMetrics.scaledDensity * 15f).coerceAtLeast(14f)
        rainPaint.textSize = cell
        val columns = ceil(width / (cell * 1.45f)).toInt().coerceAtLeast(1)
        val rows = ceil(height / cell).toInt() + 2
        val tick = ((SystemClock.uptimeMillis() - startedAt) / 84L).toInt()
        for (column in 0 until columns) {
            val x = (column + 0.5f) * width / columns
            val speed = 1 + (column * 7 % 3)
            val head = (tick * speed + column * 11) % (rows + 14)
            val trail = 9 + (column * 5 % 13)
            val offset = column * 17 % sequence.length
            for (distance in 0 until trail) {
                val row = head - distance
                if (row !in 0 until rows) continue
                val alpha = (220 - distance * 14).coerceAtLeast(18)
                rainPaint.color = if (distance == 0) Color.argb(245, 205, 255, 218)
                    else Color.argb(alpha, 38, 220, 91)
                val bit = sequence[(offset + row) % sequence.length].toString()
                canvas.drawText(bit, x, (row + 1) * cell, rainPaint)
            }
        }

        titlePaint.textSize = resources.displayMetrics.scaledDensity * 30f
        statusPaint.textSize = resources.displayMetrics.scaledDensity * 15f
        val center = height * 0.48f
        canvas.drawText("ZCODE", width / 2f, center, titlePaint)
        canvas.drawText(status, width / 2f, center + cell * 2.1f, statusPaint)
    }
}
