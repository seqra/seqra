package org.opentaint.ir.testing.persistence.incrementality

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.testing.WithDb
import org.opentaint.ir.testing.cookJar
import org.opentaint.ir.testing.createTempJar
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class IncrementalDbTest {

    companion object : WithDb() {
        private val keap_0_2_1 = "https://repo1.maven.org/maven2/com/github/penemue/keap/0.2.1/keap-0.2.1.jar"
        private val keap_0_2_2 = "https://repo1.maven.org/maven2/com/github/penemue/keap/0.2.2/keap-0.2.2.jar"
    }

    @Test
    fun `two keaps`(): Unit = runBlocking {
        val modelJar = createTempJar("model.jar")
        val firstJar = cookJar(keap_0_2_1)
        Files.copy(firstJar, modelJar, StandardCopyOption.REPLACE_EXISTING)
        val cp = db.classpath(listOf(modelJar.toFile()))
        db.awaitBackgroundJobs()
        val bc1 = cp.findClass("com.github.penemue.keap.PriorityQueue").bytecode()
        // overwrite jar
        Files.copy(cookJar(keap_0_2_2), modelJar, StandardCopyOption.REPLACE_EXISTING)
        db.refresh()
        db.awaitBackgroundJobs()
        Assertions.assertFalse(bc1 contentEquals cp.findClass("com.github.penemue.keap.PriorityQueue").bytecode())
        // rollback jar
        Files.copy(firstJar, modelJar, StandardCopyOption.REPLACE_EXISTING)
        db.refresh()
        db.awaitBackgroundJobs()
        Assertions.assertTrue(bc1 contentEquals cp.findClass("com.github.penemue.keap.PriorityQueue").bytecode())
    }
}