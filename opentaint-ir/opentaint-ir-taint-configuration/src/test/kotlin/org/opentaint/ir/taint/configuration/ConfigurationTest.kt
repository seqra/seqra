package org.opentaint.ir.taint.configuration

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.constructors
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.methods
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigurationTest : BaseTest() {
    companion object : WithDB()

    override val cp: JIRClasspath = runBlocking {
        val configPath = "/testJsonConfig.json"
        val testConfig = this::class.java.getResourceAsStream(configPath)
            ?: error("No such resource found: $configPath")
        val configJson = testConfig.bufferedReader().readText()
        val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
        val features = listOf(configurationFeature, UnknownClasses)
        db.classpath(allClasspath, features)
    }

    private val taintFeature = cp.taintConfigurationFeature()

    @Test
    fun testVirtualMethod() {
        val virtualParameter = JIRVirtualParameter(0, TypeNameImpl(cp.objectType.typeName))

        val method = JIRVirtualMethodImpl(
            name = "setValue",
            returnType = TypeNameImpl(cp.objectType.typeName),
            parameters = listOf(virtualParameter),
            description = ""
        )

        val clazz = JIRVirtualClassImpl(
            name = "com.service.model.SimpleRequest",
            initialFields = emptyList(),
            initialMethods = listOf(method)
        )
        clazz.bind(cp, VirtualLocation())

        method.bind(clazz)

        val configs = taintFeature.getConfigForMethod(method)
        val rule = configs.single() as TaintPassThrough

        assertEquals(ConstantTrue, rule.condition)
        assertEquals(2, rule.actionsAfter.size)
    }

    @Test
    fun testSinkMethod() {
        val method = cp.findClass<java.util.Properties>().methods.first { it.name == "store" }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testSourceMethod() {
        val method = cp.findClass<System>().methods.first { it.name == "getProperty" }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testCleanerMethod() {
        val method = cp.findClass<java.util.ArrayList<*>>().methods.first() { it.name == "clear" }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testParametersMatches() {
        val method = cp.findClass<java.lang.StringBuilder>().constructors.first {
            it.parameters.singleOrNull()?.type?.typeName == "java.lang.String"
        }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testPrimitiveParametersInMatcher() {
        val method = cp.findClass<java.io.Writer>().methods.first {
            it.name.startsWith("write") && it.parameters.firstOrNull()?.type?.typeName == "int"
        }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testIsTypeMatcher() {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        val method = cp.findClass<java.util.List<*>>().methods.single {
            it.name == "removeAll" && it.parameters.size == 1
        }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }
}
