package com.example.riffmaker

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectStoreTest {

    private lateinit var dir: File

    @Before fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "projects-test-${System.nanoTime()}")
        check(dir.mkdirs())
    }

    @After fun tearDown() { dir.deleteRecursively() }

    private val state = ProjectStore.State(
        bpm = 132.5f, swingPct = 33, wave = Renderer.Wave.PLUCK,
        notes = listOf(Note(0, 4, 60, 90), Note(8, 2, 64))
    )

    @Test
    fun `round trip preserves the whole state`() {
        ProjectStore.save(dir, "my riff", state)
        val loaded = ProjectStore.load(dir, "my riff")
        assertEquals(132.5f, loaded.bpm, 0.001f)
        assertEquals(33, loaded.swingPct)
        assertEquals(Renderer.Wave.PLUCK, loaded.wave)
        assertEquals(state.notes, loaded.notes)
    }

    @Test
    fun `listing is sorted and hides autosave`() {
        ProjectStore.save(dir, "banana", state)
        ProjectStore.save(dir, "apple", state)
        ProjectStore.save(dir, "__autosave", state)
        assertEquals(listOf("apple", "banana"), ProjectStore.list(dir))
    }

    @Test
    fun `delete removes the project`() {
        ProjectStore.save(dir, "gone", state)
        assertTrue(ProjectStore.delete(dir, "gone"))
        assertFalse(ProjectStore.list(dir).contains("gone"))
    }

    @Test
    fun `names are sanitized for the filesystem`() {
        assertEquals("abc d--", ProjectStore.sanitize(" a/b\\c? d-- "))
        assertEquals("riff", ProjectStore.sanitize("///"))
    }
}
