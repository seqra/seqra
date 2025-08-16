package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.Binding
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.impl.storage.ers.decorators.withAllDecorators
import org.opentaint.ir.impl.storage.ers.getBinding
import java.util.concurrent.atomic.AtomicReference

internal class RAMEntityRelationshipStorage(dataContainer: RAMDataContainer = RAMDataContainerMutable()) :
    EntityRelationshipStorage {

    private val data: AtomicReference<RAMDataContainer> = AtomicReference(dataContainer)

    override val isInRam: Boolean get() = true

    override fun beginTransaction(readonly: Boolean) = RAMTransaction(this).withAllDecorators()

    override val asReadonly: EntityRelationshipStorage
        get() = if (dataContainer is RAMDataContainerImmutable) this else RAMEntityRelationshipStorage(dataContainer.toImmutable())

    override fun <T : Any> getBinding(clazz: Class<T>): Binding<T> = clazz.getBinding()

    override fun close() {
        data.set(RAMDataContainerMutable())
    }

    internal var dataContainer: RAMDataContainer
        get() = data.get()
        set(value) {
            data.set(value)
        }

    internal fun compareAndSetDataContainer(
        expected: RAMDataContainer,
        newOne: RAMDataContainer
    ): Boolean = data.compareAndSet(expected, newOne)
}