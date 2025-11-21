package org.opentaint.dataflow.jvm.ap.ifds

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
import org.objectweb.asm.tree.ClassNode
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class JIRSummariesFeature(
    apMode: ApMode,
    private val updateExistingSummaries: Boolean = true
) : JIRFeature<Any?, Any?> {
    private lateinit var opentaint-ir: JIRDatabase
    private val interner: SymbolInterner by lazy {
        opentaint-ir.persistence.symbolInterner
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

    override fun newIndexer(opentaint-ir: JIRDatabase, location: RegisteredLocation): ByteCodeIndexer {
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
        return opentaint-ir.persistence.read { context ->
            context.txn.all(type.toMaxIdEntityType())
                .singleOrNull()
                ?.get<Long>("value")
        }
    }

    private fun flushMaxId(type: String, value: Long) {
        opentaint-ir.persistence.write { context ->
            val entity = context.txn.all(type.toMaxIdEntityType()).singleOrNull()
                ?: context.txn.newEntity(type.toMaxIdEntityType())

            entity["value"] = value
        }
    }

    private fun checkJirdb(db: JIRDatabase) {
        check(db == opentaint-ir) {
            "Unexpected query with opentaint-ir not equal to cached one"
        }
    }

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                opentaint-ir = signal.opentaint-ir

                methodIdGen.set(getMaxId(METHOD_IDS_TYPE) ?: -1L)
                accessorIdGen.set(getMaxId(ACCESSOR_IDS_TYPE) ?: MAX_RESERVED_ACCESSOR_ID)
            }
            is JIRSignal.Closed -> {
                checkJirdb(signal.opentaint-ir)
                flush()
            }
            else -> Unit
        }
    }

    fun getMethodById(id: Long, cp: JIRClasspath): JIRMethod {
        checkJirdb(cp.db)

        return idToMethodCache.computeIfAbsent(id) {
            val (classNameId, methodNameId, methodDescId) = opentaint-ir.persistence.read { context ->
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

            val methodId = opentaint-ir.persistence.read { context ->
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
            else -> {
                idToAccessorCache.computeIfAbsent(id) {
                    val (classNameId, fieldNameId, fieldTypeId, taintMarkId) = opentaint-ir.persistence.read { context ->
                        val accessorEntity = context.txn.find(ACCESSOR_IDS_TYPE, "id", id)
                            .singleOrNull() ?: error("Deserialization error. Unknown accessor with id: $id")
                        val classNameId = accessorEntity.get<Long>("classNameId")
                        val fieldNameId = accessorEntity.get<Long>("fieldNameId")
                        val fieldTypeId = accessorEntity.get<Long>("fieldTypeId")
                        val taintMarkId = accessorEntity.get<Long>("taintMarkId")
                        arrayOf(classNameId, fieldNameId, fieldTypeId, taintMarkId)
                    }

                    if (classNameId != null) {
                        checkNotNull(fieldNameId) { "Expected non-null fieldNameId" }
                        checkNotNull(fieldTypeId) { "Expected non-null fieldTypeId" }

                        val className = findSymbolName(classNameId, symbolType = "className")
                        val fieldName = findSymbolName(fieldNameId, symbolType = "fieldName")
                        val fieldType = findSymbolName(fieldTypeId, symbolType = "fieldType")
                        FieldAccessor(className, fieldName, fieldType)
                    } else {
                        checkNotNull(taintMarkId) { "Expected non-null taintMarkId" }

                        val taintMarkName = interner.findSymbolName(taintMarkId)
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

            is FieldAccessor -> accessorToIdCache.computeIfAbsent(accessor) {
                val classNameId = accessor.className.asSymbolId(interner)
                val fieldNameId = accessor.fieldName.asSymbolId(interner)
                val fieldTypeId = accessor.fieldType.asSymbolId(interner)
                val accessorId = opentaint-ir.persistence.read { context ->
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
                val accessorId = opentaint-ir.persistence.read { context ->
                    context.txn.find(ACCESSOR_IDS_TYPE, "taintMarkId", taintMarkId)
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
            opentaint-ir.persistence.read { context ->
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

            opentaint-ir.persistence.write { context ->
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

            opentaint-ir.persistence.write { context ->
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
                opentaint-ir.persistence.write { context ->
                    context.txn.newEntity(ACCESSOR_IDS_TYPE).also { fieldAccessorId ->
                        fieldAccessorId["id"] = accessorToIdCache[accessor]!!
                        fieldAccessorId["classNameId"] = classNameId
                        fieldAccessorId["fieldNameId"] = fieldNameId
                        fieldAccessorId["fieldTypeId"] = fieldTypeId
                    }
                }
            } else {
                accessor as TaintMarkAccessor

                val taintMarkId = accessor.mark.asSymbolId(interner)
                opentaint-ir.persistence.write { context ->
                    context.txn.newEntity(ACCESSOR_IDS_TYPE).also { taintMarkAccessorId ->
                        taintMarkAccessorId["id"] = accessorToIdCache[accessor]!!
                        taintMarkAccessorId["taintMarkId"] = taintMarkId
                    }
                }
            }
        }

        flushMaxId(METHOD_IDS_TYPE, methodIdGen.get())
        flushMaxId(ACCESSOR_IDS_TYPE, accessorIdGen.get())

        opentaint-ir.persistence.write {
            interner.flush(it)
        }
    }

    private fun findSymbolName(id: Long, symbolType: String): String {
        return interner.findSymbolName(id)
            ?: error("Deserialization error. Unknown $symbolType id: $id")
    }

    companion object {
        private const val METHOD_IDS_TYPE = "MethodIds"
        private const val ACCESSOR_IDS_TYPE = "AccessorIds"
        private const val METHOD_SUMMARIES_TYPE = "MethodSummaries"

        private const val ANY_ACCESSOR_ID = 0L
        private const val FINAL_ACCESSOR_ID = 1L
        private const val ELEMENT_ACCESSOR_ID = 2L
        private const val MAX_RESERVED_ACCESSOR_ID = 2L
    }
}