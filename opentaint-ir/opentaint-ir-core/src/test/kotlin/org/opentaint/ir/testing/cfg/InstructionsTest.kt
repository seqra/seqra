package org.opentaint.ir.testing.cfg

import com.sun.mail.imap.IMAPMessage
import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassProcessingTask
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.api.ext.cfg.locals
import org.opentaint.ir.api.ext.cfg.values
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.util.concurrent.ConcurrentHashMap
import javax.activation.DataHandler

class InstructionsTest : BaseTest() {

    companion object : WithDB()

    @Test
    fun `assign inst`() {
        val clazz = cp.findClass<SimpleAlias1>()
        val method = clazz.declaredMethods.first { it.name == "main" }
        val bench = cp.findClass<Benchmark>()
        val use = bench.declaredMethods.first { it.name == "use" }
        val instructions = method.instList.instructions
        val firstUse = instructions.indexOfFirst { it.callExpr?.method?.method == use }
        val assign = instructions[firstUse + 1] as JIRAssignInst
        assertEquals("%4", (assign.lhv as JIRLocalVar).name)
        assertEquals("%1", (assign.rhv as JIRLocalVar).name)
    }

    @Test
    fun `null ref test`() {
        val clazz = cp.findClass<DataHandler>()
        clazz.declaredMethods.first { it.name == "writeTo" }.flowGraph()
    }

    @Test
    fun `Protocol test`() {
        val clazz = cp.findClass("com.sun.mail.pop3.Protocol")
        val method = clazz.declaredMethods.first { it.name == "<init>" }
        method.flowGraph()
    }

    @Test
    fun `SMTPSaslAuthenticator test`() {
        val clazz = cp.findClass("com.sun.mail.smtp.SMTPSaslAuthenticator")
        val method = clazz.declaredMethods.first { it.name == "authenticate" }
        method.flowGraph()
    }

    @Test
    fun `ref undefined`() {
        val clazz = cp.findClass("com.sun.mail.smtp.SMTPTransport\$DigestMD5Authenticator")
        clazz.declaredMethods.forEach { it.flowGraph() }
    }

    @Test
    fun `properly merged frames for old bytecode`() {
        val clazz = cp.findClass<IMAPMessage>()
        val method = clazz.declaredMethods.first { it.name == "writeTo" }
        method.flowGraph()
    }

    @Test
    @EnabledOnJre(JRE.JAVA_11)
    fun `locals should work`() {
        val clazz = cp.findClass<IRExamples>()
        with(clazz.declaredMethods.first { it.name == "sortTimes" }) {
            assertEquals(9, instList.locals.size)
            assertEquals(13, instList.values .size)
        }

        with(clazz.declaredMethods.first { it.name == "test" }) {
            assertEquals(2, instList.locals.size)
            assertEquals(5, instList.values.size)
        }
        with(clazz.declaredMethods.first { it.name == "concatTest" }) {
            assertEquals(6, instList.locals.size)
            assertEquals(6, instList.values.size)
        }
        with(clazz.declaredMethods.first { it.name == "testArrays" }) {
            assertEquals(4, instList.locals.size)
            assertEquals(8, instList.values.size)
        }
    }

    @Test
    fun `java 5 bytecode processed correctly`() {
        val jars = cp.registeredLocations.map { it.path }
            .filter { it.contains("mail-1.4.7.jar") || it.contains("activation-1.1.jar") || it.contains("joda-time-2.12.5.jar") }
        assertEquals(3, jars.size)
        val list = ConcurrentHashMap.newKeySet<JIRClassOrInterface>()
        runBlocking {
            cp.execute(object : JIRClassProcessingTask {
                override fun shouldProcess(registeredLocation: RegisteredLocation): Boolean {
                    return !registeredLocation.isRuntime && jars.contains(registeredLocation.path)
                }

                override fun process(clazz: JIRClassOrInterface) {
                    list.add(clazz)
                }
            })
        }
        val failed = ConcurrentHashMap.newKeySet<JIRMethod>()
        list.parallelStream().forEach { clazz ->
            clazz.declaredMethods.forEach {
                try {
                    it.flowGraph()
                } catch (e: Exception) {
                    failed.add(it)
                }
            }
        }
        assertTrue(
            failed.isEmpty(),
            "Failed to process methods: \n${failed.joinToString("\n") { it.enclosingClass.name + "#" + it.name }}"
        )
    }

}

fun JIRMethod.dumpInstructions(): String {
    return buildString {
        val textifier = Textifier()
        asmNode().accept(TraceMethodVisitor(textifier))
        textifier.text.printList(this)
    }
}

private fun List<*>.printList(builder: StringBuilder) {
    forEach {
        if (it is List<*>) {
            it.printList(builder)
        } else {
            builder.append(it.toString())
        }
    }
}
