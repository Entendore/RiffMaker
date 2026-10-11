package com.example.riffmaker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Six-line guitar tab. High string on top, low string on bottom. */
class TabView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var tuning: IntArray = TabMapper.STANDARD
        set(value) { field = value; invalidate() }

    var notes: List<Note> = emptyList()
        set(value) { field = value; invalidate() }

    var playheadStep = -1f
        set(value) { field = value; invalidate() }

    private val cellW = dp(26f)
    private val labelW = dp(34f)
    private val rowGap = dp(26f)
    private val topPad = dp(10f)

    private val bgPaint = Paint().apply { color = Color.parseColor("#0E1118") }
    private val stringPaint = Paint().apply { color = Color.parseColor("#8B93A8") }
    private val beatLinePaint = Paint().apply { color = Color.parseColor("#2A3248") }
    private val barLinePaint = Paint().apply { color = Color.parseColor("#4A5674") }
    private val fretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20D6A8")
        textSize = dp(14f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C7590")
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val dashPaint = Paint().apply {
        color = Color.parseColor("#20D6A8"); alpha = 150; strokeWidth = dp(2f)
    }
    private val playheadPaint = Paint().apply { color = Color.parseColor("#FF5A76"); strokeWidth = dp(2f) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (topPad * 2 + 6 * rowGap).toInt()
        setMeasuredDimension((labelW + cellW * PianoRollView.TOTAL_STEPS + dp(4f)).toInt(), h)
    }

    fun playheadX(step: Float): Float = labelW + step * cellW

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Strings (row 0 = high E = string index 5)
        for (r in 0..5) {
            val y = topPad + r * rowGap + rowGap / 2f
            stringPaint.strokeWidth = dp(1f + r * 0.25f)   // lower strings thicker
            canvas.drawLine(labelW, y, w, y, stringPaint)
            val textY = y - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(TabMapper.noteName(tuning[5 - r]), labelW / 2f, textY, labelPaint)
        }

        // Beat / bar lines
        for (step in 0..PianoRollView.TOTAL_STEPS step PianoRollView.STEPS_PER_BEAT) {
            val x = labelW + step * cellW
            canvas.drawLine(x, topPad, x, h - topPad,
                if (step % PianoRollView.STEPS_PER_BAR == 0) barLinePaint else beatLinePaint)
        }

        // Fret numbers + sustain dashes
        for (n in notes) {
            val m = TabMapper.map(n.pitch, tuning) ?: continue
            val y = topPad + (5 - m.first) * rowGap + rowGap / 2f
            val numY = y - (fretPaint.ascent() + fretPaint.descent()) / 2f
            canvas.drawText(m.second.toString(), labelW + n.startStep * cellW + cellW / 2f, numY, fretPaint)
            for (i in 1 until n.durationSteps) {
                val dx = labelW + (n.startStep + i) * cellW + cellW / 2f
                canvas.drawLine(dx - dp(4f), y, dx + dp(4f), y, dashPaint)
            }
        }

        if (playheadStep >= 0f) {
            val x = playheadX(playheadStep)
            if (x <= w) canvas.drawLine(x, 0f, x, h, playheadPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
