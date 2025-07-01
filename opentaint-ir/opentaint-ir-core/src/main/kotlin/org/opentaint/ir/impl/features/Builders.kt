package org.opentaint.ir.impl.features

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.ext.jvmPrimitiveNames
import org.opentaint.ir.api.jvm.storage.ers.compressed
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.execute
import org.opentaint.ir.impl.storage.executeQueries
import org.opentaint.ir.impl.storage.jooq.tables.references.BUILDERS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.storage.runBatch
import org.opentaint.ir.impl.storage.withoutAutoCommit
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

private val MethodNode.isGetter: Boolean
    get() {
        return name.startsWith("get")
    }

private val Int.isPublic get() = this and Opcodes.ACC_PUBLIC != 0
private val Int.isStatic get() = this and Opcodes.ACC_STATIC != 0

private data class BuilderMethod(
    val callerClass: String,
    val methodOffset: Int,
    val priority: Int
)

class BuildersIndexer(val persistence: JIRDatabasePersistence, private val location: RegisteredLocation) :
    ByteCodeIndexer {

    // class -> (caller_class, offset, priority)
    private val potentialBuilders = hashMapOf<String, HashSet<BuilderMethod>>()

    override fun index(classNode: ClassNode) {
        val callerClass = classNode.name
        classNode.methods.forEachIndexed { index, methodNode ->
            val isStatic = methodNode.access.isStatic
            if (methodNode.access.isPublic && !methodNode.isGetter) {
                val returnType = Type.getMethodType(methodNode.desc).returnType.internalName
                if (
                    !jvmPrimitiveNames.contains(returnType) && // not interesting in primitives
                    !returnType.startsWith("[") && // not interesting in arrays
                    !returnType.startsWith("java/") // not interesting in java package classes
                ) {
                    val noParams = Type.getArgumentTypes(methodNode.desc).isNullOrEmpty()
                    val isBuildName = methodNode.name.equals("build")
                    val priority = when {
                        isStatic && noParams && returnType == callerClass -> 15
                        isStatic && noParams -> 10
                        isBuildName && noParams -> 7
                        isStatic -> 5
                        isBuildName -> 3
                        else -> 0
                    }
                    potentialBuilders.getOrPut(returnType) { hashSetOf() }
                        .add(BuilderMethod(callerClass, index, priority))
                }
            }
        }
    }

    override fun flush(context: JIRDBContext) {
        context.execute(
            sqlAction = { jooq ->
                jooq.withoutAutoCommit { conn ->
                    conn.runBatch(BUILDERS) {
                        potentialBuilders.forEach { (calleeClass, builders) ->
                            val calleeId = calleeClass.className
                            builders.forEach {
                                val (callerClass, offset, priority) = it
                                val callerId = callerClass.className
                                setString(1, calleeId)
                                setString(2, callerId)
                                setInt(3, priority)
                                setInt(4, offset)
                                setLong(5, location.id)
                                addBatch()
                            }
                        }
                    }
                }
            },
            noSqlAction = { txn ->
                potentialBuilders.forEach { (returnedClassInternalName, builders) ->
                    builders.forEach { builder ->
                        val entity = txn.newEntity(BuilderEntity.BUILDER_ENTITY_TYPE)
                        entity[BuilderEntity.BUILDER_LOCATION_ID_PROPERTY] = location.id
                        entity[BuilderEntity.RETURNED_CLASS_NAME_ID_PROPERTY] =
                            persistence.findSymbolId(returnedClassInternalName.className)
                        entity[BuilderEntity.BUILDER_CLASS_NAME_ID] =
                            persistence.findSymbolId(builder.callerClass.className)
                        entity[BuilderEntity.METHOD_OFFSET_PROPERTY] = builder.methodOffset
                        entity[BuilderEntity.PRIORITY_PROPERTY] = builder.priority
                    }
                }
            }
        )
    }

}

data class BuildersResponse(
    val methodOffset: Int,
    val priority: Int,
    val source: ClassSource
)

object Builders : JIRFeature<Set<String>, BuildersResponse> {

    fun create(context: JIRDBContext, drop: Boolean) {
        context.execute(
            sqlAction = { jooq ->
                if (drop) {
                    jooq.executeQueries(dropScheme)
                }
                jooq.executeQueries(createScheme)
            },
            noSqlAction = { txn ->
                if (drop) {
                    txn.all(BuilderEntity.BUILDER_ENTITY_TYPE).deleteAll()
                }
            }
        )
    }

    private val createScheme = """
        CREATE TABLE IF NOT EXISTS "Builders"(
            "class_name"                    VARCHAR(256) NOT NULL,
            "builder_class_name"            VARCHAR(256) NOT NULL,
            "priority"                      INTEGER NOT NULL,
            "offset"      					INTEGER NOT NULL,
            "location_id"                   BIGINT NOT NULL,
        CONSTRAINT "fk_location_id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE
        );
    """.trimIndent()

    private val createIndex = """
            CREATE INDEX IF NOT EXISTS "BuildersSearch" ON "Builders"(location_id, class_name, priority);
            CREATE INDEX IF NOT EXISTS "BuildersSorting" ON "Builders"(priority);
            CREATE INDEX IF NOT EXISTS "BuildersJoin" ON "Builders"(builder_class_name);
    """.trimIndent()

