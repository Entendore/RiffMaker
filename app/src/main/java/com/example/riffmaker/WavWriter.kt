package com.example.riffmaker

import java.io.OutputStream

object WavWriter {
    fun write(pcm: ShortArray, sampleRate: Int, out: OutputStream) {
        val dataLen = pcm.size * 2
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun le32(v: Int) { le16(v and 0xFFFF); le16((v shr 16) and 0xFFFF) }
        fun tag(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

        tag("RIFF"); le32(36 + dataLen); tag("WAVE")
        tag("fmt "); le32(16); le16(1); le16(1)          // PCM, mono
        le32(sampleRate); le32(sampleRate * 2)           // byte rate
        le16(2); le16(16)                                // block align, bits per sample
        tag("data"); le32(dataLen)
        for (s in pcm) le16(s.toInt() and 0xFFFF)
        out.flush()
    }
}
