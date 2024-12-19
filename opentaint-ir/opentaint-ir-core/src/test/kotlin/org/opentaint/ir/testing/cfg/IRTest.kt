package org.opentaint.ir.testing.cfg

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JavaVersion
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.NoClassInClasspathException
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.DefaultJIRExprVisitor
import org.opentaint.ir.api.cfg.DefaultJIRInstVisitor
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIREnterMonitorInst
import org.opentaint.ir.api.cfg.JIRExitMonitorInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRGotoInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRSwitchInst
import org.opentaint.ir.api.cfg.JIRTerminatingInst
import org.opentaint.ir.api.cfg.JIRThrowInst
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.isAbstract
import org.opentaint.ir.api.ext.isAnnotation
import org.opentaint.ir.api.ext.isInterface
import org.opentaint.ir.api.ext.isKotlin
import org.opentaint.ir.api.ext.packageName
import org.opentaint.ir.api.ext.toType
import org.opentaint.ir.impl.JIRClasspathImpl
import org.opentaint.ir.impl.JIRDatabaseImpl
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.bytecode.JIRDatabaseClassWriter
import org.opentaint.ir.impl.bytecode.JIRMethodImpl
import org.opentaint.ir.impl.cfg.JIRBlockGraphImpl
import org.opentaint.ir.impl.cfg.JIRGraphBuilder
import org.opentaint.ir.impl.cfg.MethodNodeBuilder
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.cfg.Simplifier
import org.opentaint.ir.impl.cfg.applyAndGet
import org.opentaint.ir.impl.cfg.util.ExprMapper
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.fs.JarLocation
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.guavaLib
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files

class OverridesResolver(
    private val hierarchyExtension: HierarchyExtension
) : DefaultJIRInstVisitor<Sequence<JIRTypedMethod>>, DefaultJIRExprVisitor<Sequence<JIRTypedMethod>> {
    override val defaultInstHandler: (JIRInst) -> Sequence<JIRTypedMethod>
        get() = { emptySequence() }
    override val defaultExprHandler: (JIRExpr) -> Sequence<JIRTypedMethod>
        get() = { emptySequence() }

    private fun JIRClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JIRTypedMethod {
        return methods.firstOrNull { typedMethod ->
            val jIRMethod = typedMethod.method
            jIRMethod.name == name &&
                    jIRMethod.returnType.typeName == returnType.typeName &&
                    jIRMethod.parameters.map { param -> param.type.typeName } == argTypes.map { it.typeName }
        } ?: error("Could not find a method with correct signature")
    }

    private val JIRMethod.typedMethod: JIRTypedMethod
        get() {
            val klass = enclosingClass.toType()
            return klass.getMethod(name, parameters.map { it.type }, returnType)
        }

    override fun visitJIRAssignInst(inst: JIRAssignInst): Sequence<JIRTypedMethod> {
        if (inst.rhv is JIRCallExpr) return inst.rhv.accept(this)
        return emptySequence()
    }

    override fun visitJIRCallInst(inst: JIRCallInst): Sequence<JIRTypedMethod> {
        return inst.callExpr.accept(this)
    }

    override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): Sequence<JIRTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

    override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): Sequence<JIRTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

}

