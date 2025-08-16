package org.opentaint.ir.impl.storage.ers

import mu.KotlinLogging
import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRDBContext
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.storage.ers.DumpableLoadableEntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.Transaction
import org.opentaint.ir.api.storage.ers.compressed
import org.opentaint.ir.api.storage.ers.findOrNew
import org.opentaint.ir.api.storage.ers.links
import org.opentaint.ir.api.storage.ers.nonSearchable
import org.opentaint.ir.impl.JIRDBSymbolsInternerImpl
import org.opentaint.ir.impl.asSymbolId
import org.opentaint.ir.impl.fs.JavaRuntime
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.fs.info
import org.opentaint.ir.impl.storage.AbstractJIRDbPersistence
import org.opentaint.ir.impl.storage.AnnotationValueKind
import org.opentaint.ir.impl.storage.toJIRDBContext
import org.opentaint.ir.impl.storage.txn
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.AnnotationValue
import org.opentaint.ir.impl.types.AnnotationValueList
import org.opentaint.ir.impl.types.ClassRef
import org.opentaint.ir.impl.types.EnumRef
import org.opentaint.ir.impl.types.PrimitiveValue
import org.opentaint.ir.impl.types.RefKind
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ErsPersistenceImpl(
    javaRuntime: JavaRuntime,
    clearOnStart: Boolean,
    override var ers: EntityRelationshipStorage,
) : AbstractJIRDbPersistence(javaRuntime) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val lock = ReentrantLock(true)

    init {
        if (clearOnStart || !runtimeProcessed) {
            write {
                it.txn.dropAll()
            }
        }
    }

    override val symbolInterner = JIRDBSymbolsInternerImpl().apply { setup(this@ErsPersistenceImpl) }

    override fun setup() {
        /* no-op */
    }

    override fun tryLoad(databaseId: String): Boolean {
        val ers = ers
        if (ers is DumpableLoadableEntityRelationshipStorage) {
            ers.load(databaseId)?.let {
                this.ers = it
                return true
            }
        }
        return false
    }

    override fun <T> read(action: (JIRDBContext) -> T): T {
        return if (ers.isInRam) { // RAM storage doesn't support explicit readonly transactions
            ers.transactionalOptimistic(attempts = 10) { txn ->
                action(toJIRDBContext(txn))
            }
        } else {
            ers.transactional(readonly = true) { txn ->
                action(toJIRDBContext(txn))
            }
        }
    }

    override fun <T> write(action: (JIRDBContext) -> T): T = lock.withLock {
        ers.transactional { txn ->
            action(toJIRDBContext(txn))
        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        if (classes.isEmpty()) {
            return
        }
        val allClasses = classes.map { it.info }
        val locationId = location.id.compressed
        val classEntities = hashMapOf<String, Entity>()
        write { context ->
            val txn = context.txn
            allClasses.forEach { classInfo ->
                txn.newEntity("Class").also { clazz ->
                    classEntities[classInfo.name] = clazz
                    clazz["nameId"] = classInfo.name.asSymbolId(symbolInterner).compressed
                    clazz["access"] = classInfo.access.compressed.nonSearchable
                    classInfo.signature?.let { signature ->
                        clazz["signature"] = signature.nonSearchable
                    }
                    val fields = links(clazz, "fields")
                    classInfo.fields.forEach { fieldInfo ->
                        txn.newEntity("Field").also { field ->
                            fields += field
                            field["access"] = fieldInfo.access.compressed.nonSearchable
                            field["nameId"] = fieldInfo.name.asSymbolId(symbolInterner).compressed
                            fieldInfo.signature?.let { signature ->
                                field["signature"] = signature.asSymbolId(symbolInterner).compressed.nonSearchable
                            }
                            field["typeId"] = fieldInfo.type.asSymbolId(symbolInterner).compressed.nonSearchable
                            fieldInfo.annotations.forEach { annotationInfo ->
                                annotationInfo.save(txn, clazz, RefKind.FIELD)
                            }
                        }
                    }
                    val methods = links(clazz, "methods")
                    classInfo.methods.forEach { methodInfo ->
                        txn.newEntity("Method").also { method ->
                            methods += method
                            method["access"] = methodInfo.access.compressed.nonSearchable
                            method["nameId"] = methodInfo.name.asSymbolId(symbolInterner).compressed
                            methodInfo.signature?.let { signature ->
                                method["signature"] = signature.asSymbolId(symbolInterner).compressed.nonSearchable
                            }
                            method["desc"] = methodInfo.desc.asSymbolId(symbolInterner).compressed.nonSearchable
                            method["returnClass"] =
                                methodInfo.returnClass.asSymbolId(symbolInterner).compressed.nonSearchable
                            methodInfo.annotations.forEach { annotationInfo ->
                                annotationInfo.save(txn, clazz, RefKind.METHOD)
                            }
                            val parameters = links(method, "parameters")
                            methodInfo.parametersInfo.forEach { parameterInfo ->
                                txn.newEntity("Parameter").also { parameter ->
                                    parameters += parameter
                                    parameter["access"] = parameterInfo.access.compressed.nonSearchable
                                    parameter["index"] = parameterInfo.index.compressed.nonSearchable
                                    parameterInfo.name?.let { parameterName ->
                                        parameter["nameId"] =
                                            parameterName.asSymbolId(symbolInterner).compressed.nonSearchable
                                    }
                                    parameter["typeId"] =
                                        parameterInfo.type.asSymbolId(symbolInterner).compressed.nonSearchable
                                    parameterInfo.annotations.forEach { annotationInfo ->
                                        annotationInfo.save(txn, clazz, RefKind.PARAM)
                                    }
                                }
                            }
                        }
                    }
                    val innerClasses = links(clazz, "innerClasses")
                    classInfo.innerClasses.forEach { innerClassName ->
                        txn.newEntity("InnerClass").also { innerClass ->
                            innerClasses += innerClass
                            innerClass["nameId"] = innerClassName.asSymbolId(symbolInterner).compressed.nonSearchable
                        }
                    }
                }
            }
            allClasses.forEach { classInfo ->
                classEntities[classInfo.name]?.let { clazz ->
                    clazz["locationId"] = locationId
                }
            }
            allClasses.forEach { classInfo ->
                classEntities[classInfo.name]?.setRawBlob("bytecode", classInfo.bytecode)
            }
            allClasses.forEach { classInfo ->
                classEntities[classInfo.name]?.let { clazz ->
                    clazz["packageId"] =
                        classInfo.name.substringBeforeLast('.').asSymbolId(symbolInterner).compressed.nonSearchable
                }
            }
            allClasses.forEach { classInfo ->
                if (classInfo.superClass != null) {
                    classEntities[classInfo.name]?.let { clazz ->
                        classInfo.superClass.takeIf { JAVA_OBJECT != it }?.let { superClassName ->
                            clazz["inherits"] = superClassName.asSymbolId(symbolInterner).compressed
                        }
                    }
                }
            }
            allClasses.forEach { classInfo ->
                if (classInfo.interfaces.isNotEmpty()) {
                    classEntities[classInfo.name]?.let { clazz ->
                        val implements = links(clazz, "implements")
                        classInfo.interfaces.forEach { interfaceName ->
                            txn.findOrNew("Interface", "nameId", interfaceName.asSymbolId(symbolInterner).compressed)
                                .also { interfaceClass ->
                                    implements += interfaceClass
                                    links(interfaceClass, "implementedBy") += clazz
                                }
                        }
                    }
                }
            }
            allClasses.forEach { classInfo ->
                classInfo.annotations.forEach { annotationInfo ->
                    annotationInfo.save(txn, classEntities[classInfo.name]!!, RefKind.CLASS)
                }
            }
            symbolInterner.flush(context)
        }
    }

    override fun findClassSourceByName(cp: JIRClasspath, fullName: String): ClassSource? {
        return read { context ->
            findClassSourcesImpl(context, cp, fullName).firstOrNull()
        }
    }

    override fun findClassSources(db: JIRDatabase, location: RegisteredLocation): List<ClassSource> {
        return read { context ->
            context.txn.find("Class", "locationId", location.id.compressed).map {
                it.toClassSource(db, findSymbolName(it.getCompressed<Long>("nameId") ?: throw NullPointerException()))
            }.toList()
        }
    }

    override fun findClassSources(cp: JIRClasspath, fullName: String): List<ClassSource> {
        return read { context ->
            findClassSourcesImpl(context, cp, fullName).toList()
        }
    }

    override fun setImmutable(databaseId: String) {
        ers = ers.asImmutable(databaseId)
    }

    override fun close() {
        try {
            ers.close()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to close ERS persistence" }
        }
    }

    private fun findClassSourcesImpl(context: JIRDBContext, cp: JIRClasspath, fullName: String): Sequence<ClassSource> {
        val ids = cp.registeredLocationIds
        return context.txn.find("Class", "nameId", findSymbolId(fullName).compressed)
            .filter { it.getCompressed<Long>("locationId") in ids }
            .map { it.toClassSource(cp.db, fullName) }
    }

    private fun AnnotationInfo.save(txn: Transaction, ref: Entity, refKind: RefKind): Entity {
        return txn.newEntity("Annotation").also { annotation ->
            annotation["nameId"] = className.asSymbolId(symbolInterner).compressed
            annotation["visible"] = visible.nonSearchable
            typeRef?.let { typeRef ->
                annotation["typeRef"] = typeRef.nonSearchable
            }
            typePath?.let { typePath ->
                annotation["typePath"] = typePath.nonSearchable
            }
            links(annotation, "ref") += ref
            annotation["refKind"] = refKind.ordinal.compressed.nonSearchable

            if (values.isNotEmpty()) {
                val flatValues = mutableListOf<Pair<String, AnnotationValue>>()
                values.forEach { (name, value) ->
                    if (value !is AnnotationValueList) {
                        flatValues.add(name to value)
                    } else {
                        value.annotations.forEach { flatValues.add(name to it) }
                    }
                }

                val valueLinks = links(annotation, "values")
                flatValues.forEach { (name, value) ->
                    txn.newEntity("AnnotationValue").also { annotationValue ->
                        annotationValue["nameId"] = name.asSymbolId(symbolInterner).compressed.nonSearchable
                        valueLinks += annotationValue
                        when (value) {
                            is ClassRef -> {
                                annotationValue["classSymbolId"] =
                                    value.className.asSymbolId(symbolInterner).compressed.nonSearchable
                            }

                            is EnumRef -> {
                                annotationValue["classSymbolId"] =
                                    value.className.asSymbolId(symbolInterner).compressed.nonSearchable
                                annotationValue["enumSymbolId"] =
                                    value.enumName.asSymbolId(symbolInterner).compressed.nonSearchable
                            }

                            is PrimitiveValue -> {
                                annotationValue["primitiveValueType"] = value.dataType.ordinal.compressed.nonSearchable
                                annotationValue["primitiveValue"] =
                                    AnnotationValueKind.serialize(value.value)
                                        .asSymbolId(symbolInterner).compressed.nonSearchable
                            }

                            is AnnotationInfo -> {
                                val refAnnotation = value.save(txn, ref, refKind)
                                links(annotationValue, "refAnnotation") += refAnnotation
                            }

                            else -> {} // do nothing as annotation values are flattened
                        }
                    }
                }
            }
        }
    }
}

private fun Entity.toClassSource(db: JIRDatabase, fullName: String) =
    PersistenceClassSource(
        db = db,
        className = fullName,
        classId = id.instanceId,
        locationId = getCompressed<Long>("locationId") ?: throw NullPointerException("locationId property isn't set"),
        cachedByteCode = getRawBlob("bytecode")
    )
