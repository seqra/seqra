package org.opentaint.ir.impl.storage.ers.decorators

import org.opentaint.ir.api.jvm.storage.ers.ERSNonExistingEntityException
import org.opentaint.ir.api.jvm.storage.ers.Entity
import org.opentaint.ir.api.jvm.storage.ers.EntityId
import org.opentaint.ir.api.jvm.storage.ers.EntityIterable
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.jvm.storage.ers.Transaction

fun Transaction.withChecks(): Transaction =
    CheckedTransaction(uncheckedDelegate = this).decorateDeeply(
        entityIterableWrapper = { CheckedEntityIterable(it, ownerTxn = this) },
        entityWrapper = { CheckedEntity(it, ownerTxn = this) }
    )

private class CheckedTransaction(
    private val uncheckedDelegate: Transaction,
) : AbstractTransactionDecorator() {
    override val delegate: Transaction
        get() {
            uncheckedDelegate.checkIsNotFinished()
            return uncheckedDelegate
        }

    // NOTE: properties bypass checks
    override val ers: EntityRelationshipStorage
        get() = uncheckedDelegate.ers
    override val isFinished: Boolean
        get() = uncheckedDelegate.isFinished
}

private class CheckedEntityIterable(
    private val uncheckedDelegate: EntityIterable,
    private val ownerTxn: Transaction
) : AbstractEntityIterableDecorator() {
    override val delegate: EntityIterable
        get() {
            ownerTxn.checkIsNotFinished()
            return uncheckedDelegate
        }

    override fun unwrapOther(other: EntityIterable): EntityIterable {
        require((other as CheckedEntityIterable).ownerTxn === ownerTxn) {
            "Cannot combine EntityIterables from different transactions"
        }
        return super.unwrapOther(other)
    }
}

private class CheckedEntity(
    private val uncheckedDelegate: Entity,
    private val ownerTxn: Transaction
) : AbstractEntityDecorator() {
    override val delegate: Entity
        get() {
            ownerTxn.checkIsNotFinished()
            uncheckedDelegate.id.checkExists()
            return uncheckedDelegate
        }

    // NOTE: properties bypass checks
    override val id: EntityId get() = uncheckedDelegate.id
    override val txn: Transaction get() = uncheckedDelegate.txn

    override fun addLink(name: String, targetId: EntityId): Boolean {
        targetId.checkExists()
        return super.addLink(name, targetId)
    }

    override fun deleteLink(name: String, targetId: EntityId): Boolean {
        targetId.checkExists()
        return super.deleteLink(name, targetId)
    }

    // TODO add ERS API endpoint for faster exists checks
    private fun EntityId.checkExists() {
        if (txn.unwrap.isEntityDeleted(this)) {
            throw ERSNonExistingEntityException()
        }
    }
}
