package org.opentaint.jvm.sast.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class KotlinClassNameExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extract single class with package`() {
        val file = tempDir.resolve("MyClass.kt").apply {
            writeText("""
                package com.example
                
                class MyClass {
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("MyClass", result.classSimpleNames[0])
        assertEquals("com.example.MyClass", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract multiple classes with package`() {
        val file = tempDir.resolve("Classes.kt").apply {
            writeText("""
                package org.opentaint.test
                
                class FirstClass
                
                class SecondClass {
                    fun doSomething() {}
                }
                
                class ThirdClass
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(3, result.classSimpleNames.size)
        assertEquals("FirstClass", result.classSimpleNames[0])
        assertEquals("org.opentaint.test.FirstClass", result.fullyQualifiedNames.orEmpty()[0])
        assertEquals("SecondClass", result.classSimpleNames[1])
        assertEquals("org.opentaint.test.SecondClass", result.fullyQualifiedNames.orEmpty()[1])
        assertEquals("ThirdClass", result.classSimpleNames[2])
        assertEquals("org.opentaint.test.ThirdClass", result.fullyQualifiedNames.orEmpty()[2])
    }

    @Test
    fun `extract class without package`() {
        val file = tempDir.resolve("NoPackage.kt").apply {
            writeText("""
                class NoPackageClass {
                    val x = 1
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("NoPackageClass", result.classSimpleNames[0])
        assertEquals(null, result.fullyQualifiedNames)
    }

    @Test
    fun `extract interface`() {
        val file = tempDir.resolve("MyInterface.kt").apply {
            writeText("""
                package com.example.api
                
                interface MyInterface {
                    fun doWork()
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("MyInterface", result.classSimpleNames[0])
        assertEquals("com.example.api.MyInterface", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract object declaration`() {
        val file = tempDir.resolve("MySingleton.kt").apply {
            writeText("""
                package com.example
                
                object MySingleton {
                    fun getInstance() = this
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("MySingleton", result.classSimpleNames[0])
        assertEquals("com.example.MySingleton", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract enum class`() {
        val file = tempDir.resolve("Color.kt").apply {
            writeText("""
                package com.example.enums
                
                enum class Color {
                    RED, GREEN, BLUE
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("Color", result.classSimpleNames[0])
        assertEquals("com.example.enums.Color", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract data class`() {
        val file = tempDir.resolve("Person.kt").apply {
            writeText("""
                package com.example.model
                
                data class Person(
                    val name: String,
                    val age: Int
                )
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("Person", result.classSimpleNames[0])
        assertEquals("com.example.model.Person", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract mixed declarations`() {
        val file = tempDir.resolve("Mixed.kt").apply {
            writeText("""
                package com.example
                
                interface Service {
                    fun process()
                }
                
                class ServiceImpl : Service {
                    override fun process() {}
                }
                
                object ServiceFactory {
                    fun create(): Service = ServiceImpl()
                }
                
                enum class Status {
                    ACTIVE, INACTIVE
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(4, result.classSimpleNames.size)
        val expectedNames = setOf("Service", "ServiceImpl", "ServiceFactory", "Status")
        assertEquals(expectedNames, result.classSimpleNames.toSet())

        val expectedFqn = expectedNames.map { "com.example.$it" }.toSet()
        assertEquals(expectedFqn, result.fullyQualifiedNames.orEmpty().toSet())
    }

    @Test
    fun `extract nested class package with subpackages`() {
        val file = tempDir.resolve("DeepPackage.kt").apply {
            writeText("""
                package com.example.very.deep.nested.pkg
                
                class DeepClass
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("DeepClass", result.classSimpleNames[0])
        assertEquals("com.example.very.deep.nested.pkg.DeepClass", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract class with companion object`() {
        val file = tempDir.resolve("WithCompanion.kt").apply {
            writeText("""
                package com.example
                
                class WithCompanion {
                    companion object {
                        const val TAG = "WithCompanion"
                    }
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        // Companion object is anonymous, only the outer class is extracted
        assertEquals(1, result.classSimpleNames.size)
        assertEquals("WithCompanion", result.classSimpleNames[0])
        assertEquals("com.example.WithCompanion", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract sealed class hierarchy`() {
        val file = tempDir.resolve("Result.kt").apply {
            writeText("""
                package com.example.result
                
                sealed class Result {
                    class Success(val data: String) : Result()
                    class Error(val exception: Exception) : Result()
                }
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(3, result.classSimpleNames.size)
        assertEquals(listOf("Result", "Success", "Error"), result.classSimpleNames)
    }

    @Test
    fun `handle empty file`() {
        val file = tempDir.resolve("Empty.kt").apply {
            writeText("")
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(0, result.classSimpleNames.size)
    }

    @Test
    fun `handle file with only package declaration`() {
        val file = tempDir.resolve("OnlyPackage.kt").apply {
            writeText("""
                package com.example
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(0, result.classSimpleNames.size)
    }

    @Test
    fun `extract class with annotations`() {
        val file = tempDir.resolve("Annotated.kt").apply {
            writeText("""
                package com.example
                
                @Suppress("unused")
                class AnnotatedClass
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("AnnotatedClass", result.classSimpleNames[0])
        assertEquals("com.example.AnnotatedClass", result.fullyQualifiedNames.orEmpty()[0])
    }

    @Test
    fun `extract class with generic parameters`() {
        val file = tempDir.resolve("Generic.kt").apply {
            writeText("""
                package com.example
                
                class Box<T>(val value: T)
            """.trimIndent())
        }

        val result = KotlinClassNameIndexer.extractClassNames(file)

        assertEquals(1, result.classSimpleNames.size)
        assertEquals("Box", result.classSimpleNames[0])
        assertEquals("com.example.Box", result.fullyQualifiedNames.orEmpty()[0])
    }
}
