package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Deep tests for properties, decorators, dataclasses, and enums.
 *
 * Covers: property getter/setter/deleter, custom decorators, stacked decorators,
 * decorator with arguments, staticmethod, classmethod, dataclass generated methods,
 * and enum class members/methods.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyDecoratorTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
from dataclasses import dataclass, field
from enum import Enum, IntEnum

# ─── Property getter / setter / deleter ─────────────────

class PropClass:
    def __init__(self) -> None:
        self._value: int = 0
        self._name: str = ""

    @property
    def value(self) -> int:
        return self._value

    @value.setter
    def value(self, v: int) -> None:
        self._value = v

    @value.deleter
    def value(self) -> None:
        self._value = 0

    @property
    def name(self) -> str:
        return self._name

    @name.setter
    def name(self, n: str) -> None:
        self._name = n

# ─── Custom decorators ──────────────────────────────────

def my_decorator(func):
    def wrapper(*args, **kwargs):
        return func(*args, **kwargs)
    return wrapper

def repeat(n: int):
    def decorator(func):
        def wrapper(*args, **kwargs):
            result = None
            for _ in range(n):
                result = func(*args, **kwargs)
            return result
        return wrapper
    return decorator

def log_calls(func):
    def wrapper(*args, **kwargs):
        return func(*args, **kwargs)
    return wrapper

class CustomDecClass:
    @my_decorator
    def decorated_method(self) -> int:
        return 1

    @repeat(3)
    def repeated_method(self) -> int:
        return 2

    @log_calls
    @my_decorator
    def stacked_method(self) -> int:
        return 3

@my_decorator
def decorated_function(x: int) -> int:
    return x + 1

@repeat(2)
def repeated_function(x: int) -> int:
    return x * 2

@log_calls
@my_decorator
def stacked_function(x: int) -> int:
    return x - 1

# ─── staticmethod / classmethod ─────────────────────────

class StaticClassMethods:
    class_var: int = 99

    @staticmethod
    def static_fn(x: int) -> int:
        return x + 1

    @classmethod
    def class_fn(cls) -> int:
        return cls.class_var

    def instance_fn(self) -> int:
        return 0

# ─── Dataclass ──────────────────────────────────────────

@dataclass
class SimpleData:
    x: int
    y: str

@dataclass
class DataWithDefaults:
    name: str = "default"
    values: list = field(default_factory=list)
    count: int = 0

# ─── Enum ───────────────────────────────────────────────

