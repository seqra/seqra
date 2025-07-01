package org.opentaint.ir.impl.storage

import mu.KLogging
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.asSymbolId
import org.opentaint.ir.impl.storage.AppVersion.Companion.currentAppVersion
import org.opentaint.ir.impl.storage.jooq.tables.references.ANNOTATIONS
import org.opentaint.ir.impl.storage.jooq.tables.references.ANNOTATIONVALUES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSINNERCLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.FIELDS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODS
import org.opentaint.ir.impl.storage.jooq.tables.references.OUTERCLASSES
import org.opentaint.ir.impl.types.AnnotationCollector
import org.opentaint.ir.impl.types.ClassInfo
import org.opentaint.ir.impl.types.ClassRefCollector
import org.opentaint.ir.impl.types.FieldCollector
import org.opentaint.ir.impl.types.LongRef
import org.opentaint.ir.impl.types.MethodCollector
import org.opentaint.ir.impl.types.MethodParamCollector
import org.opentaint.ir.impl.types.RefKind
import org.opentaint.ir.impl.types.collectSymbols
import org.jooq.TableField
import java.sql.Types

class SQLitePersistenceService(private val persistence: SQLitePersistenceImpl) {

    companion object : KLogging()

    private val refactorings = JIRRefactoringChain(
        listOf(
            AddAppMetadataAndRefactoring(),
            UpdateUsageAndBuildersSchemeRefactoring()
        )
    )

    private lateinit var version: AppVersion

    private var classIdCount = 0L
    private val methodIdGen = LongRef()
    private val fieldIdGen = LongRef()
    private var methodParamIdCount = 0L
    private val annotationIdGen = LongRef()
    private val annotationValueIdGen = LongRef()
    private var outerClassIdCount = 0L

    fun setup() {
        persistence.read { context ->
            logger.info("Starting app version $currentAppVersion")
            version = AppVersion.read(context)
            classIdCount = CLASSES.ID.maxId ?: 0L
            methodIdGen.set(METHODS.ID.maxId ?: 0)
            fieldIdGen.set(FIELDS.ID.maxId ?: 0)
            methodParamIdCount = METHODPARAMETERS.ID.maxId ?: 0L
            annotationIdGen.set(ANNOTATIONS.ID.maxId ?: 0)
            annotationValueIdGen.set(ANNOTATIONVALUES.ID.maxId ?: 0)
            outerClassIdCount = OUTERCLASSES.ID.maxId ?: 0L
        }
        when {
            version > currentAppVersion -> throw IllegalStateException("Can't start $currentAppVersion on $version database")
            version == currentAppVersion -> {}
            else -> persistence.write { context ->
                refactorings.execute(context)
            }
        }
        persistence.write { context ->
            currentAppVersion.write(context)
        }
    }

