package com.example.riffmaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabMapperTest {

    @Test
    fun `low E is open sixth string in standard tuning`() = assertEquals(0 to 0, TabMapper.map(40, TabMapper.STANDARD))

    @Test
    fun `high E prefers the open high string`() = assertEquals(5 to 0, TabMapper.map(64, TabMapper.STANDARD))

    @Test
    fun `drop D root is open sixth string`() = assertEquals(0 to 0, TabMapper.map(38, TabMapper.DROP_D))

    @Test
    fun `pitches beyond the fretboard return null`() {
        assertNull(TabMapper.map(90, TabMapper.STANDARD))
        assertNull(TabMapper.map(20, TabMapper.STANDARD))
    }

    @Test
    fun `note names are human readable`() {
        assertEquals("C3", TabMapper.noteName(48))
        assertEquals("A4", TabMapper.noteName(69))
    }
}
