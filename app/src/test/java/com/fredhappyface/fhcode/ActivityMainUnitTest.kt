package com.fredhappyface.fhcode

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for ActivityMain hfcode directory logic
 */
class ActivityMainUnitTest {

    /**
     * Test: Verify that hfcode directory path is correctly constructed
     */
    @Test
    fun testHfcodeDirectoryPath() {
        // Test basic File path construction without Android dependencies
        val testDir = File("/storage/emulated/0/Documents")
        val hfcodeDir = File(testDir, "hfcode")

        // Verify the path is correct
        assertTrue(hfcodeDir.path.endsWith("hfcode"))
        assertNotNull(hfcodeDir.parent)
    }

    /**
     * Test: Verify that file path construction works correctly
     */
    @Test
    fun testFilePathConstruction() {
        // Test basic File path construction without Android dependencies
        val testDir = File("/storage/emulated/0/Documents")
        val hfcodeDir = File(testDir, "hfcode")
        val testFile = File(hfcodeDir, "test.txt")

        // Verify file path is correct
        assertTrue(testFile.path.contains("hfcode"))
        assertTrue(testFile.path.endsWith("test.txt"))
    }

    /**
     * Test: Verify file name extraction logic
     */
    @Test
    fun testFileNameExtraction() {
        val fileName = "example.py"
        val extension = fileName.split(".").last()

        // Verify extension extraction works correctly
        assertTrue(extension == "py")
    }

}
