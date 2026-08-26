package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.client.GoIRLoadConfig
import org.opentaint.ir.go.client.GoIRLoadMode
import org.opentaint.ir.go.expr.GoIRFieldAddrExpr
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.inst.GoIRReturn
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
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
        assertThat(function.bodyAvailable).isTrue()
        val returned = (function.body!!.blocks.single().terminator as GoIRReturn).results.single() as GoIRParameterValue
        assertThat(returned.paramIndex).isZero()
        assertThat(returned.type).isEqualTo(function.params.single().type)
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
        assertThat(box.pointerMethods.map { it.name }).contains("Put", "Keep", "Helper")

        val put = box.pointerMethods.single { it.name == "Put" }
        val shadowAccess = put.findExpressions<GoIRFieldAddrExpr>().single { it.fieldName == "Shadow" }
        assertThat(shadowAccess.fieldIndex).isEqualTo(1)
        assertThat(box.pointerMethods.single { it.name == "Keep" }.bodyAvailable).isTrue()
    }

    private fun Path.write(content: String) {
        toFile().writeText(content)
    }
}
