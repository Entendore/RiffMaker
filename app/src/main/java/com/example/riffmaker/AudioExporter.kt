package com.example.riffmaker

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes 16-bit mono PCM to AAC-LC inside an .m4a container.
 * Android has no system MP3 encoder - AAC is the built-in alternative.
 */
object AudioExporter {

    fun encodeToM4a(pcm: ShortArray, sampleRate: Int, outFile: File) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val encoder = MediaCodec.createEncoderByType(mime)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val format = MediaFormat.createAudioFormat(mime, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val info = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false
            var inputDone = false
            var outputDone = false
            var readOffset = 0
            var samplesFed = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = encoder.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val samplesLeft = pcm.size - readOffset
                        if (samplesLeft <= 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0,
                                samplesToUs(samplesFed, sampleRate),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunk = minOf(inBuf.remaining() / 2, samplesLeft)
                            for (i in 0 until chunk) inBuf.putShort(pcm[readOffset + i])
                            encoder.queueInputBuffer(inIndex, 0, chunk * 2,
                                samplesToUs(samplesFed, sampleRate), 0)
                            readOffset += chunk
                            samplesFed += chunk
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            val outBuf = encoder.getOutputBuffer(outIndex)!!
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun samplesToUs(samples: Long, sampleRate: Int): Long =
        samples * 1_000_000L / sampleRate
}
