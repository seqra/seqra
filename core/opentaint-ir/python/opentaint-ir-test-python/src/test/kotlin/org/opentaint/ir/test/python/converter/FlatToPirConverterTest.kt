package org.opentaint.ir.test.python.converter

import org.junit.jupiter.api.Test
import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.PIRModuleImpl
import org.opentaint.ir.impl.python.builder.*
import org.opentaint.ir.impl.python.converter.FlatToPirConverter
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FlatToPirConverterTest {

    private val stubClasspath = object : PIRClasspath {
        override val modules = emptyList<PIRModule>()
        override fun findModuleOrNull(name: String): PIRModule? = null
        override fun findClassOrNull(qualifiedName: String): PIRClass? = null
        override fun findFunctionOrNull(qualifiedName: String): PIRFunction? = null
        override val pythonVersion = "3.11"
        override val mypyVersion = "1.0"
        override fun close() {}
    }

    @Test
    fun `minimal module round-trip`() {
        val flat = FlatModuleIR(
            moduleName = "m",
            path = "m.py",
            functions = listOf(
                FlatFunctionIR(
                    name = "foo",
                    qualifiedName = "m.foo",
                    parentQualifiedName = null,
                    kind = FlatFunctionKind.TOP_LEVEL,
                    cfg = FlatCFG.EMPTY,
                    parameters = listOf(FlatParameter("x", FlatAnyType, FlatParamKind.POSITIONAL_OR_KEYWORD, false, null)),
                    returnType = FlatAnyType,
                    isAsync = false,
                    isGenerator = false,
                    closureVars = emptyList(),
                    decorators = emptyList(),
                ),
                FlatFunctionIR(
                    name = "__module_init__",
                    qualifiedName = "m.__module_init__",
                    parentQualifiedName = null,
                    kind = FlatFunctionKind.MODULE_INIT,
                    cfg = FlatCFG.EMPTY,
                    parameters = emptyList(),
                    returnType = FlatAnyType,
                    isAsync = false,
                    isGenerator = false,
                    closureVars = emptyList(),
                    decorators = emptyList(),
                ),
            ),
            classes = emptyList(),
            fields = listOf(FlatModuleField("x", FlatClassType("builtins.int"), true)),
            imports = listOf("os"),
            diagnostics = emptyList(),
        )

        val module = FlatToPirConverter(flat, stubClasspath).convert()

        assertEquals("m", module.name)
        assertEquals("m.py", module.path)
        assertEquals(1, module.functions.size)
        assertEquals("foo", module.functions[0].name)
        assertEquals("__module_init__", module.moduleInit.name)
        assertEquals(1, module.fields.size)
        assertEquals("x", module.fields[0].name)
        assertEquals(listOf("os"), module.imports)
        assertSame(stubClasspath, module.classpath)
    }

    @Test
    fun `class with properties and enclosingClass`() {
        val flat = FlatModuleIR(
            moduleName = "m",
            path = "m.py",
            functions = listOf(
                FlatFunctionIR(
                    name = "__module_init__",
                    qualifiedName = "m.__module_init__",
                    parentQualifiedName = null,
                    kind = FlatFunctionKind.MODULE_INIT,
                    cfg = FlatCFG.EMPTY,
                    parameters = emptyList(),
                    returnType = FlatAnyType,
                    isAsync = false,
                    isGenerator = false,
                    closureVars = emptyList(),
                    decorators = emptyList(),
                ),
            ),
            classes = listOf(
                FlatClass(
                    name = "Cls",
                    qualifiedName = "m.Cls",
                    baseClasses = emptyList(),
                    mro = emptyList(),
                    methods = listOf(
                        FlatFunctionIR(
                            name = "val",
                            qualifiedName = "m.Cls.val",
                            parentQualifiedName = null,
                            kind = FlatFunctionKind.METHOD,
                            cfg = FlatCFG.EMPTY,
                            parameters = listOf(FlatParameter("self", FlatAnyType, FlatParamKind.POSITIONAL_OR_KEYWORD, false, null)),
                            returnType = FlatAnyType,
                            isAsync = false,
                            isGenerator = false,
                            closureVars = emptyList(),
                            decorators = listOf(FlatDecorator("property", "builtins.property", emptyList())),
                        ),
                        FlatFunctionIR(
                            name = "do_stuff",
                            qualifiedName = "m.Cls.do_stuff",
                            parentQualifiedName = null,
                            kind = FlatFunctionKind.METHOD,
                            cfg = FlatCFG.EMPTY,
                            parameters = listOf(
                                FlatParameter("self", FlatAnyType, FlatParamKind.POSITIONAL_OR_KEYWORD, false, null),
                                FlatParameter("arg", FlatAnyType, FlatParamKind.POSITIONAL_OR_KEYWORD, false, null),
                            ),
                            returnType = FlatAnyType,
                            isAsync = false,
                            isGenerator = false,
                            closureVars = emptyList(),
                            decorators = emptyList(),
                        ),
                    ),
                    fields = listOf(FlatClassField("x", FlatClassType("builtins.int"), false, true)),
                    nestedClasses = emptyList(),
                    decorators = emptyList(),
                    isAbstract = false,
                    isDataclass = false,
                    isEnum = false,
                ),
            ),
            fields = emptyList(),
            imports = emptyList(),
            diagnostics = emptyList(),
        )

        val module = FlatToPirConverter(flat, stubClasspath).convert()

        assertEquals(1, module.classes.size)
        val cls = module.classes[0]
        assertEquals(2, cls.methods.size)
        assertEquals(1, cls.fields.size)
        assertEquals(1, cls.properties.size)
        assertNotNull(cls.properties[0].getter)
        assertTrue(cls.properties[0].getter!!.isProperty)
        for (method in cls.methods) {
            assertSame(cls, method.enclosingClass)
        }
        assertEquals("m", cls.module.name)
    }

    @Test
    fun `diagnostics pass through`() {
        val flat = FlatModuleIR(
            moduleName = "m",
            path = "m.py",
            functions = listOf(
                FlatFunctionIR(
                    name = "__module_init__",
                    qualifiedName = "m.__module_init__",
                    parentQualifiedName = null,
                    kind = FlatFunctionKind.MODULE_INIT,
                    cfg = FlatCFG.EMPTY,
                    parameters = emptyList(),
                    returnType = FlatAnyType,
                    isAsync = false,
                    isGenerator = false,
                    closureVars = emptyList(),
                    decorators = emptyList(),
                ),
            ),
            classes = emptyList(),
            fields = emptyList(),
            imports = emptyList(),
            diagnostics = listOf(PIRDiagnostic(PIRDiagnosticSeverity.ERROR, "test error", "fn", "TestException")),
        )

        val module = FlatToPirConverter(flat, stubClasspath).convert()

        assertEquals(1, module.diagnostics.size)
        assertEquals("test error", module.diagnostics[0].message)
        assertIs<PIRModuleImpl>(module)
    }
}
