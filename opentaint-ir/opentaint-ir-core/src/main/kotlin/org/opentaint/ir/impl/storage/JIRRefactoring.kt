package org.opentaint.ir.impl.storage

import mu.KLogging
import org.opentaint.ir.impl.features.Builders
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.storage.jooq.tables.references.APPLICATIONMETADATA
import org.opentaint.ir.impl.storage.jooq.tables.references.REFACTORINGS
import org.jooq.DSLContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

abstract class JIRRefactoring {

    val name: String = javaClass.name

    /**
     * executed inside transaction
     */
    abstract fun run(jooq: DSLContext)

}

class JIRRefactoringChain(private val chain: List<JIRRefactoring>) {

    companion object : KLogging()

    private val applied = hashSetOf<String>()

    @OptIn(ExperimentalTime::class)
    fun execute(jooq: DSLContext) {
        try {
            applied.addAll(jooq.select(REFACTORINGS.NAME).from(REFACTORINGS).fetchArray(REFACTORINGS.NAME))
        } catch (e: Exception) {
            logger.info("fail to fetch applied refactorings")
        }

        chain.forEach { ref ->
            jooq.connection {
                if (!applied.contains(ref.name)) {
                    val time = measureTime {
                        ref.run(jooq)
                        jooq.insertInto(REFACTORINGS).set(REFACTORINGS.NAME, ref.name).execute()
                    }
                    logger.info("Refactoring ${ref.name} took $time msc")
                }
            }
        }
    }

}

class AddAppmetadataAndRefactoring : JIRRefactoring() {

    override fun run(jooq: DSLContext) {
        jooq.createTableIfNotExists(APPLICATIONMETADATA)
            .column(APPLICATIONMETADATA.VERSION)
            .execute()

        jooq.createTableIfNotExists(REFACTORINGS)
            .column(REFACTORINGS.NAME)
            .execute()
    }
}

class UpdateUsageAndBuildersSchemeRefactoring : JIRRefactoring() {

    override fun run(jooq: DSLContext) {
        Usages.create(jooq, true)
        Builders.create(jooq, true)
    }
}
