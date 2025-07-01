package org.opentaint.ir.impl.storage.ers.decorators

import org.opentaint.ir.api.jvm.storage.ers.Entity
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.Transaction

inline fun Transaction.decorateDeeply(
    crossinline entityIterableWrapper: (EntityIterable) -> EntityIterable = { it },
    noinline entityWrapper: (Entity) -> Entity = { it }
): Transaction = decorateDeeplyWithLazyIterable(
    entityIterableWrapper = { entityIterableWrapper(it()) },
    entityWrapper = entityWrapper
)

fun Transaction.decorateDeeplyWithLazyIterable(
    entityIterableWrapper: (entityIterableCreator: () -> EntityIterable) -> EntityIterable = { it() },
    entityWrapper: (Entity) -> Entity = { it }
): Transaction = object : ErsObjectsWrapper {
    override val wrappedTxn = WrapperTransaction(
        wrapper = this,
        delegate = this@decorateDeeplyWithLazyIterable
    )

    override fun wrapEntityIterable(entityIterableCreator: () -> EntityIterable): EntityIterable =
        WrapperEntityIterable(
            wrapper = this,
            delegate = entityIterableWrapper(entityIterableCreator)
        )

    override fun wrapEntity(entity: Entity): Entity =
        WrapperEntity(
            wrapper = this,
            delegate = entityWrapper(entity)
        )
}.wrappedTxn

val Entity.unwrap: Entity get() = if (this is AbstractEntityDecorator) delegate.unwrap else this

private interface ErsObjectsWrapper {
    val wrappedTxn: Transaction
    fun wrapEntityIterable(entityIterableCreator: () -> EntityIterable): EntityIterable
    fun wrapEntity(entity: Entity): Entity
}

private class WrapperTransaction(
    override val delegate: Transaction,
    private val wrapper: ErsObjectsWrapper
) : AbstractTransactionDecorator() {

    override fun newEntity(type: String): Entity =
        wrapper.wrapEntity(super.newEntity(type))

    override fun getEntityOrNull(id: EntityId): Entity? =
        super.getEntityOrNull(id)?.let { wrapper.wrapEntity(it) }

    override fun all(type: String): EntityIterable =
        wrapper.wrapEntityIterable { super.all(type) }

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable =
        wrapper.wrapEntityIterable { super.find(type, propertyName, value) }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable =
        wrapper.wrapEntityIterable { super.findLt(type, propertyName, value) }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable =
        wrapper.wrapEntityIterable { super.findEqOrLt(type, propertyName, value) }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable =
        wrapper.wrapEntityIterable { super.findGt(type, propertyName, value) }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable =
        wrapper.wrapEntityIterable { super.findEqOrGt(type, propertyName, value) }
}

val Transaction.unwrap: Transaction get() = if (this is AbstractTransactionDecorator) delegate.unwrap else this

private class WrapperEntityIterable(
    override val delegate: EntityIterable,
    private val wrapper: ErsObjectsWrapper,
) : AbstractEntityIterableDecorator() {
    override fun combine(
        operation: CombineOperation, unwrappedOther: EntityIterable
    ): EntityIterable = wrapper.wrapEntityIterable {
        super.combine(operation, unwrappedOther)
    }

    override fun iterator(): Iterator<Entity> = super.iterator()
        .asSequence()
        .map { wrapper.wrapEntity(it) }
        .iterator()
}

private class WrapperEntity(
    override val delegate: Entity,
    private val wrapper: ErsObjectsWrapper
) : AbstractEntityDecorator() {
    override val txn: Transaction
        get() = wrapper.wrappedTxn

    override fun getLinks(name: String): EntityIterable = wrapper.wrapEntityIterable {
        super.getLinks(name)
    }
}
