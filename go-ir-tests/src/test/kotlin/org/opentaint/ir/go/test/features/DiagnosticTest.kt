package org.opentaint.ir.go.test.features

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension

/**
 * Diagnostic test to understand naming conventions and IR structure.
 */
@ExtendWith(GoIRTestExtension::class)
class DiagnosticTest {

    @Test
    fun `dump program structure`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Point struct { X, Y int }
            type Counter struct { count int }
            func (c *Counter) Increment() { c.count++ }
            func (c Counter) Value() int { return c.count }
            func hello() int { return 42 }
            func add(a, b int) int { return a + b }
        """.trimIndent())

        println("=== PACKAGES ===")
        for ((path, pkg) in prog.packages) {
            println("Package: name='${pkg.name}' importPath='$path'")
            println("  Functions:")
            for (fn in pkg.functions) {
                println("    name='${fn.name}' fullName='${fn.fullName}' isMethod=${fn.isMethod} hasBody=${fn.hasBody}")
                if (fn.hasBody) {
                    val body = fn.body!!
                    println("      blocks=${body.blocks.size} instructions=${body.instructionCount}")
                    for (inst in body.instructions) {
                        println("      [${inst.index}] ${inst::class.simpleName}")
                    }
                }
            }
            println("  Named Types:")
            for (nt in pkg.namedTypes) {
                println("    name='${nt.name}' fullName='${nt.fullName}' kind=${nt.kind}")
                println("      fields: ${nt.fields.map { "${it.name}:${it.type.displayName}" }}")
                println("      methods: ${nt.methods.map { it.fullName }}")
                println("      pointerMethods: ${nt.pointerMethods.map { it.fullName }}")
                println("      interfaceMethods: ${nt.interfaceMethods.map { it.name }}")
            }
            println("  All Methods:")
            for (m in pkg.allMethods()) {
                println("    name='${m.name}' fullName='${m.fullName}' isMethod=${m.isMethod} receiverType=${m.receiverType?.fullName}")
            }
            println("  Globals:")
            for (g in pkg.globals) {
                println("    name='${g.name}' fullName='${g.fullName}'")
            }
        }

        println("=== ALL FUNCTIONS ===")
        for (fn in prog.allFunctions()) {
            println("  fullName='${fn.fullName}'")
        }

        println("=== ALL NAMED TYPES ===")
        for (nt in prog.allNamedTypes()) {
            println("  fullName='${nt.fullName}'")
        }
    }
}
