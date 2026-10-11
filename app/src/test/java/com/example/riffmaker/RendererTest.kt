package com.example.riffmaker

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererTest {

    @Test
    fun `loop length matches pattern duration plus release tail`() {
        val pcm = Renderer.render(emptyList(), 120f, 64)
        val secPerStep = 60.0 / 120f / Renderer.STEPS_PER_BEAT
        val expected = (64 * secPerStep * Renderer.SAMPLE_RATE).toInt() + (0.08 * Renderer.SAMPLE_RATE).toInt()
        assertEquals(expected, pcm.size)
    }

    @Test
    fun `empty pattern is silent`() {
        assertTrue(Renderer.render(emptyList(), 120f, 64).all { it.toInt() == 0 })
    }

    @Test
    fun `pattern with notes is not silent`() {
        assertTrue(Renderer.render(listOf(Note(0, 4, 60)), 120f, 64).any { it.toInt() != 0 })
    }

    @Test
    fun `output never clips past full scale even when dense`() {
        val dense = (0 until 20).map { Note(it, 2, 60) }
        assertTrue(Renderer.render(dense, 120f, 64).all { abs(it.toInt()) <= 32767 })
    }

    @Test
    fun `swing delays notes on odd steps`() {
        fun firstSound(swing: Float) =
            Renderer.render(listOf(Note(1, 1, 60)), 120f, 64, Renderer.Wave.SAW, swing)
                .indexOfFirst { it.toInt() != 0 }
        assertTrue(firstSound(0.5f) > firstSound(0f))
    }
}
