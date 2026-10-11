package com.example.riffmaker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class PianoRollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val TOTAL_STEPS = 64      // 4 bars of 16th notes
        const val STEPS_PER_BEAT = 4
        const val STEPS_PER_BAR = 16
        const val LOWEST_PITCH = 48     // C3
        const val PITCH_COUNT = 25      // C3 .. C5
    }

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    var onNotesChanged: (() -> Unit)? = null
    var onEditCommitted: (() -> Unit)? = null   // fired once per gesture -> history
    var eraseMode = false

    var playheadStep = -1f
        set(value) { field = value; invalidate() }

    private val notes = mutableListOf<Note>()
    val snapshot: List<Note> get() = notes.toList()

    private val labelWidth = dp(38f)
    private val cellW = dp(26f)                 // fixed width -> view scrolls
    private var rowH = 24f

    private enum class DragMode { NONE, MOVE, RESIZE }
    private var dragMode = DragMode.NONE
    private var activeNote: Note? = null
    private var grabOffsetSteps = 0
    private var grabOffsetPitch = 0
    private var gestureDirty = false

    private val bgPaint = Paint().apply { color = Color.parseColor("#0E1118") }
    private val whiteRowPaint = Paint().apply { color = Color.parseColor("#1A2032") }
    private val blackRowPaint = Paint().apply { color = Color.parseColor("#141927") }
    private val stepLinePaint = Paint().apply { color = Color.parseColor("#232A3D") }
    private val beatLinePaint = Paint().apply { color = Color.parseColor("#333D57") }
    private val barLinePaint = Paint().apply { color = Color.parseColor("#55617F") }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#20D6A8") }
    private val activeNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8CF5DB") }
    private val playheadPaint = Paint().apply { color = Color.parseColor("#FF5A76"); strokeWidth = dp(2f) }
    private val whiteKeyPaint = Paint().apply { color = Color.parseColor("#E9ECF4") }
    private val blackKeyPaint = Paint().apply { color = Color.parseColor("#2A2F3A") }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#11141B"); textSize = dp(10f); textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = getDefaultSize(dp(300f).toInt(), heightMeasureSpec)
        setMeasuredDimension((labelWidth + cellW * TOTAL_STEPS + dp(4f)).toInt(), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rowH = h.toFloat() / PITCH_COUNT
    }

    fun playheadX(step: Float): Float = labelWidth + step * cellW

    fun setNotes(list: List<Note>) { notes.clear(); notes.addAll(list); invalidate() }

    fun clearAll() {
        notes.clear()
        onNotesChanged?.invoke(); onEditCommitted?.invoke(); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        for (row in 0 until PITCH_COUNT) {
            val top = row * rowH
            val pitch = pitchForRow(row)
            val black = isBlack(pitch)
            canvas.drawRect(labelWidth, top, w, top + rowH, if (black) blackRowPaint else whiteRowPaint)
            canvas.drawRect(0f, top, labelWidth, top + rowH, if (black) blackKeyPaint else whiteKeyPaint)
            if (pitch % 12 == 0) {
                val textY = top + rowH / 2f - (keyTextPaint.ascent() + keyTextPaint.descent()) / 2f
                canvas.drawText("${NAMES[pitch % 12]}${pitch / 12 - 1}", labelWidth / 2f, textY, keyTextPaint)
            }
        }

        for (step in 0..TOTAL_STEPS) {
            val x = labelWidth + step * cellW
            val paint = when {
                step % STEPS_PER_BAR == 0 -> barLinePaint
                step % STEPS_PER_BEAT == 0 -> beatLinePaint
                else -> stepLinePaint
            }
            canvas.drawLine(x, 0f, x, h, paint)
        }

        val radius = dp(3f)
        for (n in notes) {
            val left = labelWidth + n.startStep * cellW
            val top = rowForPitch(n.pitch) * rowH
            canvas.drawRoundRect(
                left + 1f, top + 1f, left + n.durationSteps * cellW - 1f, top + rowH - 1f,
                radius, radius, if (n === activeNote) activeNotePaint else notePaint
            )
        }

        if (playheadStep >= 0f) {
            val x = playheadX(playheadStep)
            if (x <= w) canvas.drawLine(x, 0f, x, h, playheadPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val col = ((event.x - labelWidth) / cellW).toInt()
                val row = (event.y / rowH).toInt()
                if (col < 0 || col >= TOTAL_STEPS || row < 0 || row >= PITCH_COUNT) return false
                val pitch = pitchForRow(row)
                val hit = noteAt(col, pitch)

                if (eraseMode) {
                    if (hit != null) { notes.remove(hit); gestureDirty = true; changed() }
                    return true
                }

                if (hit != null) {
                    val endX = labelWidth + (hit.startStep + hit.durationSteps) * cellW
                    activeNote = hit
                    if (event.x >= endX - cellW * 0.45f) dragMode = DragMode.RESIZE
                    else {
                        dragMode = DragMode.MOVE
                        grabOffsetSteps = col - hit.startStep
                        grabOffsetPitch = pitch - hit.pitch
                    }
                } else {
                    val note = Note(col, 1, pitch)
                    notes.add(note)
                    activeNote = note
                    dragMode = DragMode.RESIZE
                    gestureDirty = true
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    changed()
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val note = activeNote ?: return true
                val col = ((event.x - labelWidth) / cellW).toInt().coerceIn(0, TOTAL_STEPS - 1)
                val row = (event.y / rowH).toInt().coerceIn(0, PITCH_COUNT - 1)
                when (dragMode) {
                    DragMode.MOVE -> {
                        val newStart = (col - grabOffsetSteps).coerceIn(0, TOTAL_STEPS - note.durationSteps)
                        val newPitch = (pitchForRow(row) - grabOffsetPitch)
                            .coerceIn(LOWEST_PITCH, LOWEST_PITCH + PITCH_COUNT - 1)
                        if (newStart != note.startStep || newPitch != note.pitch) {
                            note.startStep = newStart; note.pitch = newPitch
                            gestureDirty = true; changed()
                        }
                    }
                    DragMode.RESIZE -> {
                        val newDur = (col - note.startStep + 1).coerceIn(1, TOTAL_STEPS - note.startStep)
                        if (newDur != note.durationSteps) {
                            note.durationSteps = newDur; gestureDirty = true; changed()
                        }
                    }
                    DragMode.NONE -> {}
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureDirty) { gestureDirty = false; onEditCommitted?.invoke() }
                activeNote = null; dragMode = DragMode.NONE
                invalidate()
                return true
            }
        }
        return true
    }

    private fun noteAt(col: Int, pitch: Int): Note? =
        notes.firstOrNull { it.pitch == pitch && col in it.startStep until it.startStep + it.durationSteps }

    private fun pitchForRow(row: Int) = LOWEST_PITCH + (PITCH_COUNT - 1 - row)
    private fun rowForPitch(pitch: Int) = PITCH_COUNT - 1 - (pitch - LOWEST_PITCH)
    private fun isBlack(pitch: Int) = pitch % 12 in intArrayOf(1, 3, 6, 8, 10)
    private fun changed() { onNotesChanged?.invoke(); invalidate() }
    private fun dp(v: Float) = v * resources.displayMetrics.density
}
