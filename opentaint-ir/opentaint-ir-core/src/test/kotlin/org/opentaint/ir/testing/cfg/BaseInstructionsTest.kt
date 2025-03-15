package org.opentaint.ir.testing.cfg

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.NoClassInClasspathException
import org.opentaint.ir.api.cfg.applyAndGet
import org.opentaint.ir.api.ext.isKotlin
import org.opentaint.ir.api.ext.packageName
import org.opentaint.ir.impl.bytecode.JIRDatabaseClassWriter
import org.opentaint.ir.impl.cfg.MethodNodeBuilder
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

abstract class BaseInstructionsTest : BaseTest() {

    companion object : WithGlobalDB()

    private val target = Files.createTempDirectory("jIRdb-temp")

    val ext = runBlocking { cp.hierarchyExt() }

    fun runKotlinTest(className: String, muteGraphChecker: Boolean = false) {
        val clazz = cp.findClassOrNull(className)
        Assertions.assertNotNull(clazz)

        val javaClazz = testAndLoadClass(clazz!!, muteGraphChecker)
        val clazzInstance = javaClazz.constructors.first().newInstance()
        val method = javaClazz.methods.first { it.name == "box" }
        val res = method.invoke(clazzInstance)
        Assertions.assertEquals("OK", res)
    }

    protected fun testClass(klass: JIRClassOrInterface, validateLineNumbers: Boolean = true) {
        testAndLoadClass(klass, false, validateLineNumbers)
    }

    protected fun testAndLoadClass(klass: JIRClassOrInterface, muteGraphChecker: Boolean = false): Class<*> {
        return testAndLoadClass(klass, true, validateLineNumbers = true)!!
    }

    private fun testAndLoadClass(
        klass: JIRClassOrInterface,
        loadClass: Boolean,
        validateLineNumbers: Boolean,
        muteGraphChecker: Boolean = false
    ): Class<*>? {
        try {
            val classNode = klass.asmNode()
            classNode.methods = klass.declaredMethods.filter { it.enclosingClass == klass }.map {
                if (it.isAbstract || it.name.contains("$\$forInline")) {
                    it.asmNode()
                } else {
                    try {
                        val instructionList = it.rawInstList
                        it.instList.forEachIndexed { index, inst ->
                            Assertions.assertEquals(index, inst.location.index, "indexes not matched for $it at $index")
                        }
                        val graph = it.flowGraph()
                        if (!it.enclosingClass.isKotlin) {
                            val methodMsg = "$it should have line number"
                            if (validateLineNumbers) {
                                graph.instructions.forEach {
                                    Assertions.assertTrue(it.lineNumber > 0, methodMsg)
                                }
                            }
                        }
                        graph.applyAndGet(OverridesResolver(ext)) {}
                        if (!muteGraphChecker) JIRGraphChecker(it, graph).check()
                        val newBody = MethodNodeBuilder(it, instructionList).build()
                        newBody
                    } catch (e: Throwable) {
                        it.dumpInstructions()
                        throw IllegalStateException("error handling $it", e)
                    }

                }
            }
            val cw = JIRDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
            val checker = CheckClassAdapter(cw)
            classNode.accept(checker)
            val targetDir = target.resolve(klass.packageName.replace('.', '/'))
            val targetFile = targetDir.resolve("${klass.simpleName}.class").toFile().also {
                it.parentFile?.mkdirs()
            }
            targetFile.writeBytes(cw.toByteArray())
            if (loadClass) {

                val cp = listOf(target.toUri().toURL()) + System.getProperty("java.class.path")
                    .split(File.pathSeparatorChar)
                    .map { Paths.get(it).toUri().toURL() }
                val allClassLoader = URLClassLoader(cp.toTypedArray(), null)
                return allClassLoader.loadClass(klass.name)
            }
        } catch (e: NoClassInClasspathException) {
            System.err.println(e.localizedMessage)
        }
        return null
    }
}