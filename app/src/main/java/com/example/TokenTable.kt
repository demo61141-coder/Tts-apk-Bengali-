package com.example

import java.io.File

/**
 * Embedded 84-line sherpa-onnx token table for the single-speaker Bengali VITS model.
 * - Line 74 (id 75) has token U+200C (Zero-Width Non-Joiner, ZWNJ: ‌).
 * - Line 84 (id 85) has token U+0020 (plain space: ' ').
 * - Total lines: 84 (IDs: 1, then 3..85; IDs 0 and 2 do not exist).
 */
object TokenTable {

    const val EXPECTED_LINE_COUNT = 84

    val EMBEDDED_TOKENS_RAW: String = """। 1
<BLNK> 3
ঁ 4
ং 5
ঃ 6
অ 7
আ 8
ই 9
ঈ 10
উ 11
ঊ 12
ঋ 13
এ 14
ঐ 15
ও 16
ঔ 17
ক 18
খ 19
গ 20
ঘ 21
ঙ 22
চ 23
ছ 24
জ 25
ঝ 26
ঞ 27
ট 28
ঠ 29
ড 30
ঢ 31
ণ 32
ত 33
থ 34
দ 35
ধ 36
ন 37
প 38
ফ 39
ব 40
ভ 41
ম 42
য 43
র 44
ল 45
শ 46
ষ 47
স 48
হ 49
় 50
া 51
ি 52
ী 53
ু 54
ূ 55
ৃ 56
ে 57
ৈ 58
ো 59
ৌ 60
্ 61
ৎ 62
ৗ 63
ড় 64
ঢ় 65
য় 66
০ 67
১ 68
২ 69
৩ 70
৪ 71
৫ 72
৬ 73
৮ 74
‌ 75
— 76
‘ 77
’ 78
. 79
? 80
- 81
! 82
| 83
, 84
  85"""

    data class ValidationResult(
        val isValid: Boolean,
        val lineCount: Int,
        val filePath: String,
        val errorMessageBn: String? = null
    )

    /**
     * Writes the exact 84-line token table to [filesDir]/tokens.txt and verifies:
     * 1. File exists and is readable.
     * 2. Line count is strictly 84.
     * 3. Line 74 (0-indexed 73) starts with U+200C (ZWNJ) for ID 75.
     * 4. Line 84 (0-indexed 83) starts with U+0020 (space) for ID 85.
     */
    fun writeAndVerifyTokensFile(filesDir: File): ValidationResult {
        val tokensFile = File(filesDir, "tokens.txt")
        return try {
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }
            tokensFile.writeText(EMBEDDED_TOKENS_RAW + "\n", Charsets.UTF_8)

            val writtenContent = tokensFile.readText(Charsets.UTF_8)
            val lines = writtenContent
                .split("\n")
                .let { all ->
                    if (all.isNotEmpty() && all.last().isEmpty()) all.dropLast(1) else all
                }

            if (lines.size != EXPECTED_LINE_COUNT) {
                return ValidationResult(
                    isValid = false,
                    lineCount = lines.size,
                    filePath = tokensFile.absolutePath,
                    errorMessageBn = "টোকেন ফাইল যাচাই ব্যর্থ হয়েছে: প্রত্যাশিত ৮৪ লাইন, পাওয়া গেছে ${lines.size} লাইন। ভয়েস ইঞ্জিন চালু করা যাবে না।"
                )
            }

            val zwnjLine = lines[73]
            if (zwnjLine != "\u200C 75") {
                return ValidationResult(
                    isValid = false,
                    lineCount = lines.size,
                    filePath = tokensFile.absolutePath,
                    errorMessageBn = "টোকেন ফাইল ত্রুটি: আইডি ৭৫ (ZWNJ U+200C) সঠিক নয়।"
                )
            }

            val spaceLine = lines[83]
            if (spaceLine != "  85") {
                return ValidationResult(
                    isValid = false,
                    lineCount = lines.size,
                    filePath = tokensFile.absolutePath,
                    errorMessageBn = "টোকেন ফাইল ত্রুটি: আইডি ৮৫ (Space U+0020) সঠিক নয়।"
                )
            }

            ValidationResult(
                isValid = true,
                lineCount = lines.size,
                filePath = tokensFile.absolutePath,
                errorMessageBn = null
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                lineCount = 0,
                filePath = tokensFile.absolutePath,
                errorMessageBn = "টোকেন ফাইল লিখতে বা যাচাই করতে ব্যর্থ: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
            )
        }
    }
}
