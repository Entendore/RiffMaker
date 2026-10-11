package com.example.riffmaker

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/** Writes a Standard MIDI File (format 0). Every event is preceded by its delta-time. */
object MidiWriter {

    private const val PPQ = 480
    private const val TICKS_PER_STEP = PPQ / 4
    private const val CHANNEL = 0

    fun write(notes: List<Note>, bpm: Float, totalSteps: Int, program: Int, swing: Float, out: OutputStream) {
        val track = ByteArrayOutputStream()

        // Tempo meta event (microseconds per quarter note) - delta 0
        val microPerQuarter = (60_000_000.0 / bpm).toInt()
        track.write(0x00)
        track.write(0xFF); track.write(0x51); track.write(0x03)
        track.write((microPerQuarter shr 16) and 0xFF)
        track.write((microPerQuarter shr 8) and 0xFF)
        track.write(microPerQuarter and 0xFF)

        // Time signature 4/4 - delta 0
        track.write(0x00)
        track.write(0xFF); track.write(0x58); track.write(0x04)
        track.write(4); track.write(2); track.write(24); track.write(8)

        // Program change - delta 0
        track.write(0x00)
        track.write(0xC0 or CHANNEL)
        track.write(program and 0x7F)

        val events = mutableListOf<MidiEvent>()
        for (n in notes) {
            val swingTick = if (n.startStep % 2 == 1) (swing * TICKS_PER_STEP).roundToInt() else 0
            val start = n.startStep * TICKS_PER_STEP + swingTick
            val end = start + n.durationSteps * TICKS_PER_STEP
            events.add(MidiEvent(start, 1, 0x90 or CHANNEL, n.pitch, n.velocity))
            events.add(MidiEvent(end, 0, 0x80 or CHANNEL, n.pitch, 0))
        }
        events.sortWith(compareBy({ it.tick }, { it.priority }))   // note-offs before note-ons

        var lastTick = 0
        for (e in events) {
            writeVlq(track, e.tick - lastTick)
            track.write(e.status)
            track.write(e.d1 and 0x7F)
            track.write(e.d2 and 0x7F)
            lastTick = e.tick
        }

        writeVlq(track, (totalSteps * TICKS_PER_STEP - lastTick).coerceAtLeast(0))
        track.write(0xFF); track.write(0x2F); track.write(0x00)

        out.write("MThd".toByteArray(Charsets.US_ASCII)); writeInt32(out, 6)
        writeInt16(out, 0); writeInt16(out, 1); writeInt16(out, PPQ)
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        val bytes = track.toByteArray()
        writeInt32(out, bytes.size); out.write(bytes)
    }

    private data class MidiEvent(val tick: Int, val priority: Int, val status: Int, val d1: Int, val d2: Int)

    private fun writeVlq(out: ByteArrayOutputStream, value: Int) {
        var v = value
        var buffer = v and 0x7F
        v = v shr 7
        while (v > 0) { buffer = (buffer shl 8) or ((v and 0x7F) or 0x80); v = v shr 7 }
        while (true) { out.write(buffer and 0xFF); if (buffer and 0x80 != 0) buffer = buffer shr 8 else break }
    }

    private fun writeInt32(out: OutputStream, v: Int) {
        out.write((v shr 24) and 0xFF); out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF); out.write(v and 0xFF)
    }

    private fun writeInt16(out: OutputStream, v: Int) {
        out.write((v shr 8) and 0xFF); out.write(v and 0xFF)
    }
}
