package com.cx330.toolbox.core.inspect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `deletes json and matching png`() {
        val json = temp.newFile("capture-1.json")
        val png = temp.newFile("capture-1.png")
        val other = temp.newFile("capture-2.json")

        CaptureFiles.delete(json)

        assertFalse(json.exists())
        assertFalse(png.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `missing png does not throw`() {
        val json = temp.newFile("capture-3.json")

        CaptureFiles.delete(json)

        assertFalse(json.exists())
        assertFalse(File(temp.root, "capture-3.png").exists())
    }
}
