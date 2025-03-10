package org.opentaint.ir.testing.cfg

import com.sun.mail.imap.IMAPMessage
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassProcessingTask
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.cfg.*
import org.opentaint.ir.api.ext.boolean
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.api.ext.cfg.locals
import org.opentaint.ir.api.ext.cfg.values
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.humanReadableSignature
import org.opentaint.ir.api.ext.int
import org.opentaint.ir.testing.Common
import org.opentaint.ir.testing.Common.CommonClass
import org.opentaint.ir.testing.cfg.RealMethodResolution.Virtual
import org.opentaint.ir.testing.cfg.RealMethodResolution.VirtualImpl
import org.opentaint.ir.testing.hierarchies.Inheritance
import org.opentaint.ir.testing.primitives.Primitives
import org.opentaint.ir.testing.structure.FieldsAndMethods
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.activation.DataHandler

class InstructionsTest : BaseInstructionsTest() {

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
    fun `cmp insts`() {
        val clazz = cp.findClass<Conditionals>()
        val method = clazz.declaredMethods.first { it.name == "main" }
        val instructions = method.instList.instructions
        val cmpExprs = instructions.filterIsInstance<JIRIfInst>().map { it.condition }
        assertEquals(4, cmpExprs.size)

        val geZero = cmpExprs[0] as JIRGeExpr
        assertEquals(0 to 0, (geZero.lhv as JIRArgument).index to (geZero.rhv as JIRInt).value)

        val gtZero = cmpExprs[1] as JIRGtExpr
        assertEquals(0 to 0, (gtZero.lhv as JIRArgument).index to (gtZero.rhv as JIRInt).value)

        val geOther = cmpExprs[2] as JIRGeExpr
        assertEquals(0 to 1, (geOther.lhv as JIRArgument).index to (geOther.rhv as JIRArgument).index)

        val gtOther = cmpExprs[3] as JIRGtExpr
        assertEquals(0 to 1, (gtOther.lhv as JIRArgument).index to (gtOther.rhv as JIRArgument).index)
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
            assertEquals(13, instList.values.size)
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
    @EnabledOnJre(JRE.JAVA_8)
    fun `java 5 bytecode processed correctly on java 8`() {
        runAlong("mail-1.4.7.jar", "joda-time-2.12.5.jar")
    }

    @Test
    @DisabledOnJre(JRE.JAVA_8)
    fun `java 5 bytecode processed correctly on java 9+`() {
        runAlong("mail-1.4.7.jar", "activation-1.1.jar", "joda-time-2.12.5.jar")
    }

    @Test
    fun `constant ldc instructions`() {
        val clazz = cp.findClass("TestLDC")
        clazz.declaredMethods.forEach {
            it.flowGraph()
        }
    }

    private fun runAlong(vararg patters: String) {
        val jars = cp.registeredLocations.map { it.path }
            .filter { patters.any { pattern -> it.contains(pattern) } }
        assertEquals(patters.size, jars.size)
        val list = ConcurrentHashMap.newKeySet<JIRClassOrInterface>()
        runBlocking {
            val pureClasspath = cp.db.classpath(jars.map { File(it) })
            pureClasspath.execute(object : JIRClassProcessingTask {
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
                    KLogging().logger.error(e) { "can't process $it" }
                    failed.add(it)
                }
            }
        }
        assertTrue(
            failed.isEmpty(),
            "Failed to process methods: \n${failed.joinToString("\n") { it.enclosingClass.name + "#" + it.name }}"
        )
    }

    @Test
    fun `resolving primitive types for local variables should work`() {
        val clazz = cp.findClass<Primitives>()
        clazz.declaredMethods.filter { !it.isConstructor }.forEach {
            val returnValue = (it.instList.last() as JIRReturnInst).returnValue
            assertNotNull(returnValue!!)
            val expected = cp.findTypeOrNull(it.returnType.typeName)
            assertEquals(expected, returnValue.type, "types not matched for ${it.humanReadableSignature}")
        }
    }

    @Test
    fun `method resolution based on var`() {
        val clazz = cp.findClass<RealMethodResolution>()
        val insts = clazz.declaredMethods.first { it.name == "test" }.instList
        val actionCallExpr = insts.instructions.firstNotNullOf {
            (it as? JIRCallInst)?.callExpr.takeIf { it is JIRVirtualCallExpr }
        } as JIRVirtualCallExpr
        assertEquals(cp.findClass<VirtualImpl>(), actionCallExpr.method.method.enclosingClass)
        assertEquals(cp.findClass<Virtual>(), actionCallExpr.declaredMethod.method.enclosingClass)
    }

    @Test
    fun `resolving field descriptors should work`() {
        val parent = cp.findClass<Common.Common1>()
        val child = cp.findClass<FieldsAndMethods.Common1Child>()

        // public int field
        val methodWithPublicFieldInt = child.declaredMethods.first { it.name == "accessIntField" }
        val assignInstInt = methodWithPublicFieldInt.instList
            .filterIsInstance<JIRAssignInst>()
            .single()
        val fieldInt = (assignInstInt.rhv as JIRFieldRef).field

        assertEquals(parent, fieldInt.enclosingType.jIRClass)
        assertEquals(cp.int, fieldInt.fieldType)

        // public boolean field
        val methodWithPublicFieldBoolean = child.declaredMethods.first { it.name == "accessBooleanField" }
        val assignInstBoolean = methodWithPublicFieldBoolean.instList
            .filterIsInstance<JIRAssignInst>()
            .single()
        val fieldBoolean = (assignInstBoolean.rhv as JIRFieldRef).field

        assertEquals(child, fieldBoolean.enclosingType.jIRClass)
        assertEquals(cp.boolean, fieldBoolean.fieldType)
    }

    @Test
    fun `private call with invokevirtual instruction`() {
        val clazz = cp.findClass("VirtualInstructions")
        val instList = clazz.declaredMethods.first { it.name == "run" }.instList
        val callDoSmth = instList.mapNotNull { it.callExpr }.first {
            it.toString().contains("doSmth")
        }
        assertEquals("doSmth", callDoSmth.method.method.name)
    }

    @Test
    fun `call default method should be resolved`() {
        val clazz = cp.findClass<CommonClass>()
        val instList = clazz.declaredMethods.first { it.name == "run" }.instList
        val callDefaultMethod = instList.mapNotNull { it.callExpr }.first {
            it.toString().contains("defaultMethod")
        }
        assertEquals("defaultMethod", callDefaultMethod.method.method.name)
    }

    @Test
    fun `condition in for should work`() {
        val clazz = cp.findClass<Conditionals>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "conditionInFor" }
        val res = method.invoke(null)
        assertNull(res)
    }

    @Test
    fun `lambda test`() {
        val clazz = cp.findClass<Lambdas>()
        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "lambdaTest" }
        val res = method.invoke(null)
        assertNull(res)
    }

    @Test
    fun `hierarchy test`() {
        val clazz = cp.findClass<Inheritance>()
        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "test" }
        val res = method.invoke(null, null)
        assertNull(res)
    }

    @Test
    fun `instance method ref bug`() {
        val clazz = cp.findClass<Close>()
        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "test" }
        val res = method.invoke(null)
        assertNull(res)
    }

    @Test
    fun `big decimal test`() {
        val clazz = cp.findClass<MultiplyTests>()
        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "test" }
        val res = method.invoke(null)
        assertNull(res)
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