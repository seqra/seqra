package org.opentaint.ir.approximations

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRFieldRef
import org.opentaint.ir.api.cfg.JIRRawAssignInst
import org.opentaint.ir.api.cfg.JIRRawCallInst
import org.opentaint.ir.api.cfg.JIRRawFieldRef
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.findDeclaredFieldOrNull
import org.opentaint.ir.approximation.ApproximationsInstructionsFeature
import org.opentaint.ir.approximation.ApproximationsMappingFeature
import org.opentaint.ir.approximation.ApproximationsMappingFeature.findApproximationByOriginOrNull
import org.opentaint.ir.approximation.ApproximationsMappingFeature.findOriginalByApproximationOrNull
import org.opentaint.ir.approximation.ClassContentApproximationFeature
import org.opentaint.ir.approximation.JIREnrichedVirtualField
import org.opentaint.ir.approximation.JIREnrichedVirtualMethod
import org.opentaint.ir.approximation.toApproximationName
import org.opentaint.ir.approximation.toOriginalName
import org.opentaint.ir.approximations.target.KotlinClass
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.CleanDB
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.allClasspath
import org.opentaint.ir.testing.tests.DatabaseEnvTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(CleanDB::class)
class ApproximationsTest : DatabaseEnvTest() {
    companion object : WithDB(ApproximationsMappingFeature)

    override val cp: JIRClasspath = runBlocking {
        val features = listOf(ClassContentApproximationFeature, ApproximationsInstructionsFeature)
        db.classpath(allClasspath, features)
    }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

    @BeforeEach
    fun waitForParsing() = runBlocking {
        db.awaitBackgroundJobs()
    }

    @Test
    fun testKotlinApproximation() {
        val classec = cp.findClass<KotlinClass>()

        val originalClassName = KotlinClass::class.qualifiedName!!.toOriginalName()
        val approximation = findApproximationByOriginOrNull(originalClassName)

        assertNotNull(approximation)
        assertEquals(classec.name, findOriginalByApproximationOrNull(approximation!!.toApproximationName()))
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun testJavaApproximation() {
        val classec = cp.findClass<Integer>()

        val originalClassName = "java.lang.Integer".toOriginalName()
        val approximation = findApproximationByOriginOrNull(originalClassName)

        assertNotNull(approximation)
        assertEquals(classec.name, findOriginalByApproximationOrNull(approximation!!.toApproximationName()))
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun testIntegerApproximation() {
        val classec = cp.findClass<Integer>()

        val field = classec.findDeclaredFieldOrNull("value")
        assertTrue(field is JIREnrichedVirtualField)

        val method = classec.declaredMethods
            .filter { it.name == "valueOf" }
            .singleOrNull { it is JIREnrichedVirtualMethod }
        assertNotNull(method)
    }

    @Test
    fun testReplacedFields() {
        val classec = cp.findClass<KotlinClass>()
        val fields = classec.declaredFields

        assertTrue(fields.size == 7)

        val (virtualFields, originalFields) = fields.partition { it is JIREnrichedVirtualField }
        val virtualFieldsNames = virtualFields.map { it.name }
        val originalFieldsNames = originalFields.map { it.name }

        assertTrue(virtualFields.size == 4)
        assertTrue("fieldToReplace" in virtualFieldsNames)
        assertTrue("sameApproximation" in virtualFieldsNames)
        assertTrue("anotherApproximation" in virtualFieldsNames)
        assertTrue("artificialField" in virtualFieldsNames)

        assertTrue(originalFields.size == 3)
        assertTrue("sameApproximationTarget" in originalFieldsNames)
        assertTrue("anotherApproximationTarget" in originalFieldsNames)
        assertTrue("fieldWithoutApproximation" in originalFieldsNames)
    }

    @Test
    fun testReplacedMethods() {
        val classec = cp.findClass<KotlinClass>()
        val methods = classec.declaredMethods

        assertTrue(methods.size == 8)

        val (virtualMethods, originalMethods) = methods.partition { it is JIREnrichedVirtualMethod }
        // we can use just names here since we don't have overload in both original and target classes
        val virtualMethodsNames = virtualMethods.map { it.name }
        val originalMethodsNames = originalMethods.map { it.name }

        assertTrue(virtualMethods.size == 7)
        assertTrue("replaceBehaviour" in virtualMethodsNames)
        assertTrue("artificialMethod" in virtualMethodsNames)
        assertTrue("useArtificialField" in virtualMethodsNames)
        assertTrue("useSameApproximationTarget" in virtualMethodsNames)
        assertTrue("useAnotherApproximationTarget" in virtualMethodsNames)
        assertTrue("useFieldWithoutApproximation" in virtualMethodsNames)
        assertTrue("<init>" in virtualMethodsNames)

        assertTrue(originalMethods.size == 1)
        assertTrue("methodWithoutApproximation" in originalMethodsNames)
    }

    @Test
    fun testReplaceApproximationsInMethodBody() {
        val classec = cp.findClass<KotlinClass>()
        val method = classec.declaredMethods.single { it.name == "useSameApproximationTarget" }

        val graph = method.flowGraph()
        val instructions = graph.instructions
        val rawInstructions = method.rawInstList

        assertTrue(method.enclosingClass === classec)
        assertTrue("KotlinClassApprox" !in method.description)

        val types = hashSetOf<String>()

        types += method.returnType.typeName

        val callInsts = instructions.filterIsInstance<JIRCallInst>()
        val assignInsts = instructions.filterIsInstance<JIRAssignInst>()

        val rawCallInsts = rawInstructions.filterIsInstance<JIRRawCallInst>()
        val rawAssignInsts = rawInstructions.filterIsInstance<JIRRawAssignInst>()

        callInsts.forEach { inst ->
            val location = inst.location
            assertTrue(location.method === method)

            val callExpr = inst.callExpr
            types += callExpr.type.typeName
            types += callExpr.method.returnType.typeName
            types += callExpr.method.enclosingType.typeName
            types += callExpr.method.typeArguments.map { it.typeName }
        }

        assignInsts.forEach { inst ->
            val location = inst.location
            assertTrue(location.method === method)

            types += inst.lhv.type.typeName
            val rhv = inst.rhv
            if (rhv is JIRFieldRef) {
                types += rhv.type.typeName
                types += rhv.field.fieldType.typeName
                types += rhv.field.enclosingType.typeName
            }
        }

        rawCallInsts.forEach { inst ->
            val location = inst.owner
            assertTrue(location === method)

            val callExpr = inst.callExpr
            types += callExpr.typeName.typeName
            types += callExpr.returnType.typeName
            types += callExpr.args.map { it.typeName.typeName }
        }

        rawAssignInsts.forEach { inst ->
            val location = inst.owner
            assertTrue(location === method)

            types += inst.lhv.typeName.typeName
            val rhv = inst.rhv
            if (rhv is JIRRawFieldRef) {
                types += rhv.typeName.typeName
                rhv.instance?.typeName?.let { types += it.typeName }
                types += rhv.declaringClass.typeName
            }
        }

        assertTrue(types.none { findOriginalByApproximationOrNull(it.toApproximationName()) != null })
    }
}