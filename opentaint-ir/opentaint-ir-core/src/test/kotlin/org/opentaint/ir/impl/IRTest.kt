
package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.NoClassInClasspathException
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.DefaultJcExprVisitor
import org.opentaint.ir.api.cfg.DefaultJcInstVisitor
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRBlockGraph
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIREnterMonitorInst
import org.opentaint.ir.api.cfg.JIRExitMonitorInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRGotoInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRGraphBuilder
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRSwitchInst
import org.opentaint.ir.api.cfg.JIRTerminatingInst
import org.opentaint.ir.api.cfg.JIRThrowInst
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.cfg.ext.applyAndGet
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.methods
import org.opentaint.ir.api.packageName
import org.opentaint.ir.api.toType
import org.opentaint.ir.impl.bytecode.JIRDBClassWriter
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.bytecode.JIRMethodImpl
import org.opentaint.ir.impl.cfg.BinarySearchTree
import org.opentaint.ir.impl.cfg.IRExamples
import org.opentaint.ir.impl.cfg.JavaTasks
import org.opentaint.ir.impl.cfg.MethodNodeBuilder
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.cfg.Simplifier
import org.opentaint.ir.impl.cfg.util.ExprMapper
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.hierarchyExt
import java.net.URLClassLoader
import java.nio.file.Files

class OverridesResolver(
    val hierarchyExtension: HierarchyExtension
) : DefaultJcInstVisitor<Sequence<JIRTypedMethod>>, DefaultJcExprVisitor<Sequence<JIRTypedMethod>> {
    override val defaultInstHandler: (JIRInst) -> Sequence<JIRTypedMethod>
        get() = { emptySequence() }
    override val defaultExprHandler: (JIRExpr) -> Sequence<JIRTypedMethod>
        get() = { emptySequence() }

    private fun JIRClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JIRTypedMethod {
        return methods.firstOrNull { typedMethod ->
            val jirMethod = typedMethod.method
            jirMethod.name == name &&
                    jirMethod.returnType.typeName == returnType.typeName &&
                    jirMethod.parameters.map { param -> param.type.typeName } == argTypes.map { it.typeName }
        } ?: error("Could not find a method with correct signature")
    }

    private val JIRMethod.typedMethod: JIRTypedMethod
        get() {
            val klass = enclosingClass.toType()
            return klass.getMethod(name, parameters.map { it.type }, returnType)
        }

    override fun visitJcAssignInst(inst: JIRAssignInst): Sequence<JIRTypedMethod> {
        if (inst.rhv is JIRCallExpr) return inst.rhv.accept(this)
        return emptySequence()
    }

    override fun visitJcCallInst(inst: JIRCallInst): Sequence<JIRTypedMethod> {
        return inst.callExpr.accept(this)
    }

    override fun visitJcVirtualCallExpr(expr: JIRVirtualCallExpr): Sequence<JIRTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

    override fun visitJcSpecialCallExpr(expr: JIRSpecialCallExpr): Sequence<JIRTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

}

class JIRGraphChecker(val jirGraph: JIRGraph) : JIRInstVisitor<Unit> {
    fun check() {
        assertDoesNotThrow { jirGraph.entry }
        assertTrue(jirGraph.exits.all { it is JIRTerminatingInst })

        jirGraph.forEach { it.accept(this) }

        checkBlocks()
    }

    fun checkBlocks() {
        val blockGraph = jirGraph.blockGraph()

        val entry = assertDoesNotThrow { blockGraph.entry }
        for (block in blockGraph) {
            if (block != entry) {
                when (jirGraph.inst(block.start)) {
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
            assertDoesNotThrow { blockGraph.instructions(block).map { jirGraph.catchers(it) }.toSet().single() }
            if (jirGraph.inst(block.end) !is JIRTerminatingInst) {
                assertTrue(blockGraph.successors(block).isNotEmpty())
            }
        }
    }

    override fun visitJcAssignInst(inst: JIRAssignInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jirGraph.next(inst)), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcEnterMonitorInst(inst: JIREnterMonitorInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jirGraph.next(inst)), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcExitMonitorInst(inst: JIRExitMonitorInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jirGraph.next(inst)), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcCallInst(inst: JIRCallInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jirGraph.next(inst)), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcReturnInst(inst: JIRReturnInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(emptySet<JIRInst>(), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcThrowInst(inst: JIRThrowInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(emptySet<JIRInst>(), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcCatchInst(inst: JIRCatchInst) {
        assertEquals(emptySet<JIRInst>(), jirGraph.predecessors(inst))
        assertTrue(jirGraph.successors(inst).isNotEmpty())
        assertTrue(jirGraph.throwers(inst).all { thrower ->
            inst in jirGraph.catchers(thrower)
        })
    }

    override fun visitJcGotoInst(inst: JIRGotoInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jirGraph.inst(inst.target)), jirGraph.successors(inst))
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcIfInst(inst: JIRIfInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(
            setOf(
                jirGraph.inst(inst.trueBranch),
                jirGraph.inst(inst.falseBranch)
            ),
            jirGraph.successors(inst)
        )
        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

    override fun visitJcSwitchInst(inst: JIRSwitchInst) {
        if (inst != jirGraph.entry) {
            assertTrue(jirGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(
            inst.branches.values.map { jirGraph.inst(it) }.toSet() + jirGraph.inst(inst.default),
            jirGraph.successors(inst)
        )

        assertTrue(jirGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jirGraph.inst(thrower) }.toSet()
        })
        assertTrue(jirGraph.throwers(inst).isEmpty())
    }

}

class IRTest : BaseTest() {
    val target = Files.createTempDirectory("jirdb-temp")

    companion object : WithDB(InMemoryHierarchy)

    private val ext = runBlocking { cp.hierarchyExt() }

    @Test
    fun `get ir of simple method`() {
        testClass(cp.findClass<IRExamples>())
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
        testClass(cp.findClass<JIRDBImpl>())
        testClass(cp.findClass<ExprMapper>())
        testClass(cp.findClass<JIRGraphBuilder>())
        testClass(cp.findClass<JIRBlockGraph>())
    }

    private fun testClass(klass: JIRClassOrInterface) = try {
        val classNode = klass.bytecode()
        classNode.methods = klass.methods.filter { it.enclosingClass == klass }.map {
//            val oldBody = it.body()
//            println()
//            println("Old body: ${oldBody.print()}")
            val instructionList = it.instructionList(cp)
//            println("Instruction list: $instructionList")
            val graph = instructionList.graph(cp, it)
            graph.applyAndGet(OverridesResolver(ext)) {}
            JIRGraphChecker(graph).check()
//            println("Graph: $graph")
//            graph.view("/usr/bin/dot", "/usr/bin/firefox", false)
//            graph.blockGraph().view("/usr/bin/dot", "/usr/bin/firefox")
            val newBody = MethodNodeBuilder(it, instructionList).build()
//            println("New body: ${newBody.print()}")
//            println()
            newBody
        }
        val cw = JIRDBClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
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
