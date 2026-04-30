package org.opentaint.dataflow.jvm.ap.ifds

import org.objectweb.asm.tree.ClassNode
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.ir.api.jvm.ByteCodeIndexer
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRSignal
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.api.storage.SymbolInterner
import org.opentaint.ir.api.storage.asSymbolId
import org.opentaint.ir.impl.storage.txn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.opentaint.dataflow.ap.ifds.ValueAccessor

class JIRSummariesFeature(
    apMode: ApMode,
    private val updateExistingSummaries: Boolean = true
) : JIRFeature<Any?, Any?> {
    private lateinit var jIRdb: JIRDatabase
    private val interner: SymbolInterner by lazy {
        jIRdb.persistence.symbolInterner
    }

    private val methodToIdCache = ConcurrentHashMap<JIRMethod, Long>()
    private val idToMethodCache = ConcurrentHashMap<Long, JIRMethod>()

    private val accessorToIdCache = ConcurrentHashMap<Accessor, Long>()
    private val idToAccessorCache = ConcurrentHashMap<Long, Accessor>()

    private val methodIdGen = AtomicLong()
    private val newMethods = ConcurrentHashMap.newKeySet<JIRMethod>()

    private val accessorIdGen = AtomicLong()
    private val newAccessors = ConcurrentHashMap.newKeySet<Accessor>()

    private val summariesCache = ConcurrentHashMap<JIRMethod, ByteArray>()

    private val apModeId = apMode.ordinal

    override suspend fun query(classpath: JIRClasspath, req: Any?): Sequence<Any?> {
        return emptySequence()
    }

    override fun newIndexer(jIRdb: JIRDatabase, location: RegisteredLocation): ByteCodeIndexer {
        // Don't need to index anything => returning dummy indexer here
        return object : ByteCodeIndexer {
            override fun flush(context: StorageContext) = Unit
            override fun index(classNode: ClassNode) = Unit
        }
    }

    private fun String.toMaxIdEntityType(): String {
        return "${this}MaxValue"
    }

    private fun getMaxId(type: String): Long? {
        return jIRdb.persistence.read { context ->
            context.txn.all(type.toMaxIdEntityType())
                .singleOrNull()
                ?.get<Long>("value")
        }
    }

    private fun flushMaxId(type: String, value: Long) {
        jIRdb.persistence.write { context ->
            val entity = context.txn.all(type.toMaxIdEntityType()).singleOrNull()
                ?: context.txn.newEntity(type.toMaxIdEntityType())

            entity["value"] = value
        }
    }

    private fun checkJIRdb(db: JIRDatabase) {
        check(db == jIRdb) {
            "Unexpected query with jIRdb not equal to cached one"
        }
    }

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                jIRdb = signal.jIRdb

                methodIdGen.set(getMaxId(METHOD_IDS_TYPE) ?: -1L)
                accessorIdGen.set(getMaxId(ACCESSOR_IDS_TYPE) ?: MAX_RESERVED_ACCESSOR_ID)
            }
            is JIRSignal.Closed -> {
                checkJIRdb(signal.jIRdb)
                flush()
            }
            else -> Unit
        }
    }

    fun getMethodById(id: Long, cp: JIRClasspath): JIRMethod {
        checkJIRdb(cp.db)

        return idToMethodCache.computeIfAbsent(id) {
            val (classNameId, methodNameId, methodDescId) = jIRdb.persistence.read { context ->
                val methodEntry = context.txn.find(METHOD_IDS_TYPE, "id", id)
                    .singleOrNull() ?: error("Deserialization error. Unknown method id: $id")

                val classNameId = methodEntry.get<Long>("classNameId")
                val methodNameId = methodEntry.get<Long>("methodNameId")
                val methodDescId = methodEntry.get<Long>("methodDescId")
                Triple(classNameId, methodNameId, methodDescId)
            }

            checkNotNull(classNameId) { "Expected non-null classNameId" }
            checkNotNull(methodNameId) { "Expected non-null methodNameId" }
            checkNotNull(methodDescId) { "Expected non-null methodDescId" }

            val className = findSymbolName(classNameId, symbolType = "className")
            val methodName = findSymbolName(methodNameId, symbolType = "methodName")
            val methodDesc = findSymbolName(methodDescId, symbolType = "methodDesc")

            cp.findClass(className).findMethodOrNull(methodName, methodDesc)
                ?: error("Deserialization error: can't find method $className.$methodName($methodDesc) in classpath")
        }
    }

    fun getIdByMethod(method: JIRMethod): Long {
        return methodToIdCache.computeIfAbsent(method) {
            val classNameId = method.enclosingClass.name.asSymbolId(interner)
            val methodNameId = method.name.asSymbolId(interner)
            val methodDescId = method.description.asSymbolId(interner)

            val methodId = jIRdb.persistence.read { context ->
                context.txn.find(METHOD_IDS_TYPE, "classNameId", classNameId)
                    .filter { it.get<Long>("methodNameId") == methodNameId }
                    .filter { it.get<Long>("methodDescId") == methodDescId }
                    .singleOrNull()
                    ?.get<Long>("id")
            }

            methodId ?: methodIdGen.incrementAndGet().also {
                newMethods.add(method)
            }
        }
    }

    fun getAccessorById(id: Long): Accessor {
        return when (id) {
            ANY_ACCESSOR_ID -> AnyAccessor
            FINAL_ACCESSOR_ID -> FinalAccessor
            ELEMENT_ACCESSOR_ID -> ElementAccessor
            VALUE_ACCESSOR_ID -> ValueAccessor
            TYPE_INFO_GROUP_ACCESSOR_ID -> TypeInfoGroupAccessor
            else -> {
                idToAccessorCache.computeIfAbsent(id) {
                    val ids = jIRdb.persistence.read { context ->
                        val accessorEntity = context.txn.find(ACCESSOR_IDS_TYPE, "id", id)
                            .singleOrNull() ?: error("Deserialization error. Unknown accessor with id: $id")
                        AccessorIds(
                            classNameId = accessorEntity.get<Long>("classNameId"),
                            fieldNameId = accessorEntity.get<Long>("fieldNameId"),
                            fieldTypeId = accessorEntity.get<Long>("fieldTypeId"),
                            taintMarkId = accessorEntity.get<Long>("taintMarkId"),
                            staticTypeNameId = accessorEntity.get<Long>("staticTypeNameId"),
                            typeInfoTypeNameId = accessorEntity.get<Long>("typeInfoTypeNameId"),
                        )
                    }

                    if (ids.classNameId != null) {
                        checkNotNull(ids.fieldNameId) { "Expected non-null fieldNameId" }
                        checkNotNull(ids.fieldTypeId) { "Expected non-null fieldTypeId" }

                        val className = findSymbolName(ids.classNameId, symbolType = "className")
                        val fieldName = findSymbolName(ids.fieldNameId, symbolType = "fieldName")
                        val fieldType = findSymbolName(ids.fieldTypeId, symbolType = "fieldType")
                        FieldAccessor(className, fieldName, fieldType)
                    } else if (ids.staticTypeNameId != null) {
                        val typeName = interner.findSymbolName(ids.staticTypeNameId)
                            ?: error("Deserialization error. Unknown typeName id: $id")
                        ClassStaticAccessor(typeName)
                    } else if (ids.typeInfoTypeNameId != null) {
                        val typeName = interner.findSymbolName(ids.typeInfoTypeNameId)
                            ?: error("Deserialization error. Unknown typeName id: $id")
                        TypeInfoAccessor(typeName)
                    } else {
                        checkNotNull(ids.taintMarkId) { "Expected non-null taintMarkId" }

                        val taintMarkName = interner.findSymbolName(ids.taintMarkId)
                            ?: error("Deserialization error. Unknown taintMark id: $id")
                        TaintMarkAccessor(taintMarkName)
                    }
                }
            }
        }
    }

    fun getIdByAccessor(accessor: Accessor): Long {
        return when (accessor) {
            AnyAccessor -> ANY_ACCESSOR_ID
            ElementAccessor -> ELEMENT_ACCESSOR_ID
            FinalAccessor -> FINAL_ACCESSOR_ID
            ValueAccessor -> VALUE_ACCESSOR_ID
            TypeInfoGroupAccessor -> TYPE_INFO_GROUP_ACCESSOR_ID

            is FieldAccessor -> accessorToIdCache.computeIfAbsent(accessor) {
                val classNameId = accessor.className.asSymbolId(interner)
                val fieldNameId = accessor.fieldName.asSymbolId(interner)
                val fieldTypeId = accessor.fieldType.asSymbolId(interner)
                val accessorId = jIRdb.persistence.read { context ->
                    context.txn.find(ACCESSOR_IDS_TYPE, "classNameId", classNameId)
                        .filter { it.get<Long>("fieldNameId") == fieldNameId }
                        .filter { it.get<Long>("fieldTypeId") == fieldTypeId }
                        .singleOrNull()
                        ?.get<Long>("id")
                }

                accessorId ?: accessorIdGen.incrementAndGet().also {
                    newAccessors.add(accessor)
                }
            }

            is TaintMarkAccessor -> accessorToIdCache.computeIfAbsent(accessor) {
                val taintMarkId = accessor.mark.asSymbolId(interner)
                val accessorId = jIRdb.persistence.read { context ->
                    context.txn.find(ACCESSOR_IDS_TYPE, "taintMarkId", taintMarkId)
                        .singleOrNull()
                        ?.get<Long>("id")
                }

                accessorId ?: accessorIdGen.incrementAndGet().also {
                    newAccessors.add(accessor)
                }
            }

            is ClassStaticAccessor -> accessorToIdCache.computeIfAbsent(accessor) {
                val staticTypeNameId = accessor.typeName.asSymbolId(interner)
                val accessorId = jIRdb.persistence.read { context ->
                    context.txn.find(ACCESSOR_IDS_TYPE, "staticTypeNameId", staticTypeNameId)
                        .singleOrNull()
                        ?.get<Long>("id")
                }
                accessorId ?: accessorIdGen.incrementAndGet().also {
                    newAccessors.add(accessor)
                }
            }

            is TypeInfoAccessor -> accessorToIdCache.computeIfAbsent(accessor) {
                val typeInfoTypeNameId = accessor.typeName.asSymbolId(interner)
                val accessorId = jIRdb.persistence.read { context ->
                    context.txn.find(ACCESSOR_IDS_TYPE, "typeInfoTypeNameId", typeInfoTypeNameId)
                        .singleOrNull()
                        ?.get<Long>("id")
                }
                accessorId ?: accessorIdGen.incrementAndGet().also {
                    newAccessors.add(accessor)
                }
            }
        }
    }

    fun loadSummaries(method: JIRMethod): ByteArray? {
        return summariesCache.computeIfAbsent(method) {
            val methodId = getIdByMethod(method)
            jIRdb.persistence.read { context ->
                val summaryEntry = context.txn
                    .find(METHOD_SUMMARIES_TYPE, "methodId", methodId)
                    .filter { it.get<Int>("apModeId") == apModeId }
                    .singleOrNull()
                summaryEntry?.getRawBlob("summaries")
            } ?: ByteArray(0)
        }.takeUnless { it.isEmpty() }
    }

    fun storeSummaries(method: JIRMethod, summaries: ByteArray) {
        summariesCache[method] = summaries
    }

    private fun flush() {
        summariesCache.forEach { (method, summaries) ->
            if (summaries.isEmpty()) {
                return@forEach
            }

            val methodId = getIdByMethod(method)

            jIRdb.persistence.write { context ->
                val oldEntity = context.txn
                    .find(METHOD_SUMMARIES_TYPE, "methodId", methodId)
                    .filter { it.get<Int>("apModeId") == apModeId }
                    .singleOrNull()

                if (oldEntity != null) {
                    if (updateExistingSummaries) {
                        oldEntity.setRawBlob("summaries", summaries)
                    }
                } else {
                    context.txn.newEntity(METHOD_SUMMARIES_TYPE).also { summariesEntity ->
                        summariesEntity["methodId"] = methodId
                        summariesEntity["apModeId"] = apModeId
                        summariesEntity.setRawBlob("summaries", summaries)
                    }
                }
            }
        }

        newMethods.forEach { method ->
            val classNameId = method.enclosingClass.name.asSymbolId(interner)
            val methodNameId = method.name.asSymbolId(interner)
            val methodDescId = method.description.asSymbolId(interner)

            jIRdb.persistence.write { context ->
                context.txn.newEntity(METHOD_IDS_TYPE).also { methodId ->
                    methodId["id"] = methodToIdCache[method]
                    methodId["classNameId"] = classNameId
                    methodId["methodNameId"] = methodNameId
                    methodId["methodDescId"] = methodDescId
                }
            }
        }

        newAccessors.forEach { accessor ->
            if (accessor is FieldAccessor) {
                val classNameId = accessor.className.asSymbolId(interner)
                val fieldNameId = accessor.fieldName.asSymbolId(interner)
                val fieldTypeId = accessor.fieldType.asSymbolId(interner)
                jIRdb.persistence.write { context ->
                    context.txn.newEntity(ACCESSOR_IDS_TYPE).also { fieldAccessorId ->
                        fieldAccessorId["id"] = accessorToIdCache[accessor]!!
                        fieldAccessorId["classNameId"] = classNameId
                        fieldAccessorId["fieldNameId"] = fieldNameId
                        fieldAccessorId["fieldTypeId"] = fieldTypeId
                    }
                }
            } else if (accessor is ClassStaticAccessor) {
                val staticTypeNameId = accessor.typeName.asSymbolId(interner)
                jIRdb.persistence.write { context ->
                    context.txn.newEntity(ACCESSOR_IDS_TYPE).also { staticAccessorId ->
                        staticAccessorId["id"] = accessorToIdCache[accessor]!!
                        staticAccessorId["staticTypeNameId"] = staticTypeNameId
                    }
                }
            } else if (accessor is TypeInfoAccessor) {
                val typeInfoTypeNameId = accessor.typeName.asSymbolId(interner)
                jIRdb.persistence.write { context ->
                    context.txn.newEntity(ACCESSOR_IDS_TYPE).also { typeInfoAccessorId ->
                        typeInfoAccessorId["id"] = accessorToIdCache[accessor]!!
                        typeInfoAccessorId["typeInfoTypeNameId"] = typeInfoTypeNameId
                    }
                }
            } else {
                accessor as TaintMarkAccessor

                val taintMarkId = accessor.mark.asSymbolId(interner)
                jIRdb.persistence.write { context ->
                    context.txn.newEntity(ACCESSOR_IDS_TYPE).also { taintMarkAccessorId ->
                        taintMarkAccessorId["id"] = accessorToIdCache[accessor]!!
                        taintMarkAccessorId["taintMarkId"] = taintMarkId
                    }
                }
            }
        }

        flushMaxId(METHOD_IDS_TYPE, methodIdGen.get())
        flushMaxId(ACCESSOR_IDS_TYPE, accessorIdGen.get())

        jIRdb.persistence.write {
            interner.flush(it)
        }
    }

    private fun findSymbolName(id: Long, symbolType: String): String {
        return interner.findSymbolName(id)
            ?: error("Deserialization error. Unknown $symbolType id: $id")
    }

    private data class AccessorIds(
        val classNameId: Long?,
        val fieldNameId: Long?,
        val fieldTypeId: Long?,
        val taintMarkId: Long?,
        val staticTypeNameId: Long?,
        val typeInfoTypeNameId: Long?,
    )

    companion object {
        private const val METHOD_IDS_TYPE = "MethodIds"
        private const val ACCESSOR_IDS_TYPE = "AccessorIds"
        private const val METHOD_SUMMARIES_TYPE = "MethodSummaries"

        private const val ANY_ACCESSOR_ID = 0L
        private const val FINAL_ACCESSOR_ID = 1L
        private const val ELEMENT_ACCESSOR_ID = 2L
        private const val VALUE_ACCESSOR_ID = 3L
        private const val TYPE_INFO_GROUP_ACCESSOR_ID = 4L
        private const val MAX_RESERVED_ACCESSOR_ID = 4L
    }
}