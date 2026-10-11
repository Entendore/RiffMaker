package com.example.riffmaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns rendering + playback.
 * - Renders off the main thread; stale renders are discarded via a generation counter.
 * - Requests audio focus and stops on loss (calls, other apps).
 * - Stops when headphones are unplugged (ACTION_AUDIO_BECOMING_NOISY).
 * - All callbacks arrive on the main thread.
 */
class PlaybackEngine(
    context: Context,
    private val onStep: (Float) -> Unit,          // 16th-note position, or -1f when stopped
    private val onPlayingChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) main.post { stop() }   // a looper has no ducking - just stop
        }
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stop()
        }
    }
    private var receiverRegistered = false

    private var track: AudioTrack? = null
    private var generation = 0
    private var loopFrames = 1
    private var bpm = 120f
    private var playing = false

    val isPlaying: Boolean get() = playing

    private val ticker = object : Runnable {
        override fun run() {
            val t = track ?: return
            val secPerStep = 60.0 / bpm / Renderer.STEPS_PER_BEAT
            val frame = t.playbackHeadPosition % loopFrames
            onStep(
                (frame / (Renderer.SAMPLE_RATE * secPerStep)).toFloat()
                    .coerceIn(0f, PianoRollView.TOTAL_STEPS.toFloat())
            )
            main.postDelayed(this, 33)
        }
    }

    fun play(notes: List<Note>, bpm: Float, wave: Renderer.Wave, swing: Float, metronome: Boolean) {
        stop()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        this.bpm = bpm
        val gen = ++generation
        renderExecutor.execute {
            val pcm = try {
                Renderer.render(notes, bpm, PianoRollView.TOTAL_STEPS, wave, swing, metronome)
            } catch (e: Exception) {
                null
            } ?: return@execute
            main.post { if (gen == generation) startTrack(pcm) }   // drop renders from an aborted session
        }
    }

    fun stop() {
        generation++
        main.removeCallbacks(ticker)
        unregisterNoisy()
        track?.let {
            try { it.pause(); it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (playing) {
            playing = false
            onStep(-1f)
            onPlayingChanged(false)
        }
    }

    fun release() {
        stop()
        renderExecutor.shutdown()
    }

    private fun startTrack(pcm: ShortArray) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Renderer.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, pcm.size, -1)
        try {
            t.play()
        } catch (_: IllegalStateException) {
            t.release()
            return
        }
        loopFrames = pcm.size
        track = t
        playing = true
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        onPlayingChanged(true)
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    private fun unregisterNoisy() {
        if (receiverRegistered) {
            try { appContext.unregisterReceiver(noisyReceiver) } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }
    }
}
