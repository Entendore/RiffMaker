package com.example.riffmaker

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

object Renderer {

    const val SAMPLE_RATE = 44100
    const val STEPS_PER_BEAT = 4

    enum class Wave(val label: String, val midiProgram: Int, val gain: Double) {
        SAW("Saw Lead", 80, 0.55),           // GM #81
        SQUARE("Square Lead", 79, 0.45),     // GM #80
        TRIANGLE("Triangle Bell", 10, 0.62), // GM #11 Vibraphone
        SINE("Pure Sine", 72, 0.72),         // GM #73 Flute
        PLUCK("Pluck", 28, 0.60)             // GM #29 Overdriven Guitar
    }

    /** swing: 0..0.5 step delay applied to odd 16ths (100% UI = 0.5). */
    fun render(
        notes: List<Note>, bpm: Float, totalSteps: Int,
        wave: Wave = Wave.SAW, swing: Float = 0f, metronome: Boolean = false
    ): ShortArray {
        val secPerStep = 60.0 / bpm / STEPS_PER_BEAT
        val tail = (0.08 * SAMPLE_RATE).toInt()
        val totalSamples = (totalSteps * secPerStep * SAMPLE_RATE).toInt() + tail

        val mix = DoubleArray(totalSamples)
        for (n in notes) {
            val delaySteps = if (n.startStep % 2 == 1) swing else 0f
            val start = ((n.startStep + delaySteps) * secPerStep * SAMPLE_RATE).toInt()
            val length = (n.durationSteps * secPerStep * SAMPLE_RATE).toInt()
            addVoice(mix, start, length, n.pitch, n.velocity / 127.0, wave)
        }

        if (metronome) {
            for (step in 0 until totalSteps step STEPS_PER_BEAT) {
                addClick(mix, (step * secPerStep * SAMPLE_RATE).toInt(), accent = step % 16 == 0)
            }
        }

        val out = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            out[i] = (tanh(mix[i]) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun addVoice(mix: DoubleArray, start: Int, length: Int, pitch: Int, velocity: Double, wave: Wave) {
        if (length <= 0 || start >= mix.size) return
        val freq = 440.0 * 2.0.pow((pitch - 69) / 12.0)
        val phaseInc = freq / SAMPLE_RATE
        var phase = 0.0
        var lp = 0.0
        val lpCoef = if (wave == Wave.PLUCK) 0.35 else 0.22
        val attack = (0.004 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val release = (0.03 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val decayTau = 0.11 * SAMPLE_RATE

        for (i in 0 until length) {
            val idx = start + i
            if (idx >= mix.size) break
            val raw = when (wave) {
                Wave.SAW, Wave.PLUCK -> 2.0 * phase - 1.0
                Wave.SQUARE -> if (phase < 0.5) 1.0 else -1.0
                Wave.TRIANGLE -> if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                Wave.SINE -> sin(2.0 * PI * phase)
            }
            lp += (raw - lp) * lpCoef
            val sustain = when {
                i < attack -> i.toDouble() / attack
                i >= length - release -> (length - i).toDouble() / release
                else -> 1.0
            }
            val env = if (wave == Wave.PLUCK) sustain * exp(-i / decayTau) else sustain
            mix[idx] += lp * env * velocity * wave.gain
            phase += phaseInc
            if (phase >= 1.0) phase -= 1.0
        }
    }

    private fun addClick(mix: DoubleArray, start: Int, accent: Boolean) {
        val freq = if (accent) 2000.0 else 1300.0
        val len = (0.015 * SAMPLE_RATE).toInt()
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= mix.size) break
            mix[idx] += sin(2 * PI * freq * i / SAMPLE_RATE) * exp(-i / (0.002 * SAMPLE_RATE)) * 0.45
        }
    }
}
