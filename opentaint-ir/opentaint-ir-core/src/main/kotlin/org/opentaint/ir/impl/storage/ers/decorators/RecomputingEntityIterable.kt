package org.opentaint.ir.impl.storage.ers.decorators

import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.Transaction

fun Transaction.recomputeEntityIterableOnEachUse() = decorateDeeplyWithLazyIterable(
    entityIterableWrapper = { entityIterableCreator -> RecomputingEntityIterable(entityIterableCreator) }
)

class RecomputingEntityIterable(
    val entityIterableCreator: () -> EntityIterable
) : AbstractEntityIterableDecorator() {
    override val delegate: EntityIterable get() = entityIterableCreator()
}
