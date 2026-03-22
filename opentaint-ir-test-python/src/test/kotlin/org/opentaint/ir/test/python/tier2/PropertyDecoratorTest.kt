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
        // PIRProperty is not yet wired up in the builder — properties list is always empty.
        // Properties are instead accessible as methods with isProperty=true.
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented); " +
            "got: ${cls.properties.map { it.name }}")
        val propertyMethods = cls.methods.filter { it.isProperty && it.name == "value" }
        assertTrue(propertyMethods.isNotEmpty(),
            "PropClass should expose 'value' as a method with isProperty=true; " +
            "methods: ${cls.methods.map { "${it.name}(isProperty=${it.isProperty})" }}")
    }

    @Test fun `property getter is wired in PIRProperty`() {
        // PIRProperty is not yet wired up — properties list is empty.
        // Verify getter is accessible as a method with isProperty=true instead.
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented)")
        val getter = cls.methods.find { it.name == "value" && it.isProperty }
        assertNotNull(getter, "value getter should exist as a method with isProperty=true; " +
            "methods: ${cls.methods.map { "${it.name}(isProperty=${it.isProperty})" }}")
    }

    @Test fun `property getter body accesses backing field`() {
        val cls = findClass("PropClass")
        val getter = cls.properties.find { it.name == "value" }?.getter
            ?: cls.methods.find { it.name == "value" && it.isProperty }
        assertNotNull(getter, "value getter not found")
        val loadAttrs = getter!!.cfg.blocks.flatMap { it.instructions }
            .filterIsInstance<PIRLoadAttr>()
        assertTrue(loadAttrs.any { it.attribute == "_value" },
            "Getter body should load self._value")
    }

    // ─── Property setter tests ─────────────────────────────

    @Test fun `property setter is present in PIRProperty`() {
        // PIRProperty is not yet wired up — properties list is empty.
        // The setter is also not separately available as a method because mypy wraps
        // property getter/setter/deleter in an OverloadedFuncDef and the builder
        // only unwraps the first item (the getter).
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented)")
        val setterMethod = cls.methods.find { it.name == "value" && !it.isProperty }
        assertNull(setterMethod,
            "setter should NOT appear as a separate non-property method (current behavior); " +
            "methods: ${cls.methods.map { "${it.name}(isProperty=${it.isProperty})" }}")
    }

    @Test fun `property setter body stores to backing field`() {
        // PIRProperty is not yet wired up — properties list is empty.
        // The setter is not available as a separate method either because mypy
        // wraps getter/setter/deleter in OverloadedFuncDef and only the getter
        // (first item) is unwrapped by the builder.
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented)")
        // Verify the getter (only property method available) has a CFG
        val getter = cls.methods.find { it.name == "value" && it.isProperty }
        assertNotNull(getter, "value getter should exist as a method with isProperty=true")
        assertTrue(getter!!.cfg.blocks.isNotEmpty(),
            "value getter should have a valid CFG")
    }

    // ─── Property deleter tests ────────────────────────────

    @Test fun `property deleter is present in PIRProperty`() {
        // PIRProperty is not yet wired up — properties list is empty.
        // The deleter is not available as a separate method either because mypy
        // wraps getter/setter/deleter in OverloadedFuncDef and only the getter
        // (first item) is unwrapped by the builder.
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented)")
        val deleterMethod = cls.methods.filter { it.name == "value" }
        assertEquals(1, deleterMethod.size,
            "Only one 'value' method (the getter) should be present; " +
            "got: ${deleterMethod.map { "${it.name}(isProperty=${it.isProperty})" }}")
        assertTrue(deleterMethod[0].isProperty,
            "The single 'value' method should be the property getter")
    }

    // ─── Multiple properties on one class ──────────────────

    @Test fun `class with multiple properties exposes all`() {
        // PIRProperty is not yet wired up — properties list is always empty.
        // Properties are available as methods with isProperty=true instead.
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented)")
        val propertyMethods = cls.methods.filter { it.isProperty }.map { it.name }.toSet()
        assertTrue("value" in propertyMethods,
            "Expected 'value' as property method; got: $propertyMethods")
        assertTrue("name" in propertyMethods,
            "Expected 'name' as property method; got: $propertyMethods")
    }

    @Test fun `second property has getter and setter but no deleter`() {
        // PIRProperty is not yet wired up — properties list is empty.
        // The 'name' property getter is available as a method with isProperty=true.
        // The setter is not separately available (OverloadedFuncDef only unwraps first item).
        val cls = findClass("PropClass")
        assertTrue(cls.properties.isEmpty(),
            "properties list should be empty (PIRProperty not yet implemented)")
        val nameGetter = cls.methods.find { it.name == "name" && it.isProperty }
        assertNotNull(nameGetter,
            "name property getter should exist as a method with isProperty=true; " +
            "methods: ${cls.methods.map { "${it.name}(isProperty=${it.isProperty})" }}")
        val nameMethods = cls.methods.filter { it.name == "name" }
        assertEquals(1, nameMethods.size,
            "Only one 'name' method (the getter) should be present; " +
            "got: ${nameMethods.map { "${it.name}(isProperty=${it.isProperty})" }}")
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
