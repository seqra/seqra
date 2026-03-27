package org.opentaint.ir.go.test.features

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/**
 * Annotation-driven tests (Strategy 2, Approach B):
 * Reads Go source files with `//@ inst(...)` annotations and verifies the IR matches.
 *
 * Test files are in `src/test/resources/testdata/features/`.
 * Each file contains `//go:ir-test func=FuncName` directives and `//@ ...` annotations.
 */
@ExtendWith(GoIRTestExtension::class)
class AnnotationDrivenTests {

    @TestFactory
    fun `annotated test files`(builder: GoIRTestBuilder): List<DynamicTest> {
        val testDataDir = javaClass.classLoader.getResource("testdata/features")
            ?: return emptyList()

        val testFiles = java.io.File(testDataDir.toURI())
            .listFiles { f -> f.extension == "go" }
            ?.sortedBy { it.name }
            ?: return emptyList()

        return testFiles.map { file ->
            DynamicTest.dynamicTest(file.nameWithoutExtension) {
                val source = file.readText()
                val annotationFile = GoIRAnnotationParser.parse(source)

                // Build IR
                val prog = builder.buildFromSource(source)

                // Always run sanity checker
                GoIRSanityChecker.check(prog).assertNoErrors()

                // Verify annotations
                if (annotationFile.annotations.isNotEmpty()) {
                    val result = GoIRAnnotationVerifier.verify(prog, annotationFile)
                    result.assertNoFailures()
                }
            }
        }
    }
}
