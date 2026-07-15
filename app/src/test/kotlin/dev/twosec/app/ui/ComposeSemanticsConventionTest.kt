package dev.twosec.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComposeSemanticsConventionTest {

    @Test
    fun `every interactive widget in ui has a testTag within ten lines`() {
        val violations = mutableListOf<String>()
        val sourceRoot = File("src/main/kotlin/dev/twosec/app/ui")
        val widgetPattern = Regex("""\b(Switch|Checkbox|IconButton|Button)\s*\(""")
        val testTagPattern = Regex("""\btestTag\s*[=(]""")

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    if (widgetPattern.containsMatchIn(line)) {
                        val window = lines.drop(index + 1).take(WINDOW_LINES)
                        if (window.none { testTagPattern.containsMatchIn(it) }) {
                            violations += "${file.name}:${index + 1}  ${line.trim()}  has no testTag within $WINDOW_LINES lines"
                        }
                    }
                }
            }

        assertTrue(
            "Interactive widgets without testTag:\n  ${violations.joinToString("\n  ")}",
            violations.isEmpty(),
        )
    }

    private companion object {
        const val WINDOW_LINES = 10
    }
}
