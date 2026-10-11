package com.example.riffmaker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectStore {

    data class State(val bpm: Float, val swingPct: Int, val wave: Renderer.Wave, val notes: List<Note>)

    fun projectsDir(ctx: Context): File = File(ctx.filesDir, "projects").apply { mkdirs() }

    // Context convenience wrappers
    fun save(ctx: Context, name: String, state: State) { save(projectsDir(ctx), name, state) }
    fun list(ctx: Context): List<String> = list(projectsDir(ctx))
    fun load(ctx: Context, name: String): State = load(projectsDir(ctx), name)
    fun delete(ctx: Context, name: String): Boolean = delete(projectsDir(ctx), name)

    // Pure versions (unit-tested on the JVM)
    fun save(dir: File, name: String, state: State): File {
        dir.mkdirs()
        val arr = JSONArray()
        for (n in state.notes) arr.put(
            JSONObject().put("s", n.startStep).put("d", n.durationSteps)
                .put("p", n.pitch).put("v", n.velocity)
        )
        val obj = JSONObject()
            .put("bpm", state.bpm.toDouble())
            .put("swing", state.swingPct)
            .put("wave", state.wave.name)
            .put("notes", arr)
        val file = File(dir, sanitize(name) + ".json")
        file.writeText(obj.toString())
        return file
    }

    fun list(dir: File): List<String> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { !it.startsWith("__") }        // hide autosave
            ?.sorted() ?: emptyList()

    fun load(dir: File, name: String): State {
        val obj = JSONObject(File(dir, sanitize(name) + ".json").readText())
        val notes = mutableListOf<Note>()
        val arr = obj.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            notes.add(Note(o.getInt("s"), o.getInt("d"), o.getInt("p"), o.optInt("v", 100)))
        }
        return State(
            bpm = obj.optDouble("bpm", 120.0).toFloat(),
            swingPct = obj.optInt("swing", 0),
            wave = try { Renderer.Wave.valueOf(obj.optString("wave", "SAW")) } catch (_: Exception) { Renderer.Wave.SAW },
            notes = notes
        )
    }

    fun delete(dir: File, name: String): Boolean = File(dir, sanitize(name) + ".json").delete()

    fun sanitize(name: String) =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "riff" }
}
