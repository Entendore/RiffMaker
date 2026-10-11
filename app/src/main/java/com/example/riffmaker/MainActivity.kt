package com.example.riffmaker

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object { private const val AUTOSAVE = "__autosave" }

    private lateinit var pianoRoll: PianoRollView
    private lateinit var tabView: TabView
    private lateinit var sheetView: SheetView
    private lateinit var pianoScroll: HorizontalScrollView
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var sheetScroll: ScrollView
    private lateinit var playButton: Button
    private lateinit var modeButton: Button
    private lateinit var bpmInput: EditText
    private lateinit var swingLabel: TextView
    private lateinit var swingSeek: SeekBar
    private lateinit var instrumentButton: Button
    private lateinit var metroButton: Button
    private lateinit var tuningButton: Button
    private lateinit var viewButtons: List<Button>
    private var menu: Menu? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val exportExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var engine: PlaybackEngine

    private var bpm = 120f
    private var swingPct = 0
    private var wave = Renderer.Wave.SAW
    private var metronome = false
    private var tuningIndex = 0
    private val tunings = listOf(
        "Standard" to TabMapper.STANDARD,
        "Drop D" to TabMapper.DROP_D,
        "Eb Standard" to TabMapper.EB_STANDARD
    )

    private var current: List<Note> = emptyList()
    private val undoStack = ArrayDeque<List<Note>>()
    private val redoStack = ArrayDeque<List<Note>>()

    private val swing get() = swingPct / 100f * 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        pianoRoll = findViewById(R.id.pianoRoll)
        tabView = findViewById(R.id.tabView)
        sheetView = findViewById(R.id.sheetView)
        pianoScroll = findViewById(R.id.pianoScroll)
        tabScroll = findViewById(R.id.tabScroll)
        sheetScroll = findViewById(R.id.sheetScroll)
        playButton = findViewById(R.id.playButton)
        modeButton = findViewById(R.id.modeButton)
        bpmInput = findViewById(R.id.bpmInput)
        swingLabel = findViewById(R.id.swingLabel)
        swingSeek = findViewById(R.id.swingSeek)
        instrumentButton = findViewById(R.id.instrumentButton)
        metroButton = findViewById(R.id.metroButton)
        tuningButton = findViewById(R.id.tuningButton)
        viewButtons = listOf(findViewById(R.id.btnViewPiano), findViewById(R.id.btnViewTab), findViewById(R.id.btnViewSheet))

        engine = PlaybackEngine(this, onStep = { onPlaybackStep(it) }, onPlayingChanged = { onPlayingChanged(it) })

        pianoRoll.onNotesChanged = {
            tabView.notes = pianoRoll.snapshot
            sheetView.notes = pianoRoll.snapshot
        }
        pianoRoll.onEditCommitted = {
            commitHistory()
            if (engine.isPlaying) startPlayback()   // live editing: loop restarts with new notes
        }
        tabView.tuning = tunings[tuningIndex].second

        playButton.setOnClickListener { if (engine.isPlaying) stopPlayback() else startPlayback() }
        modeButton.text = getString(R.string.draw)
        modeButton.setOnClickListener {
            pianoRoll.eraseMode = !pianoRoll.eraseMode
            modeButton.text = getString(if (pianoRoll.eraseMode) R.string.erase else R.string.draw)
        }

        bpmInput.setText(bpm.toInt().toString())
        bpmInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.toFloatOrNull() ?: return
                if (v in 40f..240f) {
                    bpm = v
                    if (engine.isPlaying) { stopPlayback(); startPlayback() }
                }
            }
        })

        swingSeek.max = 100
        swingSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                swingPct = value
                swingLabel.text = getString(R.string.swing_val, value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (engine.isPlaying) { stopPlayback(); startPlayback() }
            }
        })
        swingLabel.text = getString(R.string.swing_val, swingPct)

        instrumentButton.text = wave.label
        instrumentButton.setOnClickListener { pickInstrument() }

        metroButton.text = getString(R.string.met_off)
        metroButton.setOnClickListener {
            metronome = !metronome
            metroButton.text = getString(if (metronome) R.string.met_on else R.string.met_off)
            if (engine.isPlaying) { stopPlayback(); startPlayback() }
        }

        tuningButton.text = tunings[tuningIndex].first
        tuningButton.setOnClickListener { pickTuning() }

        viewButtons.forEachIndexed { i, b -> b.setOnClickListener { showView(i) } }
        showView(0)

        restoreSession()
        offerBugReport()
        maybeShowWelcome()
    }

    // ---------- Session ----------

    private fun restoreSession() {
        val s = try { ProjectStore.load(this, AUTOSAVE) } catch (_: Exception) { null } ?: return
        pianoRoll.setNotes(s.notes)
        current = s.notes
        bpm = s.bpm.coerceIn(40f, 240f)
        bpmInput.setText(bpm.toInt().toString())
        swingPct = s.swingPct
        swingSeek.progress = swingPct
        swingLabel.text = getString(R.string.swing_val, swingPct)
        wave = s.wave
        instrumentButton.text = wave.label
        refreshSecondary()
    }

    private fun autosave() {
        try {
            ProjectStore.save(this, AUTOSAVE, ProjectStore.State(bpm, swingPct, wave, pianoRoll.snapshot))
        } catch (_: Exception) {}
    }

    private fun maybeShowWelcome() {
        val prefs = getSharedPreferences("riffmaker", MODE_PRIVATE)
        if (!prefs.getBoolean("welcomed", false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_msg)
                .setPositiveButton(R.string.got_it, null)
                .show()
            prefs.edit().putBoolean("welcomed", true).apply()
        }
    }

    private fun offerBugReport() {
        val report = CrashReporter.takeReport(this) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.bug_title)
            .setMessage(R.string.bug_msg)
            .setPositiveButton(R.string.send) { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email)))
                    putExtra(Intent.EXTRA_SUBJECT, "Riff Maker bug report")
                    putExtra(Intent.EXTRA_TEXT, report.take(4000))
                }
                try { startActivity(Intent.createChooser(intent, getString(R.string.send))) }
                catch (_: ActivityNotFoundException) {}
            }
            .setNegativeButton(R.string.no_thanks, null)
            .show()
    }

    // ---------- View switching ----------

    private fun showView(which: Int) {
        pianoScroll.visibility = if (which == 0) View.VISIBLE else View.GONE
        tabScroll.visibility = if (which == 1) View.VISIBLE else View.GONE
        sheetScroll.visibility = if (which == 2) View.VISIBLE else View.GONE
        viewButtons.forEachIndexed { i, b ->
            b.setBackgroundColor(if (i == which) Color.parseColor("#22D3A7") else Color.parseColor("#1A2032"))
            b.setTextColor(if (i == which) Color.BLACK else Color.WHITE)
        }
    }

    // ---------- Dialogs ----------

    private fun pickInstrument() {
        val waves = Renderer.Wave.values()
        val names = waves.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.instrument)
            .setSingleChoiceItems(names, waves.indexOf(wave)) { d, i ->
                wave = waves[i]
                instrumentButton.text = wave.label
                if (engine.isPlaying) { stopPlayback(); startPlayback() }
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickTuning() {
        val names = tunings.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_tuning)
            .setSingleChoiceItems(names, tuningIndex) { d, i ->
                tuningIndex = i
                tabView.tuning = tunings[i].second
                tuningButton.text = tunings[i].first
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- History ----------

    private fun commitHistory() {
        val snap = pianoRoll.snapshot
        if (snap == current) return
        undoStack.addLast(current)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        current = snap
        autosave()
        invalidateMenuItems()
    }

    private fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current); current = prev
        pianoRoll.setNotes(prev); refreshSecondary(); invalidateMenuItems(); autosave()
    }

    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current); current = next
        pianoRoll.setNotes(next); refreshSecondary(); invalidateMenuItems(); autosave()
    }

    private fun refreshSecondary() {
        tabView.notes = pianoRoll.snapshot
        sheetView.notes = pianoRoll.snapshot
    }

    private fun invalidateMenuItems() {
        menu?.findItem(R.id.action_undo)?.isEnabled = undoStack.isNotEmpty()
        menu?.findItem(R.id.action_redo)?.isEnabled = redoStack.isNotEmpty()
    }

    // ---------- Playback ----------

    private fun startPlayback() = engine.play(pianoRoll.snapshot, bpm, wave, swing, metronome)

    private fun stopPlayback() = engine.stop()

    private fun onPlayingChanged(playing: Boolean) {
        playButton.text = getString(if (playing) R.string.stop else R.string.play)
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun onPlaybackStep(step: Float) {
        if (step < 0f) {
            pianoRoll.playheadStep = -1f
            tabView.playheadStep = -1f
            sheetView.playheadStep = -1f
            return
        }
        pianoRoll.playheadStep = step
        tabView.playheadStep = step
        sheetView.playheadStep = step
        autoScroll(pianoScroll, pianoRoll.playheadX(step))
        autoScroll(tabScroll, tabView.playheadX(step))
    }

    private fun autoScroll(hsv: HorizontalScrollView, x: Float) {
        val left = hsv.scrollX + hsv.width / 4
        val right = hsv.scrollX + hsv.width * 3 / 4
        if (x < left || x > right) {
            hsv.smoothScrollTo((x - hsv.width / 2).toInt().coerceAtLeast(0), 0)
        }
    }

    // ---------- Menu / projects ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        invalidateMenuItems()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_undo -> { undo(); true }
        R.id.action_redo -> { redo(); true }
        R.id.action_save_project -> { saveDialog(); true }
        R.id.action_open_project -> { openDialog(); true }
        R.id.action_delete_project -> { deleteDialog(); true }
        R.id.action_export_midi -> { exportMidi(); true }
        R.id.action_export_wav -> { exportWav(); true }
        R.id.action_export_m4a -> { exportM4a(); true }
        R.id.action_save_downloads -> { saveWavToDownloads(); true }
        R.id.action_rate -> { openPlayStore(); true }
        R.id.action_clear -> { stopPlayback(); pianoRoll.clearAll(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun saveDialog() {
        val input = EditText(this).apply { setText("riff"); setHint(R.string.project_name) }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_project)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show()
                } else {
                    ProjectStore.save(this, name, ProjectStore.State(bpm, swingPct, wave, pianoRoll.snapshot))
                    Toast.makeText(this, getString(R.string.saved_toast, name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openDialog() {
        val names = ProjectStore.list(this)
        if (names.isEmpty()) { Toast.makeText(this, R.string.no_projects, Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.open_project)
            .setItems(names.toTypedArray()) { _, i ->
                stopPlayback()
                val s = ProjectStore.load(this, names[i])
                pianoRoll.setNotes(s.notes); commitHistory()
                bpm = s.bpm.coerceIn(40f, 240f); bpmInput.setText(bpm.toInt().toString())
                swingPct = s.swingPct; swingSeek.progress = swingPct
                swingLabel.text = getString(R.string.swing_val, swingPct)
                wave = s.wave; instrumentButton.text = wave.label
                refreshSecondary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteDialog() {
        val names = ProjectStore.list(this)
        if (names.isEmpty()) { Toast.makeText(this, R.string.no_projects, Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_project)
            .setItems(names.toTypedArray()) { _, i ->
                if (ProjectStore.delete(this, names[i])) {
                    Toast.makeText(this, getString(R.string.deleted_toast, names[i]), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Export / share (background thread, cleanup on failure) ----------

    private fun exportDir() = File(getExternalFilesDir(null) ?: filesDir, "exports").apply { mkdirs() }
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun requireNotes(): List<Note>? {
        val notes = pianoRoll.snapshot
        if (notes.isEmpty()) { Toast.makeText(this, R.string.no_notes, Toast.LENGTH_SHORT).show(); return null }
        return notes
    }

    private fun toast(msg: String) {
        mainHandler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun runExport(work: () -> File) {
        exportExecutor.execute {
            var out: File? = null
            try {
                out = work()
                val f = out!!
                mainHandler.post { shareFile(f, mimeFor(f)) }
            } catch (e: Exception) {
                out?.delete()   // never leave a corrupt partial file behind
                toast(getString(R.string.export_failed, e.message ?: "unknown error"))
            }
        }
    }

    private fun mimeFor(f: File) = when (f.extension) {
        "mid" -> "audio/midi"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    private fun exportMidi() {
        val notes = requireNotes() ?: return
        val bpmNow = bpm; val swingNow = swing; val programNow = wave.midiProgram
        runExport {
            val file = File(exportDir(), "riff_${stamp()}.mid")
            FileOutputStream(file).use {
                MidiWriter.write(notes, bpmNow, PianoRollView.TOTAL_STEPS, programNow, swingNow, it)
            }
            file
        }
    }

    private fun exportWav() {
        val notes = requireNotes() ?: return
        val bpmNow = bpm; val swingNow = swing; val waveNow = wave
        runExport {
            val pcm = Renderer.render(notes, bpmNow, PianoRollView.TOTAL_STEPS, waveNow, swingNow, metronome = false)
            val file = File(exportDir(), "riff_${stamp()}.wav")
            FileOutputStream(file).use { WavWriter.write(pcm, Renderer.SAMPLE_RATE, it) }
            file
        }
    }

    private fun exportM4a() {
        val notes = requireNotes() ?: return
        val bpmNow = bpm; val swingNow = swing; val waveNow = wave
        runExport {
            val pcm = Renderer.render(notes, bpmNow, PianoRollView.TOTAL_STEPS, waveNow, swingNow, metronome = false)
            val file = File(exportDir(), "riff_${stamp()}.m4a")
            AudioExporter.encodeToM4a(pcm, Renderer.SAMPLE_RATE, file)
            file
        }
    }

    /** No permission needed on API 29+. */
    private fun saveWavToDownloads() {
        val notes = requireNotes() ?: return
        val bpmNow = bpm; val swingNow = swing; val waveNow = wave
        exportExecutor.execute {
            try {
                val pcm = Renderer.render(notes, bpmNow, PianoRollView.TOTAL_STEPS, waveNow, swingNow, metronome = false)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "riff_${stamp()}.wav")
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create file")
                contentResolver.openOutputStream(uri)?.use {
                    WavWriter.write(pcm, Renderer.SAMPLE_RATE, it)
                } ?: error("Could not open file for writing")
                mainHandler.post {
                    Toast.makeText(applicationContext, R.string.saved_downloads, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                toast(getString(R.string.save_failed, e.message ?: "unknown error"))
            }
        }
    }

    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun shareFile(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_riff)))
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        autosave()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
        exportExecutor.shutdownNow()
    }
}
