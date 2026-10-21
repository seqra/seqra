package org.opentaint.python.sast.dataflow

import org.opentaint.dataflow.configuration.python.serialized.PythonPosition
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionModifier
import org.opentaint.dataflow.configuration.python.serialized.PythonTarget
import org.opentaint.python.config.PythonDefaultConfigLoader
import kotlin.test.Test
import kotlin.test.assertTrue

class PythonDefaultConfigTest {

    @Test
    fun `parses the shipped python config yaml end to end`() {
        val config = PythonDefaultConfigLoader.loadConfig() ?: error("Couldn't load config")

        assertTrue(config.entryPoint.isEmpty())
        assertTrue(config.source.isEmpty())
        assertTrue(config.sink.isEmpty())
        assertTrue(config.cleaner.isEmpty())
        assertTrue(config.passThrough.isNotEmpty())

        val parseaddr = config.passThrough.single {
            (it.target as? PythonTarget.Function)?.function == "email.utils.parseaddr"
        }
        assertTrue(parseaddr.copy.any { it.to is PythonPosition.WithModifiers })

        val zipFileCtor = config.passThrough.single {
            (it.target as? PythonTarget.Function)?.function == "zipfile.ZipFile"
        }
        val hasFieldModifier = zipFileCtor.copy.any { action ->
            (action.to as? PythonPosition.WithModifiers)
                ?.modifiers
                ?.any { it is PythonPositionModifier.Field && it.name == "filelist" } == true
        }
        assertTrue(hasFieldModifier, "expected `.filelist` field modifier on zipfile.ZipFile copy")
    }
}
