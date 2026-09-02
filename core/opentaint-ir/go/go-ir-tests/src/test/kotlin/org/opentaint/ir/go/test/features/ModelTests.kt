package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.client.GoIRLoadConfig
import org.opentaint.ir.go.client.GoIRLoadMode
import org.opentaint.ir.go.expr.GoIRFieldAddrExpr
import org.opentaint.ir.go.expr.GoIRMakeClosureExpr
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.inst.GoIRCall
import org.opentaint.ir.go.inst.GoIRGlobalStore
import org.opentaint.ir.go.inst.GoIRReturn
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.type.GoIRStructType
import org.opentaint.ir.go.value.GoIRConstValue
import org.opentaint.ir.go.value.GoIRConstantValue
import org.opentaint.ir.go.value.GoIRParameterValue
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(GoIRTestExtension::class)
class ModelTests {
    @Test
    fun `model body replaces dependency body after client deserialization`(builder: GoIRTestBuilder) {
        val project = Files.createTempDirectory("goir-model-project")
        project.resolve("go.mod").write("module example.com/app\ngo 1.22\n")
        project.resolve("main.go").write(
            "package main\nimport \"strings\"\nfunc Upper(value string) string { return strings.ToUpper(value) }\n",
        )

        val model = Files.createTempDirectory("goir-model")
        model.resolve("go.mod").write("module opentaint\ngo 1.25\n")
        val modelPackage = model.resolve("strings")
        Files.createDirectories(modelPackage)
        modelPackage.resolve("model.go").write(
            "package strings\nfunc ToUpper(value string) string { return value }\n",
        )

        val program = builder.buildFromDir(
            project,
            GoIRLoadConfig(mode = GoIRLoadMode.PROJECT, modelDirs = listOf(model)),
        )

        assertThat(program.findPackage("opentaint/strings")).isNull()
        val function = program.findPackage("strings")!!.functions.single { it.name == "ToUpper" }
        val caller = program.findPackage("example.com/app")!!.functions.single { it.name == "Upper" }
        val originalTarget = caller.findInstructions<GoIRCall>().single().call.target as GoIRCallTarget.Function
        assertThat(function.bodyAvailable).isTrue()
        assertThat(program.effectiveFunction(originalTarget.function)).isSameAs(function)
        val returned = (function.body!!.blocks.single().terminator as GoIRReturn).results.single() as GoIRParameterValue
        assertThat(returned.paramIndex).isZero()
        assertThat(returned.type).isEqualTo(function.params.single().type)
    }

