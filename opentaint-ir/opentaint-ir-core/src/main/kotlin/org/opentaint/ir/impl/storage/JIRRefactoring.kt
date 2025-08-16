package org.opentaint.ir.impl.storage

import mu.KLogging
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.impl.features.Builders
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.storage.jooq.tables.references.APPLICATIONMETADATA
import org.opentaint.ir.impl.storage.jooq.tables.references.REFACTORINGS
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private const val REFACTORING_TYPE = "Refactoring"

abstract class JIRRefactoring {

    val name: String = javaClass.name

    /**
     * executed inside transaction
     */
    abstract fun run(context: StorageContext)
}

class JIRRefactoringChain(private val chain: List<JIRRefactoring>) {

    companion object : KLogging()

    @OptIn(ExperimentalTime::class)
    fun execute(context: StorageContext) {
        context.execute(
            sqlAction = { jooq ->
                val applied = hashSetOf<String>()
                try {
                    applied.addAll(jooq.select(REFACTORINGS.NAME).from(REFACTORINGS).fetchArray(REFACTORINGS.NAME))
                } catch (e: Exception) {
                    logger.info("fail to fetch applied refactorings")
                }
                chain.forEach { ref ->
                    jooq.connection {
                        if (!applied.contains(ref.name)) {
                            val time = measureTime {
                                ref.run(context)
                                jooq.insertInto(REFACTORINGS).set(REFACTORINGS.NAME, ref.name).execute()
                            }
                            logger.info("Refactoring ${ref.name} took $time msc")
                        }
                    }
                }
            },
            noSqlAction = { txn ->
                chain.forEach { refactoring ->
                    val refactoringName = refactoring.name
                    if (txn.find(REFACTORING_TYPE, "name", refactoringName).isEmpty) {
                        val time = measureTime {
                            refactoring.run(context)
                            txn.newEntity(REFACTORING_TYPE)["name"] = refactoringName
                        }
                        logger.info("Refactoring $refactoringName took $time msc")
                    }
                }
            }
        )
    }
}

class AddAppMetadataAndRefactoring : JIRRefactoring() {

    override fun run(context: StorageContext) {
        // This refactoring is applicable only for SQL context
        if (context.isSqlContext) {
            val jooq = context.dslContext
            jooq.createTableIfNotExists(APPLICATIONMETADATA)
                .column(APPLICATIONMETADATA.VERSION)
                .execute()
            jooq.createTableIfNotExists(REFACTORINGS)
                .column(REFACTORINGS.NAME)
                .execute()
        }
    }
}

class UpdateUsageAndBuildersSchemeRefactoring : JIRRefactoring() {

    override fun run(context: StorageContext) {
        Usages.create(context, true)
        Builders.create(context, true)
    }
}
