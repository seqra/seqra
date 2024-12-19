package org.opentaint.ir.testing

import org.opentaint.ir.impl.fs.JarFacade
import org.opentaint.ir.impl.fs.parseRuntimeVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE
import org.junit.platform.commons.util.ModuleUtils
import java.nio.file.Paths
import java.util.jar.JarFile

class JarFacadeTest {
    companion object {
        private val moduleUtilsEntry = "org/junit/platform/commons/util/ModuleUtils.class"

        private val moduleUtils = ModuleUtils::class.java.name

        private val java8Version = parseRuntimeVersion("1.8")
        private val java9Version = parseRuntimeVersion("9.0.0")
        private val java11Version = parseRuntimeVersion("11.0.15")
    }

    private val junitPlatformCommons = allClasspath.first { it.absolutePath.contains("junit-platform-commons-1.9") }

    @Test
    fun `test for java 8`() {
        val junitPlatform = JarFacade(java8Version.majorVersion) {
            JarFile(junitPlatformCommons)
        }
        assertTrue(junitPlatform.classes.contains(moduleUtils))
        assertTrue(junitPlatform.classes.all { !it.key.contains("META-INF") })
        with(junitPlatform.classes[moduleUtils]) {
            assertNotNull(this)
            assertEquals(moduleUtilsEntry, this?.name)
        }
    }

    @Test
    fun `test for java 9`() {
        val junitPlatform = JarFacade(java9Version.majorVersion) {
            JarFile(junitPlatformCommons)
        }
        assertTrue(junitPlatform.classes.contains(moduleUtils))
        assertTrue(junitPlatform.classes.all { !it.key.contains("META-INF") })
        with(junitPlatform.classes[moduleUtils]) {
            assertNotNull(this)
            assertEquals("META-INF/versions/9/$moduleUtilsEntry", this?.name)
        }
    }

    @Test
    fun `test for java 11`() {
        val junitPlatform = JarFacade(java11Version.majorVersion) {
            JarFile(junitPlatformCommons)
        }
        assertTrue(junitPlatform.classes.contains(moduleUtils))
        assertTrue(junitPlatform.classes.all { !it.key.contains("META-INF") })
        with(junitPlatform.classes[moduleUtils]) {
            assertNotNull(this)
            assertEquals("META-INF/versions/9/$moduleUtilsEntry", this?.name)
        }
    }

    @Test
    @EnabledOnJre(JRE.JAVA_11)
    fun `jmod parsing is working`() {
        val javaHome = System.getProperty("java.home") ?: throw IllegalArgumentException("java.home is not set")
        val jmod = Paths.get(javaHome, "jmods", "java.base.jmod").toFile()
        assertTrue(jmod.exists())
        val javaBase = JarFacade(java11Version.majorVersion) {
            JarFile(jmod)
        }
        assertTrue(javaBase.classes.all { !it.key.contains("META-INF") })
        assertTrue(javaBase.classes.contains("java.lang.String"))
        assertNotNull(javaBase.inputStreamOf("java.lang.String"))
    }
}