    private val dropScheme = """
            DROP TABLE IF EXISTS "Builders";
            DROP INDEX IF EXISTS "BuildersSearch";
            DROP INDEX IF EXISTS "BuildersSorting";
            DROP INDEX IF EXISTS "BuildersJoin";
    """.trimIndent()

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                signal.jIRdb.persistence.write { context ->
                    if (signal.clearOnStart) {
                        context.execute(
                            sqlAction = { it.executeQueries(dropScheme) },
                            noSqlAction = { it.all(type = BuilderEntity.BUILDER_ENTITY_TYPE).deleteAll() }
                        )
                    }
                    context.execute(
                        sqlAction = { it.executeQueries(createScheme) },
                        noSqlAction = { /* no-op */ }
                    )
                }
            }

            is JIRSignal.LocationRemoved -> {
                signal.jIRdb.persistence.write { context ->
                    context.execute(
                        sqlAction = { it.deleteFrom(BUILDERS).where(BUILDERS.LOCATION_ID.eq(signal.location.id)) },
                        noSqlAction = { txn ->
                            txn.find(
                                type = BuilderEntity.BUILDER_ENTITY_TYPE,
                                propertyName = BuilderEntity.BUILDER_LOCATION_ID_PROPERTY,
                                value = signal.location.id
                            ).deleteAll()
                        }
                    )
                }
            }

            is JIRSignal.AfterIndexing -> {
                signal.jIRdb.persistence.write { context ->
                    context.execute(
                        sqlAction = { it.executeQueries(createIndex) },
                        noSqlAction = { /* no-op */ }
                    )
                }
            }

            is JIRSignal.Drop -> {
                signal.jIRdb.persistence.write { context ->
                    context.execute(
                        sqlAction = { it.deleteFrom(BUILDERS).execute() },
                        noSqlAction = { it.all(type = BuilderEntity.BUILDER_ENTITY_TYPE).deleteAll() }
                    )
                }
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JIRClasspath, req: Set<String>): Sequence<BuildersResponse> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JIRClasspath, req: Set<String>): Sequence<BuildersResponse> {
        val locationIds = classpath.registeredLocations.map { it.id }
        val persistence = classpath.db.persistence
        return sequence {
            val result = persistence.read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.select(BUILDERS.OFFSET, SYMBOLS.NAME, CLASSES.ID, CLASSES.LOCATION_ID, BUILDERS.PRIORITY)
                            .from(BUILDERS)
                            .join(SYMBOLS).on(SYMBOLS.NAME.eq(BUILDERS.BUILDER_CLASS_NAME))
                            .join(CLASSES).on(CLASSES.NAME.eq(SYMBOLS.ID).and(BUILDERS.LOCATION_ID.eq(CLASSES.LOCATION_ID)))
                            .where(
                                BUILDERS.CLASS_NAME.`in`(req).and(BUILDERS.LOCATION_ID.`in`(locationIds))
                            )
                            .limit(100)
                            .fetch()
                            .mapNotNull { (offset, className, classId, locationId, priority) ->
                                BuildersResponse(
                                    source = PersistenceClassSource(
                                        db = classpath.db,
                                        locationId = locationId!!,
                                        classId = classId!!,
                                        className = className!!,
                                    ),
                                    methodOffset = offset!!,
                                    priority = priority ?: 0
                                )
                            }
                    },
                    noSqlAction = { txn ->
                        req
                            .asSequence()
                            .map { returnedClassName -> persistence.findSymbolId(returnedClassName) }
                            .map { returnedClassNameId ->
                                txn.find(
                                    type = BuilderEntity.BUILDER_ENTITY_TYPE,
                                    propertyName = BuilderEntity.RETURNED_CLASS_NAME_ID_PROPERTY,
                                    value = returnedClassNameId
                                )
                            }
                            .flatten()
                            .filter { builder -> builder[BuilderEntity.BUILDER_LOCATION_ID_PROPERTY] in locationIds }
                            .flatMap { builder ->
                                val builderClassNameId: Long = builder[BuilderEntity.BUILDER_CLASS_NAME_ID]!!
                                txn.find("Class", "nameId", builderClassNameId.compressed).map { builderClass ->
                                    BuildersResponse(
                                        source = PersistenceClassSource(
                                            db = classpath.db,
                                            locationId = builder[BuilderEntity.BUILDER_LOCATION_ID_PROPERTY]!!,
                                            classId = builderClass.id.instanceId,
                                            className = persistence.findSymbolName(builderClassNameId),
                                        ),
                                        methodOffset = builder[BuilderEntity.METHOD_OFFSET_PROPERTY]!!,
                                        priority = builder[BuilderEntity.PRIORITY_PROPERTY] ?: 0
                                    )
                                }
                            }
                            .toList()
                    }
                )
            }.sortedByDescending { it.priority }
            yieldAll(result)
        }
    }

    override fun newIndexer(jIRdb: JIRDatabase, location: RegisteredLocation) =
        BuildersIndexer(jIRdb.persistence, location)

}

private object BuilderEntity {
    const val BUILDER_ENTITY_TYPE = "Builder"
    const val BUILDER_LOCATION_ID_PROPERTY = "builderLocationId"
    const val RETURNED_CLASS_NAME_ID_PROPERTY = "returnedClassNameId"
    const val BUILDER_CLASS_NAME_ID = "builderClassNameId"
    const val METHOD_OFFSET_PROPERTY = "methodOffset"
    const val PRIORITY_PROPERTY = "priority"
}
