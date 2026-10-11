package com.example.riffmaker

/** One note in the grid. startStep is a 16th-note step index. */
data class Note(
    var startStep: Int,
    var durationSteps: Int,
    var pitch: Int,          // MIDI note number (48 = C3)
    var velocity: Int = 100
)