    @Test
    fun `lazy model lookup keeps the original Go IR visible`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/original",
            "package original\n" +
                "func Identity(value string) string { return \"original\" }\n",
        )
        val model = model(
            "example.com/original",
            "package original\n" +
                "func helper(value string) string { return value }\n" +
                "func Identity(value string) string { return helper(value) }\n",
        )

        val effectiveProgram = builder.buildFromDir(project, modelConfig(model))
        val effectivePackage = effectiveProgram.findPackage("example.com/original")!!
        val effectiveIdentity = effectivePackage.functions.single { it.name == "Identity" }
        val originalProgram = effectiveProgram.originalProgram
        val originalPackage = originalProgram.findPackage("example.com/original")!!
        val originalIdentity = originalPackage.functions.single { it.name == "Identity" }

        assertThat(originalProgram).isNotSameAs(effectiveProgram)
        assertThat(effectiveIdentity.pkg).isSameAs(effectivePackage)
        assertThat(effectiveIdentity.syntheticKind).isEqualTo("opentaint model")
        assertThat(effectiveIdentity.findInstructions<GoIRCall>())
            .anyMatch { it.call.target?.displayName == "example.com/original.helper" }
        assertThat(originalIdentity.isSynthetic).isFalse()
        assertThat(originalIdentity.syntheticKind).isNull()
        assertThat(originalIdentity.body!!.instructions)
            .noneMatch { instruction -> instruction is GoIRCall }
        assertThat(originalPackage.functions.map { it.name }).doesNotContain("helper")
    }

    @Test
    fun `partial model adds fields and methods after client deserialization`(builder: GoIRTestBuilder) {
        val project = Files.createTempDirectory("goir-model-fields-project")
        project.resolve("go.mod").write("module example.com/fields\ngo 1.22\n")
        project.resolve("fields.go").write(
            """
            package fields
            type Box struct { Original string }
            func (b *Box) Put(value string) { b.Original = "original" }
            func (b *Box) Keep(value string) string { return "original" }
            """.trimIndent(),
        )

        val model = Files.createTempDirectory("goir-model-fields")
        model.resolve("go.mod").write("module opentaint\ngo 1.25\n")
        val modelPackage = model.resolve("example.com/fields")
        Files.createDirectories(modelPackage)
        modelPackage.resolve("model.go").write(
            """
            package fields
            type Box struct { Shadow string }
            func (b *Box) Put(value string) { b.Shadow = value }
            func (b *Box) Helper(value string) string {
                b.Shadow = value
                return b.Shadow
            }
            """.trimIndent(),
        )

        val program = builder.buildFromDir(
            project,
            GoIRLoadConfig(mode = GoIRLoadMode.PROJECT, modelDirs = listOf(model)),
        )

        val targetPackage = program.findPackage("example.com/fields")!!
        val box = targetPackage.findNamedType("Box")!!
        assertThat(box.fields.map { it.name to it.index })
            .containsExactly("Original" to 0, "Shadow" to 1)
        assertThat((box.underlying as GoIRStructType).fields.map { it.name })
            .containsExactly("Original", "Shadow")
        assertThat(box.pointerMethods.map { it.name }).contains("Put", "Keep", "Helper")

        val put = box.pointerMethods.single { it.name == "Put" }
        val shadowAccess = put.findExpressions<GoIRFieldAddrExpr>().single { it.fieldName == "Shadow" }
        assertThat(shadowAccess.fieldIndex).isEqualTo(1)
        assertThat(box.pointerMethods.single { it.name == "Keep" }.bodyAvailable).isTrue()
    }

    @Test
    fun `model package name must match target package name`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            "package main\nimport \"strings\"\nfunc Use(value string) string { return strings.ToUpper(value) }\n",
        )
        val model = model(
            "strings",
            "package modelstrings\nfunc ToUpper(value string) string { return value }\n",
        )

        assertThatThrownBy {
            builder.buildFromDir(project, modelConfig(model))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("package name \"modelstrings\", want \"strings\"")
    }

    @Test
    fun `model function signature must match target function`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            "package main\nimport \"strings\"\nfunc Use(value string) string { return strings.ToUpper(value) }\n",
        )
        val model = model(
            "strings",
            "package strings\nfunc ToUpper(value int) int { return value }\n",
        )

        assertThatThrownBy {
            builder.buildFromDir(project, modelConfig(model))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("strings.ToUpper has a different signature")
    }

    @Test
    fun `one package cannot have two partial model modules`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            "package main\nimport \"strings\"\n" +
                "func Upper(value string) string { return strings.ToUpper(value) }\n" +
                "func Lower(value string) string { return strings.ToLower(value) }\n",
        )
        val firstModel = model(
            "strings",
            "package strings\nfunc ToUpper(value string) string { return value }\n",
        )
        val secondModel = model(
            "strings",
            "package strings\nfunc ToLower(value string) string { return value }\n",
        )

        assertThatThrownBy {
            builder.buildFromDir(
                project,
                GoIRLoadConfig(
                    mode = GoIRLoadMode.PROJECT,
                    modelDirs = listOf(firstModel, secondModel),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Go package \"strings\" is modeled more than once")
    }

    @Test
    fun `one package cannot have two generic partial model modules`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            """
            package main
            import "slices"
            func Delete(values []string) []string {
                return slices.DeleteFunc(values, func(string) bool { return false })
            }
            func Contains(values []string) bool {
                return slices.ContainsFunc(values, func(string) bool { return true })
            }
            """.trimIndent(),
        )
        val firstModel = model(
            "slices",
            "package slices\nfunc DeleteFunc[S ~[]E, E any](values S, _ func(E) bool) S { return values }\n",
        )
        val secondModel = model(
            "slices",
            "package slices\nfunc ContainsFunc[S ~[]E, E any](_ S, predicate func(E) bool) bool { " +
                "var value E; return predicate(value) }\n",
        )

        assertThatThrownBy {
            builder.buildFromDir(
                project,
                GoIRLoadConfig(
                    mode = GoIRLoadMode.PROJECT,
                    modelDirs = listOf(firstModel, secondModel),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Go package \"slices\" is modeled more than once")
    }

    @Test
    fun `generic model instance body uses concrete target types`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            "package main\nimport \"slices\"\nfunc Clone(values []string) []string { return slices.Clone(values) }\n",
        )
        val model = model(
            "slices",
            "package slices\nfunc Clone[S ~[]E, E any](values S) S { return values }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val clone = program.findPackage("slices")!!.functions.single {
            it.fullName.startsWith("slices.Clone[")
        }
        val returned = (clone.body!!.blocks.single().terminator as GoIRReturn)
            .results.single() as GoIRParameterValue

        assertThat(clone.typeParams.map { it.name }).containsExactly("S", "E")
        assertThat(returned.type).isEqualTo(clone.params.single().type)
        assertThat(returned.type).isEqualTo(clone.signature.results.single())
    }

    @Test
    fun `generic model specializes compound body types`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/genericmodel",
            """
            package genericmodel
            type Box[T any] struct { Value T }
            func Flow[T any](value T) (
                *T, []T, map[string]T, chan T,
                [1]T, struct { Value T }, func(T) T, Box[T],
            ) {
                var array [1]T
                var record struct { Value T }
                var callback func(T) T
                var box Box[T]
                return nil, nil, nil, nil, array, record, callback, box
            }
            func Use(value string) { Flow[string](value) }
            """.trimIndent(),
        )
        val model = model(
            "example.com/genericmodel",
            """
            package genericmodel
            type Box[T any] struct { Value T }
            func Flow[T any](value T) (
                *T, []T, map[string]T, chan T,
                [1]T, struct { Value T }, func(T) T, Box[T],
            ) {
                pointer := &value
                values := []T{value}
                index := map[string]T{"value": value}
                stream := make(chan T, 1)
                stream <- value
                array := [1]T{value}
                record := struct { Value T }{Value: value}
                callback := func(input T) T { return input }
                box := Box[T]{Value: value}
                return pointer, values, index, stream, array, record, callback, box
            }
            """.trimIndent(),
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val flow = program.findPackage("example.com/genericmodel")!!.functions.single {
            it.fullName.startsWith("example.com/genericmodel.Flow[")
        }
        val returned = (flow.body!!.blocks.single { block ->
            block.terminator is GoIRReturn
        }.terminator as GoIRReturn).results

        assertThat(returned.map { it.type }).containsExactlyElementsOf(flow.signature.results)
    }

    @Test
    fun `generic receiver model instance uses concrete target types`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/genericreceiver",
            """
            package genericreceiver
            type Box[T any] struct { value T }
            func (box Box[T]) Get() T { return box.value }
            func Use(box Box[string]) string { return box.Get() }
            """.trimIndent(),
        )
        val model = model(
            "example.com/genericreceiver",
            """
            package genericreceiver
            type Box[T any] struct { value T }
            func (box Box[T]) Get() T { return box.value }
            """.trimIndent(),
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val functions = program.findPackage("example.com/genericreceiver")!!.functions
        val get = functions.single {
            it.fullName.contains("Box[string]") && it.name.startsWith("Get[")
        }
        val returned = (get.body!!.blocks.single().terminator as GoIRReturn).results.single()

        assertThat(returned.type).isEqualTo(get.signature.results.single())
    }

    @Test
    fun `generic model closure uses concrete target types`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/genericclosure",
            """
            package genericclosure
            func Apply[T any](value T) T { return value }
            func UseString(value string) string { return Apply[string](value) }
            func UseInt(value int) int { return Apply[int](value) }
            """.trimIndent(),
        )
        val model = model(
            "example.com/genericclosure",
            """
            package genericclosure
            func Apply[T any](value T) T {
                nested := func(input T) T {
                    inner := func() T { return value }
                    return inner()
                }
                return nested(value)
            }
            """.trimIndent(),
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val applyInstances = program.findPackage("example.com/genericclosure")!!.functions.filter {
            it.fullName.startsWith("example.com/genericclosure.Apply[")
        }
        assertThat(applyInstances).hasSize(2)
        applyInstances.forEach { apply ->
            val outer = apply.findExpressions<GoIRMakeClosureExpr>().single().fn
            val inner = outer.findExpressions<GoIRMakeClosureExpr>().single().fn
            val concreteType = apply.signature.results.single()

            assertThat(outer.parent!!.function).isSameAs(apply)
            assertThat(inner.parent!!.function).isSameAs(outer)
            assertThat(outer.signature.params.single()).isEqualTo(apply.signature.params.single())
            assertThat(outer.signature.results.single()).isEqualTo(concreteType)
            assertThat(inner.signature.results.single()).isEqualTo(concreteType)
            val returned = (inner.body!!.blocks.single().terminator as GoIRReturn).results.single()
            assertThat(returned.type).isEqualTo(concreteType)
        }
        assertThat(
            applyInstances.map { apply ->
                apply.findExpressions<GoIRMakeClosureExpr>().single().fn.fullName
            },
        ).doesNotHaveDuplicates()
    }

    @Test
    fun `one package cannot be modeled twice`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            "package main\nimport \"strings\"\nfunc Use(value string) string { return strings.ToUpper(value) }\n",
        )
        val source = "package strings\nfunc ToUpper(value string) string { return value }\n"
        val firstModel = model("strings", source)
        val secondModel = model("strings", source)

        assertThatThrownBy {
            builder.buildFromDir(
                project,
                GoIRLoadConfig(
                    mode = GoIRLoadMode.PROJECT,
                    modelDirs = listOf(firstModel, secondModel),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Go package \"strings\" is modeled more than once")
    }

    @Test
    fun `model package requires opentaint address prefix`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/app",
            "package main\nimport \"strings\"\nfunc Use(value string) string { return strings.ToUpper(value) }\n",
        )
        val model = model(
            targetPath = "strings",
            source = "package strings\nfunc ToUpper(value string) string { return value }\n",
            module = "models",
        )

        assertThatThrownBy {
            builder.buildFromDir(project, modelConfig(model))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("import path must be opentaint/<target-import-path>")
    }

    @Test
    fun `model initializer and its closures do not replace target initialization`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/initmodel",
            "package initmodel\nvar Value string\nfunc init() { Value = \"original\" }\n",
        )
        val model = model(
            "example.com/initmodel",
            "package initmodel\nvar Value string\nfunc init() { write := func() { Value = \"model\" }; write() }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val target = program.findPackage("example.com/initmodel")!!
        val initializer = target.functions.single { it.name == "init#1" }
        val values = initializer.findInstructions<GoIRGlobalStore>()
            .mapNotNull { (it.value as? GoIRConstValue)?.value as? GoIRConstantValue.StringConst }
            .map { it.value }

        assertThat(values).contains("original").doesNotContain("model")
        assertThat(target.functions.map { it.fullName }).noneMatch { "opentaint" in it }
    }

    @Test
    fun `existing model field type must match target field type`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/fieldtype",
            "package fieldtype\ntype Box struct { Value string }\nfunc (b *Box) Put(value string) {}\n",
        )
        val model = model(
            "example.com/fieldtype",
            "package fieldtype\ntype Box struct { Value int }\nfunc (b *Box) Put(value string) { b.Value = len(value) }\n",
        )

        assertThatThrownBy {
            builder.buildFromDir(project, modelConfig(model))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("field example.com/fieldtype.Box.Value has a different type")
    }

    @Test
    fun `versioned target path can use its declared package name`(builder: GoIRTestBuilder) {
        val project = project(
            "github.com/acme/widget/v2",
            "package widget\nfunc Identity(value string) string { return \"original\" }\n",
        )
        val model = model(
            "github.com/acme/widget/v2",
            "package widget\nfunc Identity(value string) string { return value }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val identity = program.findPackage("github.com/acme/widget/v2")!!
            .functions.single { it.name == "Identity" }
        val returned = (identity.body!!.blocks.single().terminator as GoIRReturn).results.single()

        assertThat(returned).isInstanceOf(GoIRParameterValue::class.java)
    }

    @Test
    fun `model can use the dependency that it models`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/dependency",
            "package dependency\ntype Value struct { Text string }\n" +
                "func Identity(value Value) Value { return Value{Text: \"original\"} }\n",
        )
        val model = model(
            "example.com/dependency",
            "package dependency\nimport target \"example.com/dependency\"\n" +
                "func Identity(value target.Value) target.Value { return value }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val identity = program.findPackage("example.com/dependency")!!
            .functions.single { it.name == "Identity" && it.parent == null }
        val returned = (identity.body!!.blocks.single().terminator as GoIRReturn).results.single()

        assertThat((returned as GoIRParameterValue).paramIndex).isZero()
    }

    @Test
    fun `model call into modeled dependency resolves through effective lookup`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/dependencycall",
            "package dependencycall\n" +
                "func Helper(value string) string { return \"original\" }\n" +
                "func Identity(value string) string { return \"original\" }\n",
        )
        val model = model(
            "example.com/dependencycall",
            "package dependencycall\n" +
                "import target \"example.com/dependencycall\"\n" +
                "func Identity(value string) string { return target.Helper(value) }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val targetPackage = program.findPackage("example.com/dependencycall")!!
        val helper = targetPackage.functions.single { it.name == "Helper" && it.parent == null }
        val identity = targetPackage.functions.single { it.name == "Identity" && it.parent == null }
        val callTarget = identity.findInstructions<GoIRCall>().single().call.target

        assertThat(callTarget).isInstanceOf(GoIRCallTarget.Function::class.java)
        assertThat((callTarget as GoIRCallTarget.Function).function).isSameAs(helper)
    }

    @Test
    fun `model-only dependency is visible only through effective lookup`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/modeldependency",
            "package modeldependency\n" +
                "func Identity(value string) string { return \"original\" }\n",
        )
        val model = model(
            "example.com/modeldependency",
            "package modeldependency\n" +
                "import \"net/url\"\n" +
                "func Identity(value string) string { return url.QueryEscape(value) }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val identity = program.findPackage("example.com/modeldependency")!!
            .functions.single { it.name == "Identity" && it.parent == null }
        val call = identity.findInstructions<GoIRCall>().single()

        assertThat(program.originalProgram.findPackage("net/url")).isNull()
        assertThat(program.findPackage("net/url")).isNotNull()
        assertThat(call.call.target?.displayName).isEqualTo("net/url.QueryEscape")
    }

    @Test
    fun `imported target declarations do not damage generic target types`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/genericdependency",
            """
            package genericdependency
            import _ "net/http"
            type Box[T any] struct { Value T }
            type Callback func(*Box[string])
            func Register(Callback) {}
            """.trimIndent(),
        )
        val model = model(
            "example.com/genericdependency",
            """
            package genericdependency
            import _ "net/http"
            import target "example.com/genericdependency"
            func Register(callback target.Callback) { callback(nil) }
            """.trimIndent(),
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val targetPackage = program.findPackage("example.com/genericdependency")!!
        val register = targetPackage
            .functions.single { it.name == "Register" }

        assertThat(targetPackage.findNamedType("Box")!!.typeParams.map { it.name })
            .containsExactly("T")
        assertThat(register.findInstructions<GoIRCall>())
            .anyMatch { it.call.target is GoIRCallTarget.Dynamic }
    }

    @Test
    fun `model adds support declarations and keeps helper calls`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/support",
            "package support\nfunc Target(value string) string { return \"original\" }\n",
        )
        val model = model(
            "example.com/support",
            """
            package support
            type Added struct { Text string }
            var AddedGlobal string
            const AddedConstant = "constant"
            func helper(value string) string { return value }
            func Target(value string) string { return helper(value) }
            """.trimIndent(),
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val targetPackage = program.findPackage("example.com/support")!!
        val helper = targetPackage.functions.single { it.name == "helper" }
        val target = targetPackage.functions.single { it.name == "Target" }
        val calledFunction = target.findInstructions<GoIRCall>().single().call.target as GoIRCallTarget.Function

        assertThat(targetPackage.findNamedType("Added")).isNotNull()
        assertThat(targetPackage.findGlobal("AddedGlobal")).isNotNull()
        assertThat(targetPackage.findConstant("AddedConstant")).isNotNull()
        assertThat(helper.isSynthetic).isTrue()
        assertThat(helper.syntheticKind).isEqualTo("opentaint model support")
        assertThat(helper.bodyAvailable).isTrue()
        assertThat(calledFunction.function).isSameAs(helper)
    }

    @Test
    fun `model body owns its anonymous functions`(builder: GoIRTestBuilder) {
        val project = project(
            "example.com/closures",
            "package closures\nfunc Apply(value string) string { return \"original\" }\n",
        )
        val model = model(
            "example.com/closures",
            "package closures\nfunc Apply(value string) string { " +
                "nested := func(input string) string { return input }; return nested(value) }\n",
        )

        val program = builder.buildFromDir(project, modelConfig(model))
        val apply = program.findPackage("example.com/closures")!!.functions.single { it.name == "Apply" }
        val closure = apply.anonymousFunctions.single().function

        assertThat(apply.anonymousFunctions).hasSize(1)
        assertThat(closure.parent!!.function).isSameAs(apply)
        assertThat(closure.bodyAvailable).isTrue()
    }

    @Test
    fun `one model module can model multiple target packages`(builder: GoIRTestBuilder) {
        val project = Files.createTempDirectory("goir-multi-package-project")
        project.resolve("go.mod").write("module example.com/multiple\ngo 1.22\n")
        listOf("first", "second").forEach { name ->
            val packageDir = project.resolve(name)
            Files.createDirectories(packageDir)
            packageDir.resolve("source.go").write(
                "package $name\nfunc Identity(value string) string { return \"original\" }\n",
            )
        }

        val model = Files.createTempDirectory("goir-multi-package-model")
        model.resolve("go.mod").write("module opentaint\ngo 1.25\n")
        listOf("first", "second").forEach { name ->
            val packageDir = model.resolve("example.com/multiple/$name")
            Files.createDirectories(packageDir)
            packageDir.resolve("model.go").write(
                "package $name\nfunc Identity(value string) string { return value }\n",
            )
        }

        val program = builder.buildFromDir(project, modelConfig(model))
        listOf("first", "second").forEach { name ->
            val identity = program.findPackage("example.com/multiple/$name")!!
                .functions.single { it.name == "Identity" }
            val returned = (identity.body!!.blocks.single().terminator as GoIRReturn).results.single()
            assertThat(returned).isInstanceOf(GoIRParameterValue::class.java)
        }
    }

    private fun project(module: String, source: String): Path {
        val project = Files.createTempDirectory("goir-model-project")
        project.resolve("go.mod").write("module $module\ngo 1.22\n")
        project.resolve("source.go").write(source)
        return project
    }

    private fun model(targetPath: String, source: String, module: String = "opentaint"): Path {
        val model = Files.createTempDirectory("goir-model")
        model.resolve("go.mod").write("module $module\ngo 1.25\n")
        val modelPackage = model.resolve(targetPath)
        Files.createDirectories(modelPackage)
        modelPackage.resolve("model.go").write(source)
        return model
    }

    private fun modelConfig(model: Path): GoIRLoadConfig {
        return GoIRLoadConfig(mode = GoIRLoadMode.PROJECT, modelDirs = listOf(model))
    }

    private fun Path.write(content: String) {
        toFile().writeText(content)
    }
}
