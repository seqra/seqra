package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.methods
import org.opentaint.ir.impl.fs.asClassInfo
import org.opentaint.ir.impl.types.ParameterInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

open class ParameterNamesTest : BaseTest() {

    // NB! Cannot use WithDbImmutable here since new location is being loaded in test
    companion object : WithDb()

    private val target = Files.createTempDirectory("jIRdb-temp")

    @Test
    fun checkParameterName() {
        val clazz = cp.findClass("GenericsApi")
        runBlocking {
            cp.db.load(target.toFile())
        }
        val method = clazz.methods.firstOrNull { jIRMethod -> jIRMethod.name == "call" }
        Assertions.assertNotNull(method)
        Assertions.assertNull(method?.parameters?.get(0)?.name)
        Assertions.assertEquals("arg", method?.parameterNames?.get(0))
    }

    private val JIRMethod.parameterNames: List<String?>
        get() {
            return enclosingClass
                .withAsmNode { it.asClassInfo(enclosingClass.bytecode()) }
                .methods.find { info -> info.name == name && info.desc == description }
                ?.parametersInfo?.map(ParameterInfo::name)
                ?: parameters.map(JIRParameter::name)
        }
}

class ParameterNamesSQLiteTest : ParameterNamesTest() {
    companion object : WithSQLiteDb()
}