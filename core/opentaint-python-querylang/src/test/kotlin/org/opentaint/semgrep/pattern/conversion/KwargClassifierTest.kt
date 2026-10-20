package org.opentaint.semgrep.pattern.conversion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KwargClassifierTest {
    @Test fun `classifier then name round-trips the keyword`() {
        for (name in listOf("data", "cmd", "x", "kwarg")) {
            assertEquals(name, PythonLanguageStrategy.kwargClassifierNameOrNull(PythonLanguageStrategy.kwargClassifier(name)))
        }
    }

    @Test fun `name returns null for non-kwarg classifiers`() {
        assertNull(PythonLanguageStrategy.kwargClassifierNameOrNull("X"))
        assertNull(PythonLanguageStrategy.kwargClassifierNameOrNull("*->0"))
        assertNull(PythonLanguageStrategy.kwargClassifierNameOrNull("tainted"))
    }
}