class JIRGraphChecker(val method: JIRMethod, val jIRGraph: JIRGraph) : JIRInstVisitor<Unit> {
    fun check() {
        try {
            jIRGraph.entry
        } catch (e: Exception) {
            println(
                "Fail on method ${method.enclosingClass.simpleName}#${method.name}(${
                    method.parameters.joinToString(
                        ","
                    ) { it.type.typeName }
                })"
            )
            throw e
        }
        assertTrue(jIRGraph.exits.all { it is JIRTerminatingInst })

        jIRGraph.forEach { it.accept(this) }

        checkBlocks()
    }

    fun checkBlocks() {
        val blockGraph = jIRGraph.blockGraph()

        val entry = assertDoesNotThrow { blockGraph.entry }
        for (block in blockGraph) {
            if (block != entry) {
                when (jIRGraph.inst(block.start)) {
                    is JIRCatchInst -> {
                        assertTrue(blockGraph.predecessors(block).isEmpty())
                        assertTrue(blockGraph.throwers(block).isNotEmpty())
                    }

                    else -> {
                        assertTrue(blockGraph.predecessors(block).isNotEmpty())
                        assertTrue(blockGraph.throwers(block).isEmpty())
                    }
                }
            }
            assertDoesNotThrow { blockGraph.instructions(block).map { jIRGraph.catchers(it) }.toSet().single() }
            if (jIRGraph.inst(block.end) !is JIRTerminatingInst) {
                assertTrue(blockGraph.successors(block).isNotEmpty())
            }
        }
    }

    override fun visitJIRAssignInst(inst: JIRAssignInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jIRGraph.next(inst)), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIREnterMonitorInst(inst: JIREnterMonitorInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jIRGraph.next(inst)), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRExitMonitorInst(inst: JIRExitMonitorInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jIRGraph.next(inst)), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRCallInst(inst: JIRCallInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jIRGraph.next(inst)), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRReturnInst(inst: JIRReturnInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(emptySet<JIRInst>(), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRThrowInst(inst: JIRThrowInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(emptySet<JIRInst>(), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRCatchInst(inst: JIRCatchInst) {
        assertEquals(emptySet<JIRInst>(), jIRGraph.predecessors(inst))
        assertTrue(jIRGraph.successors(inst).isNotEmpty())
        assertTrue(jIRGraph.throwers(inst).all { thrower ->
            inst in jIRGraph.catchers(thrower)
        })
    }

    override fun visitJIRGotoInst(inst: JIRGotoInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jIRGraph.inst(inst.target)), jIRGraph.successors(inst))
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRIfInst(inst: JIRIfInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(
            setOf(
                jIRGraph.inst(inst.trueBranch),
                jIRGraph.inst(inst.falseBranch)
            ),
            jIRGraph.successors(inst)
        )
        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

    override fun visitJIRSwitchInst(inst: JIRSwitchInst) {
        if (inst != jIRGraph.entry) {
            assertTrue(jIRGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(
            inst.branches.values.map { jIRGraph.inst(it) }.toSet() + jIRGraph.inst(inst.default),
            jIRGraph.successors(inst)
        )

        assertTrue(jIRGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jIRGraph.inst(thrower) }.toSet()
        })
        assertTrue(jIRGraph.throwers(inst).isEmpty())
    }

}

class IRTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy)

    private val target = Files.createTempDirectory("jIRdb-temp")

    private val ext = runBlocking { cp.hierarchyExt() }

    @Test
    fun `get ir of simple method`() {
        testClass(cp.findClass<IRExamples>())
    }

    @Test
    fun `arrays methods`() {
        testClass(cp.findClass<JavaArrays>())
    }

    @Test
    fun `get ir of algorithms lesson 1`() {
        testClass(cp.findClass<JavaTasks>())
    }

    @Test
    fun `get ir of binary search tree`() {
        testClass(cp.findClass<BinarySearchTree<*>>())
        testClass(cp.findClass<BinarySearchTree<*>.BinarySearchTreeIterator>())
    }

    @Test
    fun `get ir of self`() {
        testClass(cp.findClass<JIRClasspathImpl>())
        testClass(cp.findClass<JIRClassOrInterfaceImpl>())
        testClass(cp.findClass<JIRMethodImpl>())
        testClass(cp.findClass<RawInstListBuilder>())
        testClass(cp.findClass<Simplifier>())
        testClass(cp.findClass<JIRDatabaseImpl>())
        testClass(cp.findClass<ExprMapper>())
        testClass(cp.findClass<JIRGraphBuilder>())
        testClass(cp.findClass<JIRBlockGraphImpl>())
    }

    @Test
    fun `get ir of jackson`() {
        allClasspath
            .filter { it.name.contains("jackson") }
            .forEach { runAlongLib(it) }
    }

    @Test
    fun `get ir of guava`() {
        runAlongLib(guavaLib)
    }

    private fun runAlongLib(file: File) {
        println("Run along: ${file.absolutePath}")

        val classes = JarLocation(file, isRuntime = false, object : JavaVersion {
            override val majorVersion: Int
                get() = 8
        }).classes
        assertNotNull(classes)
        classes!!.forEach {
            val clazz = cp.findClass(it.key)
            if (!clazz.isAnnotation && !clazz.isInterface) {
                println("Testing class: ${it.key}")
                testClass(clazz)
            }
        }
    }

    private fun testClass(klass: JIRClassOrInterface) = try {
        val classNode = klass.bytecode()
        classNode.methods = klass.declaredMethods.filter { it.enclosingClass == klass }.map {
            if (it.isAbstract) {
                it.body()
            } else {
//            val oldBody = it.body()
//            println()
//            println("Old body: ${oldBody.print()}")
                val instructionList = it.rawInstList

//            println("Instruction list: $instructionList")
                val graph = it.flowGraph()
                if (!it.enclosingClass.isKotlin) {
                    graph.instructions.forEach {
                        assertTrue(it.lineNumber > 0, "$it should have line number")
                    }
                }
                graph.applyAndGet(OverridesResolver(ext)) {}
                JIRGraphChecker(it, graph).check()
//            println("Graph: $graph")
//            graph.view("/usr/bin/dot", "/usr/bin/firefox", false)
//            graph.blockGraph().view("/usr/bin/dot", "/usr/bin/firefox")
                val newBody = MethodNodeBuilder(it, instructionList).build()
//            println("New body: ${newBody.print()}")
//            println()
                newBody
            }
        }
        val cw = JIRDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(classNode)
        classNode.accept(checker)
        val targetDir = target.resolve(klass.packageName.replace('.', '/'))
        val targetFile = targetDir.resolve("${klass.simpleName}.class").toFile().also {
            it.parentFile?.mkdirs()
        }
        targetFile.writeBytes(cw.toByteArray())

        val classloader = URLClassLoader(arrayOf(target.toUri().toURL()))
        classloader.loadClass(klass.name)
    } catch (e: NoClassInClasspathException) {
        System.err.println(e.localizedMessage)
    }
}
