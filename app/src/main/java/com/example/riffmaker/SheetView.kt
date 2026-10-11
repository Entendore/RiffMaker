package com.example.riffmaker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

/**
 * Staff notation. Simplified engraving: sharps-only accidentals, flags instead of
 * beams, odd durations snap to the nearest standard value (with dots).
 * Clef (treble/bass) is chosen automatically from the average pitch.
 */
class SheetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val DEG = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
        const val TREBLE_BOTTOM_D = 37   // E4 (bottom staff line)
        const val BASS_BOTTOM_D = 25     // G2 (bottom staff line)
        fun diatonic(p: Int) = (p / 12) * 7 + DEG[p % 12]
    }

    var notes: List<Note> = emptyList()
        set(value) { field = value; invalidate() }

    var playheadStep = -1f
        set(value) { field = value; invalidate() }

    private val gap = dp(8f)
    private val stepW = dp(13f)
    private val clefW = dp(42f)
    private val pad = dp(10f)
    private val systemH = dp(72f)

    private val staffPaint = Paint().apply { color = Color.BLACK; strokeWidth = dp(1f) }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = gap * 0.22f
    }
    private val stemPaint = Paint().apply { color = Color.BLACK; strokeWidth = dp(1.4f) }
    private val accPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = gap * 0.14f; strokeCap = Paint.Cap.ROUND
    }
    private val clefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val clefStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = dp(1.6f); strokeCap = Paint.Cap.ROUND
    }
    private val playheadPaint = Paint().apply { color = Color.parseColor("#FF5A76"); strokeWidth = dp(2f) }

    private val TREBLE_GLYPH = "\uD834\uDD1E"   // treble clef
    private val BASS_GLYPH = "\uD834\uDD22"     // bass clef

    private data class Layout(val barsPer: Int, val systems: Int, val stepsPerSystem: Int)

    private fun layoutFor(w: Int): Layout {
        val barW = PianoRollView.STEPS_PER_BAR * stepW
        val usable = w - pad * 2 - clefW - dp(4f)
        val barsPer = max(1, (usable / barW).toInt())
        val stepsPerSystem = barsPer * PianoRollView.STEPS_PER_BAR
        val systems = ceil(PianoRollView.TOTAL_STEPS / stepsPerSystem.toDouble()).toInt()
        return Layout(barsPer, systems, stepsPerSystem)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = getDefaultSize(dp(200f).toInt(), widthMeasureSpec)
        val l = layoutFor(w)
        setMeasuredDimension(w, (pad * 2 + l.systems * systemH).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layoutFor(width)
        val clefTreble = notes.isEmpty() || notes.map { it.pitch }.average() >= 60.0
        val bottomD = if (clefTreble) TREBLE_BOTTOM_D else BASS_BOTTOM_D
        val topD = bottomD + 8

        for (sys in 0 until l.systems) {
            val top = pad + sys * systemH
            val staffBottom = top + 4 * gap
            val staffLeft = pad + clefW
            val staffRight = staffLeft + l.barsPer * PianoRollView.STEPS_PER_BAR * stepW

            for (i in 0..4) canvas.drawLine(pad, top + i * gap, staffRight, top + i * gap, staffPaint)

            if (clefTreble) drawTrebleClef(canvas, pad + clefW * 0.5f, top)
            else drawBassClef(canvas, pad + clefW * 0.5f, top)

            for (b in 0..l.barsPer) {
                val x = staffLeft + b * PianoRollView.STEPS_PER_BAR * stepW
                canvas.drawLine(x, top, x, staffBottom, staffPaint)
            }

            val sysStart = sys * l.stepsPerSystem
            for (n in notes) {
                if (n.startStep < sysStart || n.startStep >= sysStart + l.stepsPerSystem) continue
                drawNote(canvas, n, staffLeft + (n.startStep - sysStart) * stepW, top, bottomD, topD)
            }

            if (playheadStep >= 0f && playheadStep.toInt() in sysStart until sysStart + l.stepsPerSystem) {
                val x = staffLeft + (playheadStep - sysStart) * stepW
                canvas.drawLine(x, top - dp(4f), x, staffBottom + dp(4f), playheadPaint)
            }
        }
    }

    // ---------- Note drawing ----------

    private class Dur(val steps: Int, val dotted: Boolean, val hollow: Boolean, val flags: Int)

    private fun describe(steps: Int) = when {
        steps >= 16 -> Dur(16, false, true, 0)   // whole
        steps >= 13 -> Dur(12, true, true, 0)    // dotted half
        steps >= 8  -> Dur(8, false, true, 0)    // half
        steps >= 6  -> Dur(6, true, false, 0)    // dotted quarter
        steps >= 4  -> Dur(4, false, false, 0)   // quarter
        steps >= 3  -> Dur(3, true, false, 1)    // dotted eighth
        steps >= 2  -> Dur(2, false, false, 1)   // eighth
        else        -> Dur(1, false, false, 2)   // sixteenth
    }

    private fun yForD(d: Int, top: Float, bottomD: Int) =
        top + 4 * gap - (d - bottomD) * (gap / 2f)

    private fun drawNote(c: Canvas, n: Note, cellX: Float, top: Float, bottomD: Int, topD: Int) {
        val d = diatonic(n.pitch)
        val y = yForD(d, top, bottomD)
        val x = cellX + stepW / 2f

        // Ledger lines below / above the staff
        var dd = bottomD - 2
        while (dd >= d) { c.drawLine(x - gap * 0.9f, yForD(dd, top, bottomD), x + gap * 0.9f, yForD(dd, top, bottomD), staffPaint); dd -= 2 }
        dd = topD + 2
        while (dd <= d) { c.drawLine(x - gap * 0.9f, yForD(dd, top, bottomD), x + gap * 0.9f, yForD(dd, top, bottomD), staffPaint); dd += 2 }

        val dur = describe(n.durationSteps)

        if (n.pitch % 12 in intArrayOf(1, 3, 6, 8, 10)) drawSharp(c, x - gap * 1.35f, y)

        val rx = gap * 0.72f
        val ry = gap * 0.5f
        c.save(); c.translate(x, y); c.rotate(-20f)
        c.drawOval(-rx, -ry, rx, ry, if (dur.hollow) ringPaint else notePaint)
        c.restore()

        if (dur.steps == 16) {           // whole note: no stem
            if (dur.dotted) drawDot(c, x, y, d, bottomD)
            return
        }

        val stemUp = d < bottomD + 4
        val sx = if (stemUp) x + rx * 0.92f else x - rx * 0.92f
        val sy2 = if (stemUp) y - gap * 3.4f else y + gap * 3.4f
        c.drawLine(sx, y, sx, sy2, stemPaint)
        for (f in 0 until dur.flags) drawFlag(c, sx, sy2, stemUp, f)
        if (dur.dotted) drawDot(c, x, y, d, bottomD)
    }

    private fun drawDot(c: Canvas, x: Float, y: Float, d: Int, bottomD: Int) {
        val dy = if ((d - bottomD) % 2 == 0) y - gap / 2f else y   // shift off staff lines
        c.drawCircle(x + gap * 1.2f, dy, gap * 0.16f, notePaint)
    }

    private fun drawSharp(c: Canvas, cx: Float, cy: Float) {
        val g = gap
        c.drawLine(cx - g * 0.18f, cy - g * 0.62f, cx - g * 0.18f, cy + g * 0.62f, accPaint)
        c.drawLine(cx + g * 0.18f, cy - g * 0.62f, cx + g * 0.18f, cy + g * 0.62f, accPaint)
        c.drawLine(cx - g * 0.34f, cy - g * 0.18f, cx + g * 0.34f, cy - g * 0.28f, accPaint)
        c.drawLine(cx - g * 0.34f, cy + g * 0.22f, cx + g * 0.34f, cy + g * 0.12f, accPaint)
    }

    private fun drawFlag(c: Canvas, sx: Float, sy: Float, stemUp: Boolean, index: Int) {
        val dir = if (stemUp) 1f else -1f
        val yy = sy + dir * index * gap * 0.55f
        val p = Path()
        p.moveTo(sx, yy)
        p.cubicTo(sx + gap, yy + dir * gap * 0.4f, sx + gap * 0.8f, yy + dir * gap * 1.0f, sx + gap * 0.1f, yy + dir * gap * 1.5f)
        p.cubicTo(sx + gap * 0.75f, yy + dir * gap * 0.95f, sx + gap * 0.85f, yy + dir * gap * 0.5f, sx, yy)
        c.drawPath(p, notePaint)
    }

    // ---------- Clefs (real glyph if the font has it, stylized path otherwise) ----------

    private fun drawTrebleClef(c: Canvas, cx: Float, top: Float) {
        if (clefPaint.hasGlyph(TREBLE_GLYPH)) {
            clefPaint.textSize = gap * 5.0f
            val y = top + 2 * gap - (clefPaint.ascent() + clefPaint.descent()) / 2f
            c.drawText(TREBLE_GLYPH, cx - clefPaint.measureText(TREBLE_GLYPH) / 2f, y, clefPaint)
            return
        }
        val g = gap
        val y1 = top; val y2 = top + g; val y3 = top + 2 * g; val y4 = top + 3 * g; val y5 = top + 4 * g
        val p = Path()
        p.moveTo(cx + 0.45f * g, y5)
        p.cubicTo(cx + 1.05f * g, y3 + 0.2f * g, cx + 0.95f * g, y1 - 0.9f * g, cx - 0.1f * g, y1 - 0.9f * g)
        p.cubicTo(cx - 1.15f * g, y1 - 0.9f * g, cx - 1.5f * g, y2 + 0.8f * g, cx - 0.25f * g, y3 + 0.15f * g)
        p.cubicTo(cx + 0.85f * g, y3 + 1.05f * g, cx + 0.75f * g, y4 + 0.35f * g, cx + 0.1f * g, y4 + 0.5f * g)
        p.cubicTo(cx - 0.6f * g, y4 + 0.65f * g, cx - 0.6f * g, y5 + 0.35f * g, cx + 0.15f * g, y5 + 0.35f * g)
        c.drawPath(p, clefStrokePaint)
        c.drawCircle(cx + 0.45f * g, y5 + 0.75f * g, g * 0.22f, notePaint)
    }

    private fun drawBassClef(c: Canvas, cx: Float, top: Float) {
        if (clefPaint.hasGlyph(BASS_GLYPH)) {
            clefPaint.textSize = gap * 3.4f
            val y = top + 1.6f * gap - (clefPaint.ascent() + clefPaint.descent()) / 2f
            c.drawText(BASS_GLYPH, cx - clefPaint.measureText(BASS_GLYPH) / 2f, y, clefPaint)
            return
        }
        val g = gap
        val p = Path()
        p.moveTo(cx + 0.55f * g, top + 0.2f * g)
        p.cubicTo(cx - 0.85f * g, top + 0.6f * g, cx - 0.85f * g, top + 3.4f * g, cx + 0.25f * g, top + 3.5f * g)
        c.drawPath(p, clefStrokePaint)
        c.drawCircle(cx + 0.55f * g, top + 0.2f * g, g * 0.26f, notePaint)
        c.drawCircle(cx + 1.0f * g, top + 0.7f * g, g * 0.12f, notePaint)
        c.drawCircle(cx + 1.0f * g, top + 1.3f * g, g * 0.12f, notePaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
