package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension

/**
 * Tests for channels, goroutines, defer, select.
 */
@ExtendWith(GoIRTestExtension::class)
class ChannelGoroutineTests {

    @Test
    fun `make channel generates MakeChan`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func makeCh() chan int {
                return make(chan int, 10)
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("makeCh")!!
        val makeChans = fn.findExpressions<GoIRMakeChanExpr>()
        assertThat(makeChans).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `send to channel generates Send`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sendVal(ch chan int, v int) {
                ch <- v
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sendVal")!!
        val sends = fn.findInstructions<GoIRSend>()
        assertThat(sends).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `receive from channel generates UnOp ARROW`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func recvVal(ch chan int) int {
                return <-ch
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("recvVal")!!
        val unops = fn.findExpressions<GoIRUnOpExpr>()
        val recvs = unops.filter {
            it.op == org.opentaint.ir.go.type.GoIRUnaryOp.ARROW
        }
        assertThat(recvs).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `goroutine generates Go instruction`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func launch() {
                go func() {}()
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("launch")!!
        val gos = fn.findInstructions<GoIRGo>()
        assertThat(gos).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `defer generates Defer and RunDefers`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func withDefer() {
                defer func() {}()
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("withDefer")!!
        val defers = fn.findInstructions<GoIRDefer>()
        assertThat(defers).isNotEmpty()

        val runDefers = fn.findInstructions<GoIRRunDefers>()
        assertThat(runDefers).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `select generates Select instruction`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func selectCh(a, b chan int) int {
                select {
                case v := <-a:
                    return v
                case v := <-b:
                    return v
                }
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("selectCh")!!
        val selects = fn.findExpressions<GoIRSelectExpr>()
        assertThat(selects).isNotEmpty()
        assertThat(selects[0].states).hasSizeGreaterThanOrEqualTo(2)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