    fun persist(location: RegisteredLocation, classes: List<ClassInfo>) = synchronized(this) {
        val symbolInterner = persistence.symbolInterner
        val annotationCollector = AnnotationCollector(annotationIdGen, annotationValueIdGen, symbolInterner)
        val fieldCollector = FieldCollector(fieldIdGen)
        val classRefCollector = ClassRefCollector()
        val paramCollector = MethodParamCollector()
        val methodCollector = MethodCollector(methodIdGen, paramCollector)
        collectSymbols(classes).forEach { it.asSymbolId(symbolInterner) }
        val locationId = location.id
        classes.forEachIndexed { i, classInfo ->
            val storedClassId = classIdCount + i + 1
            if (classInfo.interfaces.isNotEmpty()) {
                classInfo.interfaces.forEach {
                    classRefCollector.collectParent(storedClassId, it, isClass = false)
                }
            }
            classRefCollector.collectParent(storedClassId, classInfo.superClass, isClass = true)
            if (classInfo.innerClasses.isNotEmpty()) {
                classInfo.innerClasses.forEach {
                    classRefCollector.collectInnerClass(storedClassId, it)
                }
            }
            classInfo.methods.forEach {
                methodCollector.collect(storedClassId, it)
            }
            classInfo.fields.forEach {
                fieldCollector.collect(storedClassId, it)
            }
        }
        persistence.write { context ->
            val jooq = context.dslContext
            jooq.withoutAutoCommit { conn ->
                symbolInterner.flush(toJIRDBContext(jooq, conn))
                conn.insertElements(CLASSES, classes) { classInfo ->
                    val id = ++classIdCount
                    val packageName = classInfo.name.substringBeforeLast('.')
                    val pack = packageName.asSymbolId(symbolInterner)
                    setLong(1, id)
                    setInt(2, classInfo.access)
                    setLong(3, classInfo.name.asSymbolId(symbolInterner))
                    setString(4, classInfo.signature)
                    setBytes(5, classInfo.bytecode)
                    setLong(6, locationId)
                    setLong(7, pack)
                    setNull(8, Types.BIGINT)
                    setNull(9, Types.BIGINT)
                    annotationCollector.collect(classInfo.annotations, id, RefKind.CLASS)
                }
                conn.insertElements(METHODS, methodCollector.methods) { (classId, methodId, method) ->
                    setLong(1, methodId)
                    setInt(2, method.access)
                    setLong(3, method.name.asSymbolId(symbolInterner))
                    setString(4, method.signature)
                    setString(5, method.desc)
                    setLong(6, method.returnClass.asSymbolId(symbolInterner))
                    setLong(7, classId)
                    annotationCollector.collect(method.annotations, methodId, RefKind.METHOD)
                }

                conn.insertElements(METHODPARAMETERS, paramCollector.params) { (methodId, param) ->
                    val paramId = ++methodParamIdCount
                    setLong(1, paramId)
                    setInt(2, param.access)
                    setInt(3, param.index)
                    setString(4, param.name)
                    setLong(5, param.type.asSymbolId(symbolInterner))
                    setLong(6, methodId)
                    annotationCollector.collect(param.annotations, paramId, RefKind.PARAM)
                }

                conn.insertElements(
                    CLASSINNERCLASSES,
                    classRefCollector.innerClasses,
                    autoIncrementId = true
                ) { (classId, innerClass) ->
                    setLong(1, classId)
                    setLong(2, innerClass.asSymbolId(symbolInterner))
                }
                conn.insertElements(
                    CLASSHIERARCHIES,
                    classRefCollector.superClasses,
                    autoIncrementId = true
                ) { superClass ->
                    setLong(1, superClass.first)
                    setLong(2, superClass.second.asSymbolId(symbolInterner))
                    setBoolean(3, superClass.third)
                }

                conn.insertElements(FIELDS, fieldCollector.fields) { (classId, fieldId, fieldInfo) ->
                    setLong(1, fieldId)
                    setInt(2, fieldInfo.access)
                    setLong(3, fieldInfo.name.asSymbolId(symbolInterner))
                    setString(4, fieldInfo.signature)
                    setLong(5, fieldInfo.type.asSymbolId(symbolInterner))
                    setLong(6, classId)
                    annotationCollector.collect(fieldInfo.annotations, fieldId, RefKind.FIELD)
                }

                conn.insertElements(ANNOTATIONS, annotationCollector.collected) { annotation ->
                    setLong(1, annotation.id)
                    setLong(2, annotation.info.className.asSymbolId(symbolInterner))
                    setBoolean(3, annotation.info.visible)
                    setNullableLong(4, annotation.info.typeRef?.toLong())
                    setString(5, annotation.info.typePath)
                    setNullableLong(6, annotation.parentId)

                    setNullableLong(7, annotation.refId.takeIf { annotation.refKind == RefKind.CLASS })
                    setNullableLong(8, annotation.refId.takeIf { annotation.refKind == RefKind.METHOD })
                    setNullableLong(9, annotation.refId.takeIf { annotation.refKind == RefKind.FIELD })
                    setNullableLong(10, annotation.refId.takeIf { annotation.refKind == RefKind.PARAM })
                }

                conn.insertElements(ANNOTATIONVALUES, annotationCollector.collectedValues) { value ->
                    setLong(1, value.id)
                    setLong(2, value.annotationId)
                    setString(3, value.name)
                    setNullableLong(4, value.refAnnotationId)
                    if (value.primitiveValueType != null) {
                        setInt(5, value.primitiveValueType.ordinal)
                        setString(6, value.primitiveValue)
                    } else {
                        setNull(5, Types.INTEGER)
                        setNull(6, Types.VARCHAR)
                    }
                    setNullableLong(7, value.classSymbolId)
                    setNullableLong(8, value.enumSymbolId)
                }

                conn.insertElements(OUTERCLASSES, classes.filter { it.outerClass != null }) { classInfo ->
                    val outerClass = classInfo.outerClass!!
                    val outerClassId = outerClass.className.asSymbolId(symbolInterner)
                    setLong(1, ++outerClassIdCount)
                    setLong(2, outerClassId)
                    setString(3, outerClass.name)
                    setString(4, classInfo.outerMethod)
                    setString(5, classInfo.outerMethodDesc)
                }
            }
        }
    }

    private val TableField<*, Long?>.maxId: Long?
        get() {
            return maxId(persistence.jooq)
        }
}