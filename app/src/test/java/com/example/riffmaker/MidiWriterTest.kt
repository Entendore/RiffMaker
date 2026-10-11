package com.example.riffmaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

class MidiWriterTest {

    private fun bytes(notes: List<Note>): ByteArray {
        val out = ByteArrayOutputStream()
        MidiWriter.write(notes, 120f, PianoRollView.TOTAL_STEPS, 0, 0f, out)
        return out.toByteArray()
    }

    private fun ByteArray.indexOfBytes(seq: ByteArray): Int {
        outer@ for (i in 0..this.size - seq.size) {
            for (j in seq.indices) if (this[i + j] != seq[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `header is MThd format 0, one track, 480 ticks per quarter`() {
        val b = bytes(emptyList())
        assertEquals("MThd", String(b, 0, 4, Charsets.US_ASCII))
        assertEquals(0, ((b[8].toInt() and 0xFF) shl 8) or (b[9].toInt() and 0xFF))
        assertEquals(1, ((b[10].toInt() and 0xFF) shl 8) or (b[11].toInt() and 0xFF))
        assertEquals(480, ((b[12].toInt() and 0xFF) shl 8) or (b[13].toInt() and 0xFF))
        assertEquals("MTrk", String(b, 14, 4, Charsets.US_ASCII))
        val declared = ((b[18].toInt() and 0xFF) shl 24) or ((b[19].toInt() and 0xFF) shl 16) or
                ((b[20].toInt() and 0xFF) shl 8) or (b[21].toInt() and 0xFF)
        assertEquals(b.size - 22, declared)
    }

    @Test
    fun `tempo meta event encodes 120 bpm`() {
        val b = bytes(emptyList())
        val tempo = byteArrayOf(0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20)
        assertTrue(b.indexOfBytes(tempo) >= 0)
    }

    @Test
    fun `parses as a valid Standard MIDI File (JDK parser)`() {
        val b = bytes(listOf(Note(0, 4, 60, 100), Note(8, 2, 64, 90)))
        val seq = MidiSystem.getSequence(ByteArrayInputStream(b))
        assertEquals(480f, seq.resolution, 0f)
        assertEquals(1, seq.tracks.size)
    }

    @Test
    fun `note on and note off land on the right ticks`() {
        val b = bytes(listOf(Note(0, 4, 60, 100)))
        val track = MidiSystem.getSequence(ByteArrayInputStream(b)).tracks[0]
        var onTick = -1L; var offTick = -1L
        for (i in 0 until track.size()) {
            val e = track.get(i)
            val m = e.message as? ShortMessage ?: continue
            if (m.note != 60) continue
            when (m.command) {
                ShortMessage.NOTE_ON -> onTick = e.tick
                ShortMessage.NOTE_OFF -> offTick = e.tick
            }
        }
        assertEquals(0L, onTick)
        assertEquals(480L, offTick)
    }

    @Test
    fun `at a shared tick, note off comes before note on (no stuck notes)`() {
        val b = bytes(listOf(Note(0, 4, 60, 100), Note(4, 4, 60, 100)))
        val track = MidiSystem.getSequence(ByteArrayInputStream(b)).tracks[0]
        var sawOff = false; var sawOn = false; var offFirst = false
        for (i in 0 until track.size()) {
            val e = track.get(i)
            val m = e.message as? ShortMessage ?: continue
            if (m.note != 60) continue
            if (m.command == ShortMessage.NOTE_OFF) sawOff = true
            if (m.command == ShortMessage.NOTE_ON && e.tick == 480L) { sawOn = true; offFirst = sawOff }
        }
        assertTrue(sawOn && sawOff && offFirst)
    }
}
