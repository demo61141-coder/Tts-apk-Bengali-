package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    @Test
    fun tokenTable_writesAndVerifies84LinesWithInvisibleTokens() {
        val tempDir = Files.createTempDirectory("banglatts_tokens_test").toFile()
        try {
            val result = TokenTable.writeAndVerifyTokensFile(tempDir)
            assertTrue("Token validation should succeed", result.isValid)
            assertEquals(84, result.lineCount)

            val lines = File(tempDir, "tokens.txt").readLines(Charsets.UTF_8)
            assertEquals(84, lines.size)
            assertEquals("। 1", lines[0])
            assertEquals("<BLNK> 3", lines[1])
            assertEquals("\u200C 75", lines[73])
            assertEquals("  85", lines[83])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun wavWriter_writesValid22050HzMonoWavFile() {
        val tempDir = Files.createTempDirectory("banglatts_wav_test").toFile()
        try {
            val outWav = File(tempDir, "test.wav")
            val samples = FloatArray(22050) { 0.1f }
            val ok = WavWriter.writeWavFile(outWav, samples, 22050)
            assertTrue(ok)
            assertEquals(44L + 22050L * 2L, outWav.length())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
