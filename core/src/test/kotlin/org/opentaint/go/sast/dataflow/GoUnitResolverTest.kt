package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.ir.go.api.GoIRModelFunction
import org.opentaint.ir.go.api.GoIRApproximatedFunction
import org.opentaint.ir.go.api.GoIRConst
import org.opentaint.ir.go.api.GoIRFreeVar
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRGlobal
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRPackage
import org.opentaint.ir.go.api.GoIRParameter
import org.opentaint.ir.go.api.GoIRTypeParamDecl
import org.opentaint.ir.go.api.GoIrFunctionReference
import kotlin.test.assertEquals

class GoUnitResolverTest {
    @Test
    fun `project package resolves to GoPackageUnit`() {
        val resolver = GoUnitResolver()
        val fn = fakeFunction(fakePackage("example.com/main", isStdlib = false, isDependency = false))
        assertEquals(GoUnitResolver.GoPackageUnit("example.com/main"), resolver.resolve(fn))
    }

    @Test
    fun `stdlib package resolves to UnknownUnit`() {
        val resolver = GoUnitResolver()
        val fn = fakeFunction(fakePackage("fmt", isStdlib = true, isDependency = false))
        assertEquals(UnknownUnit, resolver.resolve(fn))
    }

    @Test
    fun `dependency package resolves to UnknownUnit`() {
        val resolver = GoUnitResolver()
        val fn = fakeFunction(fakePackage("github.com/foo/bar", isStdlib = false, isDependency = true))
        assertEquals(UnknownUnit, resolver.resolve(fn))
    }

    @Test
    fun `null package resolves to UnknownUnit`() {
        val resolver = GoUnitResolver()
        val fn = fakeFunction(pkg = null)
        assertEquals(UnknownUnit, resolver.resolve(fn))
    }

    @Test
    fun `typed models are analyzed in dependency and standard library packages`() {
        val resolver = GoUnitResolver()
        val dependency = fakeFunction(fakePackage("example.com/lib", isStdlib = false, isDependency = true))
        val helper = object : GoIRModelFunction, GoIRFunction by dependency {}
        assertEquals(GoUnitResolver.GoPackageUnit("example.com/lib"), resolver.resolve(helper))
        val stdlib = fakeFunction(fakePackage("strings", isStdlib = true, isDependency = false))
        val approximation = object : GoIRApproximatedFunction, GoIRFunction by stdlib {
            override val original = stdlib
        }
        assertEquals(GoUnitResolver.GoPackageUnit("strings"), resolver.resolve(approximation))
    }

    private fun fakePackage(importPath: String, isStdlib: Boolean, isDependency: Boolean): GoIRPackage =
        object : GoIRPackage {
            override val importPath = importPath
            override val name = importPath.substringAfterLast('/')
            override val isStdlib = isStdlib
            override val isDependency = isDependency
            override val functions = emptyList<GoIRFunction>()
            override val namedTypes = emptyList<GoIRNamedType>()
            override val globals = emptyList<GoIRGlobal>()
            override val constants = emptyList<GoIRConst>()
            override val imports = emptyList<GoIRPackage>()
            override val initFunction: GoIRFunction? = null
            override fun findNamedType(name: String) = null
            override fun findGlobal(name: String) = null
            override fun findConstant(name: String) = null
            override fun allMethods() = emptyList<GoIRFunction>()
        }

    private fun fakeFunction(pkg: GoIRPackage?): GoIRFunction {
        val resolvedPkg = pkg
        return object : GoIRFunction {
            override val name = "fn"
            override val fullName = "${pkg?.importPath ?: "?"}.fn"
            override val pkg = resolvedPkg
            override val signature get() = error("not used")
            override val params = emptyList<GoIRParameter>()
            override val freeVars = emptyList<GoIRFreeVar>()
            override val position = null
            override val isMethod = false
            override val isPointerReceiver = false
            override val isExported = true
            override val isSynthetic = false
            override val syntheticKind: String? = null
            override val body = null
            override val parent = null
            override val anonymousFunctions = emptyList<GoIrFunctionReference>()
            override val typeParams = emptyList<GoIRTypeParamDecl>()
        }
    }
}
