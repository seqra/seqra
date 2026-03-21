package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedClassesTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
from abc import ABC, abstractmethod
from enum import Enum
from dataclasses import dataclass

class Base:
    def base_method(self) -> int:
        return 0

class Child(Base):
    def child_method(self) -> int:
        return 1

class Multi(Base):
    pass

class GrandChild(Child, Multi):
    pass

class WithFields:
    class_var: int = 42
    def __init__(self) -> None:
        self.instance_var: str = "hello"

class Abstract(ABC):
    @abstractmethod
    def do_thing(self) -> int:
        pass

class MyEnum(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3

@dataclass
class Point:
    x: int
    y: int

class Outer:
    class Inner:
        def inner_method(self) -> int:
            return 0

class Decorated:
    @staticmethod
    def static_fn() -> int:
        return 1

    @classmethod
    def class_fn(cls) -> int:
        return 2

class WithProperty:
    @property
    def value(self) -> int:
        return 42
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun cls(name: String) = cp.findClassOrNull("__test__.$name")!!

    @Test fun `inheritance - baseClasses includes Base`() {
        val c = cls("Child")
        assertTrue(c.baseClasses.any { "Base" in it }, "Child should have Base in baseClasses: ${c.baseClasses}")
    }

    @Test fun `multiple inheritance`() {
        val c = cls("GrandChild")
        assertTrue(c.baseClasses.size >= 2, "GrandChild should have >= 2 base classes: ${c.baseClasses}")
    }

    @Test fun `class fields are extracted`() {
        val c = cls("WithFields")
        assertTrue(c.fields.isNotEmpty(), "WithFields should have fields")
        assertTrue(c.fields.any { it.name == "class_var" }, "Should have class_var field")
    }

    @Test fun `abstract class`() {
        val c = cls("Abstract")
        assertTrue(c.isAbstract, "Abstract should have isAbstract=true")
    }

    @Test fun `enum class`() {
        val c = cls("MyEnum")
        assertTrue(c.isEnum, "MyEnum should have isEnum=true")
    }

    @Test fun `dataclass`() {
        val c = cls("Point")
        assertTrue(c.isDataclass, "Point should have isDataclass=true")
    }

    @Test fun `nested class`() {
        val outer = cls("Outer")
        assertTrue(outer.nestedClasses.isNotEmpty(), "Outer should have nested classes")
        assertTrue(outer.nestedClasses.any { it.name == "Inner" })
    }

    @Test fun `class with methods`() {
        val c = cls("Base")
        assertTrue(c.methods.isNotEmpty(), "Base should have methods")
        assertTrue(c.methods.any { it.name == "base_method" })
    }

    @Test fun `static method decorator`() {
        val c = cls("Decorated")
        val m = c.methods.find { it.name == "static_fn" }
        assertNotNull(m, "static_fn should exist")
        assertTrue(m!!.isStaticMethod)
    }

    @Test fun `class method decorator`() {
        val c = cls("Decorated")
        val m = c.methods.find { it.name == "class_fn" }
        assertNotNull(m, "class_fn should exist")
        assertTrue(m!!.isClassMethod)
    }

    @Test fun `property is detected`() {
        val c = cls("WithProperty")
        val m = c.methods.find { it.name == "value" }
        assertNotNull(m, "value method should exist")
        assertTrue(m!!.isProperty, "value should have isProperty=true")
    }
}