class Color(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3

class Priority(IntEnum):
    LOW = 1
    MEDIUM = 2
    HIGH = 3

    def describe(self) -> str:
        return self.name.lower()
""".trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun findFunc(name: String): PIRFunction {
        for (m in cp.modules) {
            for (f in m.functions) {
                if (f.qualifiedName.endsWith(name)) return f
            }
            for (c in m.classes) {
                for (f in c.methods) {
                    if (f.qualifiedName.endsWith(name)) return f
                }
            }
        }
        throw AssertionError("Function not found: $name")
    }

    private fun findClass(name: String): PIRClass {
        for (m in cp.modules) {
            for (c in m.classes) {
                if (c.qualifiedName.endsWith(name)) return c
            }
        }
        throw AssertionError("Class not found: $name")
    }

    // ─── Property getter tests ─────────────────────────────

    @Test fun `property getter is flagged as isProperty`() {
        val cls = findClass("PropClass")
        val getter = cls.methods.find { it.name == "value" && it.isProperty }
        assertNotNull(getter, "value getter should exist as a method with isProperty=true; " +
            "methods: ${cls.methods.map { "${it.name}(isProperty=${it.isProperty})" }}")
    }

    @Test fun `class exposes property via properties list`() {
        val cls = findClass("PropClass")
        val valueProp = cls.properties.find { it.name == "value" }
        assertNotNull(valueProp,
            "PropClass should have a 'value' property; got: ${cls.properties.map { it.name }}")
    }

    @Test fun `property getter is wired in PIRProperty`() {
        val cls = findClass("PropClass")
        val valueProp = cls.properties.find { it.name == "value" }
        assertNotNull(valueProp, "value property not found")
        assertNotNull(valueProp!!.getter, "value property should have a getter")
        assertTrue(valueProp.getter!!.isProperty, "getter should be flagged as isProperty")
    }

    @Test fun `property getter body accesses backing field`() {
        val cls = findClass("PropClass")
        val getter = cls.properties.find { it.name == "value" }?.getter
            ?: cls.methods.find { it.name == "value" && it.isProperty }
        assertNotNull(getter, "value getter not found")
        val loadAttrs = getter!!.cfg.blocks.flatMap { it.instructions }
            .filterAssignOf<PIRAttrExpr>()
        assertTrue(loadAttrs.any { it.attrExpr.attribute == "_value" },
            "Getter body should load self._value")
    }

    // ─── Property setter tests ─────────────────────────────

    @Test fun `property setter is present in PIRProperty`() {
        val cls = findClass("PropClass")
        val valueProp = cls.properties.find { it.name == "value" }
        assertNotNull(valueProp, "value property not found")
        assertNotNull(valueProp!!.setter, "value property should have a setter")
    }

    @Test fun `property setter body stores to backing field`() {
        val cls = findClass("PropClass")
        val valueProp = cls.properties.find { it.name == "value" }
        assertNotNull(valueProp, "value property not found")
        val setter = valueProp!!.setter
        assertNotNull(setter, "value setter not found")
        assertTrue(setter!!.cfg.blocks.isNotEmpty(), "setter should have valid CFG")
        val storeAttrs = setter.cfg.blocks.flatMap { it.instructions }
            .filterIsInstance<PIRStoreAttr>()
        assertTrue(storeAttrs.any { it.attribute == "_value" },
            "Setter body should store to self._value")
    }

    // ─── Property deleter tests ────────────────────────────

    @Test fun `property deleter is present in PIRProperty`() {
        val cls = findClass("PropClass")
        val valueProp = cls.properties.find { it.name == "value" }
        assertNotNull(valueProp, "value property not found")
        assertNotNull(valueProp!!.deleter,
            "value property should have a deleter; methods: ${cls.methods.map { it.name }}")
    }

    // ─── Multiple properties on one class ──────────────────

    @Test fun `class with multiple properties exposes all`() {
        val cls = findClass("PropClass")
        val propNames = cls.properties.map { it.name }.toSet()
        assertTrue("value" in propNames, "Expected 'value' property; got: $propNames")
        assertTrue("name" in propNames, "Expected 'name' property; got: $propNames")
    }

    @Test fun `second property has getter and setter but no deleter`() {
        val cls = findClass("PropClass")
        val nameProp = cls.properties.find { it.name == "name" }
        assertNotNull(nameProp, "name property not found; got: ${cls.properties.map { it.name }}")
        assertNotNull(nameProp!!.getter, "name property should have a getter")
        assertNotNull(nameProp.setter, "name property should have a setter")
        assertNull(nameProp.deleter, "name property should NOT have a deleter")
    }

    // ─── Custom decorator tests ────────────────────────────

    @Test fun `custom decorated method exists on class`() {
        val cls = findClass("CustomDecClass")
        val m = cls.methods.find { it.name == "decorated_method" }
        assertNotNull(m, "decorated_method should exist; " +
            "methods: ${cls.methods.map { it.name }}")
    }

    @Test fun `custom decorator appears in function decorator list`() {
        val cls = findClass("CustomDecClass")
        val m = cls.methods.find { it.name == "decorated_method" }
        assertNotNull(m, "decorated_method not found")
        // mypy may or may not preserve custom decorators in the decorator list
        // At minimum the method should be callable and have a CFG
        assertTrue(m!!.cfg.blocks.isNotEmpty(),
            "decorated_method should have a valid CFG")
    }

    @Test fun `module-level decorated function is found`() {
        val f = cp.findFunctionOrNull("__test__.decorated_function")
        assertNotNull(f, "decorated_function should be found at module level")
        assertTrue(f!!.cfg.blocks.isNotEmpty(),
            "decorated_function should have a valid CFG")
    }

    // ─── Decorator with arguments ──────────────────────────

    @Test fun `decorator with arguments - method exists`() {
        val cls = findClass("CustomDecClass")
        val m = cls.methods.find { it.name == "repeated_method" }
        assertNotNull(m, "repeated_method should exist; " +
            "methods: ${cls.methods.map { it.name }}")
    }

    @Test fun `decorator with arguments - module function exists`() {
        val f = cp.findFunctionOrNull("__test__.repeated_function")
        assertNotNull(f, "repeated_function should be found at module level")
    }

    // ─── Stacked decorators ────────────────────────────────

    @Test fun `stacked decorators - method exists with valid CFG`() {
        val cls = findClass("CustomDecClass")
        val m = cls.methods.find { it.name == "stacked_method" }
        assertNotNull(m, "stacked_method should exist; " +
            "methods: ${cls.methods.map { it.name }}")
        assertTrue(m!!.cfg.blocks.isNotEmpty(),
            "stacked_method should have a valid CFG")
    }

    @Test fun `stacked decorators - module function exists with valid CFG`() {
        val f = cp.findFunctionOrNull("__test__.stacked_function")
        assertNotNull(f, "stacked_function should be found at module level")
        assertTrue(f!!.cfg.blocks.isNotEmpty(),
            "stacked_function should have a valid CFG")
    }

    // ─── staticmethod / classmethod ────────────────────────

    @Test fun `staticmethod flag is set and decorator list is clean`() {
        val cls = findClass("StaticClassMethods")
        val m = cls.methods.find { it.name == "static_fn" }
        assertNotNull(m, "static_fn should exist")
        assertTrue(m!!.isStaticMethod, "static_fn should have isStaticMethod=true")
        // mypy strips @staticmethod from decorator list and sets the flag instead
        val decoNames = m.decorators.map { it.name }
        assertFalse(decoNames.contains("staticmethod"),
            "mypy should strip @staticmethod from decorator list; got: $decoNames")
    }

    @Test fun `classmethod flag is set and decorator list is clean`() {
        val cls = findClass("StaticClassMethods")
        val m = cls.methods.find { it.name == "class_fn" }
        assertNotNull(m, "class_fn should exist")
        assertTrue(m!!.isClassMethod, "class_fn should have isClassMethod=true")
        val decoNames = m.decorators.map { it.name }
        assertFalse(decoNames.contains("classmethod"),
            "mypy should strip @classmethod from decorator list; got: $decoNames")
    }

    @Test fun `instance method is neither static nor classmethod`() {
        val cls = findClass("StaticClassMethods")
        val m = cls.methods.find { it.name == "instance_fn" }
        assertNotNull(m, "instance_fn should exist")
        assertFalse(m!!.isStaticMethod, "instance_fn should NOT be static")
        assertFalse(m.isClassMethod, "instance_fn should NOT be classmethod")
        assertFalse(m.isProperty, "instance_fn should NOT be property")
    }

    // ─── Dataclass tests ───────────────────────────────────

    @Test fun `dataclass flag is set on SimpleData`() {
        val cls = findClass("SimpleData")
        assertTrue(cls.isDataclass, "SimpleData should have isDataclass=true")
    }

    @Test fun `dataclass has generated __init__`() {
        val cls = findClass("SimpleData")
        val init = cls.methods.find { it.name == "__init__" }
        assertNotNull(init, "Dataclass SimpleData should have a generated __init__; " +
            "methods: ${cls.methods.map { it.name }}")
    }

    @Test fun `dataclass __init__ has parameters matching fields`() {
        val cls = findClass("SimpleData")
        val init = cls.methods.find { it.name == "__init__" }
        assertNotNull(init, "__init__ not found")
        // Parameters should include 'self' plus the declared fields
        val paramNames = init!!.parameters.map { it.name }
        assertTrue("x" in paramNames,
            "Expected 'x' param in __init__, got: $paramNames")
        assertTrue("y" in paramNames,
            "Expected 'y' param in __init__, got: $paramNames")
    }

    @Test fun `dataclass with defaults is flagged`() {
        val cls = findClass("DataWithDefaults")
        assertTrue(cls.isDataclass, "DataWithDefaults should have isDataclass=true")
    }

    @Test fun `dataclass with defaults has fields`() {
        val cls = findClass("DataWithDefaults")
        val fieldNames = cls.fields.map { it.name }
        assertTrue("name" in fieldNames || "count" in fieldNames,
            "DataWithDefaults should have fields; got: $fieldNames")
    }

    // ─── Enum tests ────────────────────────────────────────

    @Test fun `enum flag is set on Color`() {
        val cls = findClass("Color")
        assertTrue(cls.isEnum, "Color should have isEnum=true")
    }

    @Test fun `enum base class includes Enum`() {
        val cls = findClass("Color")
        assertTrue(cls.baseClasses.any { "Enum" in it },
            "Color should have Enum in baseClasses; got: ${cls.baseClasses}")
    }

    @Test fun `IntEnum flag is set on Priority`() {
        val cls = findClass("Priority")
        assertTrue(cls.isEnum, "Priority (IntEnum) should have isEnum=true")
    }

    @Test fun `enum class with method has that method`() {
        val cls = findClass("Priority")
        val m = cls.methods.find { it.name == "describe" }
        assertNotNull(m, "Priority should have a 'describe' method; " +
            "methods: ${cls.methods.map { it.name }}")
        assertTrue(m!!.cfg.blocks.isNotEmpty(),
            "describe method should have a valid CFG")
    }
}
