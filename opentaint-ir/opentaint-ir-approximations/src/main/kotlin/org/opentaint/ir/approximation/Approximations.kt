package org.opentaint.ir.approximation

import org.opentaint.ir.api.jvm.ByteCodeIndexer
import org.opentaint.ir.api.jvm.JIRDBContext
import org.opentaint.ir.api.jvm.JIRClassExtFeature
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRSignal
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.storage.ers.compressed
import org.opentaint.ir.approximation.TransformerIntoVirtual.transformMethodIntoVirtual
import org.opentaint.ir.approximation.annotation.Approximate
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.execute
import org.opentaint.ir.impl.storage.jooq.tables.references.ANNOTATIONS
import org.opentaint.ir.impl.storage.jooq.tables.references.ANNOTATIONVALUES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.types.RefKind
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

// TODO we need somehow to remove approximation classes from the hierarchy to avoid presence of them in the inheritors of Object

/**
 *  A feature building mapping between so-called approximations and their targets.
 *  Other features like [ClassContentApproximationFeature] and [ApproximationsInstructionsFeature]
 *  uses it for modifying source code of target classes with information from
 *  the corresponding approximations.
 *
 *  Note: for correct work of the feature, you must wait
 *  till the whole processing of classpath is finished,
 *  otherwise you might get incomplete mapping.
 *  See [JIRDatabase.awaitBackgroundJobs].
 */
object Approximations : JIRFeature<Any?, Any?>, JIRClassExtFeature, JIRInstExtFeature {

    private val originalToApproximation: ConcurrentMap<OriginalClassName, ApproximationClassName> = ConcurrentHashMap()
    private val approximationToOriginal: ConcurrentMap<ApproximationClassName, OriginalClassName> = ConcurrentHashMap()

    override suspend fun query(classpath: JIRClasspath, req: Any?): Sequence<Any?> {
        // returns an empty sequence for now, all requests are made using
        // findApproximationOrNull and findOriginalByApproximation functions
        return emptySequence()
    }

    override fun newIndexer(
        jIRdb: JIRDatabase,
        location: RegisteredLocation
    ): ByteCodeIndexer = ApproximationIndexer(originalToApproximation, approximationToOriginal)

    override fun onSignal(signal: JIRSignal) {
        if (signal is JIRSignal.BeforeIndexing) {
            val persistence = signal.jIRdb.persistence
            val approxSymbol = persistence.findSymbolId(approximationAnnotationClassName)
            persistence.read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.select(CLASSES.NAME, ANNOTATIONVALUES.CLASS_SYMBOL)
                            .from(ANNOTATIONS)
                            .join(CLASSES).on(ANNOTATIONS.CLASS_ID.eq(CLASSES.ID))
                            .join(ANNOTATIONVALUES).on(ANNOTATIONVALUES.ANNOTATION_ID.eq(ANNOTATIONS.ID))
                            .where(
                                ANNOTATIONS.ANNOTATION_NAME.eq(approxSymbol).and(
                                    ANNOTATIONVALUES.NAME.eq("value")
                                )
                            )
                            .fetch().asSequence().map { record -> record.value1() to record.value2() }
                    },
                    noSqlAction = { txn ->
                        val valueId = persistence.findSymbolId("value")
                        txn.find("Annotation", "nameId", approxSymbol.compressed)
                            .filter { it.getCompressedBlob<Int>("refKind") == RefKind.CLASS.ordinal }
                            .flatMap { annotation ->
                                annotation.getLink("ref").let { clazz ->
                                    annotation.getLinks("values").map { clazz to it }
                                }
                            }.filter { (_, annotationValue) ->
                                valueId == annotationValue["nameId"]
                            }.map { (clazz, annotationValue) ->
                                clazz.getCompressed<Long>("nameId") to annotationValue.getCompressedBlob<Long>("classSymbolId")
                            }
                    }
                ).forEach { (approximation, original) ->
                    val approximationClassName = persistence.findSymbolName(approximation!!).toApproximationName()
                    val originalClassName = persistence.findSymbolName(original!!).toOriginalName()
                    originalToApproximation[originalClassName] = approximationClassName
                    approximationToOriginal[approximationClassName] = originalClassName
                }
            }
        }
    }

    /**
     * Returns a list of [JIREnrichedVirtualField] if there is an approximation for [clazz] and null otherwise.
     */
    override fun fieldsOf(clazz: JIRClassOrInterface): List<JIRField>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredFields.map { TransformerIntoVirtual.transformIntoVirtualField(clazz, it) }
    }

    /**
     * Returns a list of [JIREnrichedVirtualMethod] if there is an approximation for [clazz] and null otherwise.
     */
    override fun methodsOf(clazz: JIRClassOrInterface): List<JIRMethod>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredMethods.map {
            approximationClass.classpath.transformMethodIntoVirtual(clazz, it)
        }
    }

    override fun transformRawInstList(method: JIRMethod, list: JIRInstList<JIRRawInst>): JIRInstList<JIRRawInst> {
        return JIRInstListImpl(list.map { it.accept(InstSubstitutorForApproximations) })
    }

    /**
     * Returns a name of the approximation class for [className] if it exists and null otherwise.
     */
    fun findApproximationByOriginOrNull(
        className: OriginalClassName
    ): String? = originalToApproximation[className]?.className

    /**
     * Returns a name of the target class for [className] approximation if it exists and null otherwise.
     */
    fun findOriginalByApproximationOrNull(
        className: ApproximationClassName
    ): String? = approximationToOriginal[className]?.className
}

private class ApproximationIndexer(
    private val originalToApproximation: ConcurrentMap<OriginalClassName, ApproximationClassName>,
    private val approximationToOriginal: ConcurrentMap<ApproximationClassName, OriginalClassName>
) : ByteCodeIndexer {
    override fun index(classNode: ClassNode) {
        val annotations = classNode.visibleAnnotations ?: return

        // Check whether the classNode contains an approximation related annotation
        val approximationAnnotation = annotations.singleOrNull {
            approximationAnnotationClassName in it.desc.className
        } ?: return

        // Extract a name of the target class for this approximation
        val target = approximationAnnotation.values.filterIsInstance<org.objectweb.asm.Type>().single()

        val originalClassName = target.className.toOriginalName()
        val approximationClassName = classNode.name.className.toApproximationName()

        // Ensure that each approximation has one and only one
        require(originalClassName !in originalToApproximation) {
            "An error occurred during approximations indexing: you tried to add `$approximationClassName` " +
                    "as an approximation for `$originalClassName`, but the target class is already " +
                    "associated with approximation `${originalToApproximation[originalClassName]}`. " +
                    "Only bijection between classes is allowed."
        }
        require(approximationClassName !in approximationToOriginal) {
            "An error occurred during approximations indexing: you tried to add `$approximationClassName` " +
                    "as an approximation for `$originalClassName`, but this approximation is already used for " +
                    "`${approximationToOriginal[approximationClassName]}`. " +
                    "Only bijection between classes is allowed."
        }

        originalToApproximation[originalClassName] = approximationClassName
        approximationToOriginal[approximationClassName] = originalClassName
    }

    override fun flush(context: JIRDBContext) {
        // do nothing
    }
}

private val approximationAnnotationClassName = Approximate::class.qualifiedName!!

@JvmInline
value class ApproximationClassName(val className: String) {
    override fun toString(): String = className
}

@JvmInline
value class OriginalClassName(val className: String) {
    override fun toString(): String = className
}
