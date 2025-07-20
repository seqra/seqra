package org.opentaint.ir.testing.types

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.ext.cfg.fieldRef
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.bytecode.JIRDatabaseClassWriter
import org.opentaint.ir.impl.types.substition.IgnoreSubstitutionProblems
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.WithRAMDB
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.nio.file.Files

open class IgnoreSubstitutionProblemsTest : BaseTest() {

    companion object : WithDB(IgnoreSubstitutionProblems)

    private val target = Files.createTempDirectory("jIRdb-temp")

    @Test
    fun `should work when params number miss match`() {
        val modifiedType = tweakClass {
            signature = "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/lang/Object;"
        }.toType()
        modifiedType.methods.forEach {
            it.parameters
            it.typeParameters
            it.returnType
            it.method.instList.forEach {
                it.fieldRef?.field?.type
            }
        }
    }

    private fun tweakClass(action: ClassNode.() -> Unit): JIRClassOrInterface {
        cp.findClass("GenericsApi").tweakClass(action)
        cp.findClass("GenericsApiConsumer").tweakClass()
        runBlocking {
            cp.db.load(target.toFile())
        }
        return runBlocking { db.classpath(listOf(target.toFile()), listOf(IgnoreSubstitutionProblems)).findClass("GenericsApiConsumer") }
    }

    private fun JIRClassOrInterface.tweakClass(action: ClassNode.() -> Unit = {}): Unit = withAsmNode { classNode ->
        classNode.action()
        val cw = JIRDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(cw)
        classNode.accept(checker)
        val targetDir = target.resolve(packageName.replace('.', '/'))
        val targetFile = targetDir.resolve("${simpleName}.class").toFile().also {
            it.parentFile?.mkdirs()
        }
        targetFile.writeBytes(cw.toByteArray())
    }
}

class IgnoreSubstitutionProblemsRAMTest : IgnoreSubstitutionProblemsTest() {
    companion object : WithRAMDB(IgnoreSubstitutionProblems)
}
