package com.example.riffmaker

/** Maps MIDI pitches to guitar string/fret positions. Strings are stored low -> high. */
object TabMapper {

    val STANDARD = intArrayOf(40, 45, 50, 55, 59, 64)   // E2 A2 D3 G3 B3 E4
    val DROP_D = intArrayOf(38, 45, 50, 55, 59, 64)     // D2 A2 D3 G3 B3 E4
    val EB_STANDARD = intArrayOf(39, 44, 49, 54, 58, 63)

    const val MAX_FRET = 19
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun noteName(pitch: Int) = NAMES[pitch % 12] + (pitch / 12 - 1)

    /** Picks the playable position with the lowest fret. */
    fun map(pitch: Int, open: IntArray): Pair<Int, Int>? {  // (stringIndex, fret)
        var best: Pair<Int, Int>? = null
        for (s in open.indices) {
            val fret = pitch - open[s]
            if (fret in 0..MAX_FRET) {
                val cur = best
                if (cur == null || fret < cur.second) best = s to fret
            }
        }
        return best
    }
}
