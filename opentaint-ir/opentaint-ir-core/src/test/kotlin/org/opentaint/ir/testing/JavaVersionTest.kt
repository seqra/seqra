package org.opentaint.ir.testing

import org.opentaint.ir.api.jvm.JIRSettings
import org.opentaint.ir.impl.fs.JavaRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE

class JavaVersionTest {

    @Test
    @EnabledOnJre(JRE.JAVA_11)
    fun `java version should be proper for 11 java`() {
        assertEquals(11, JavaRuntime(JIRSettings().useProcessJavaRuntime().jre).version.majorVersion)
    }
    @Test
    @EnabledOnJre(JRE.JAVA_8)
    fun `java version should be proper for 8 java`() {
        assertEquals(8, JavaRuntime(JIRSettings().useProcessJavaRuntime().jre).version.majorVersion)
    }

}