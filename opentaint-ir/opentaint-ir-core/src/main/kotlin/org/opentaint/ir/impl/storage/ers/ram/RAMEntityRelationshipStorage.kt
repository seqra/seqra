package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.jvm.storage.ers.Binding
import org.opentaint.ir.api.jvm.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.impl.storage.ers.decorators.withAllDecorators
import org.opentaint.ir.impl.storage.ers.getBinding
import java.util.concurrent.atomic.AtomicReference

internal class RAMEntityRelationshipStorage : EntityRelationshipStorage {

    private val data: AtomicReference<RAMPersistentDataContainer> = AtomicReference(RAMPersistentDataContainer())

    override fun beginTransaction(readonly: Boolean) = RAMTransaction(this).withAllDecorators()

    override fun <T : Any> getBinding(clazz: Class<T>): Binding<T> = clazz.getBinding()

    override fun close() {
        data.set(RAMPersistentDataContainer())
    }

    internal var dataContainer: RAMPersistentDataContainer
        get() = data.get()
        set(value) {
            data.set(value)
        }

    internal fun compareAndSetDataContainer(
        expected: RAMPersistentDataContainer,
        newOne: RAMPersistentDataContainer
    ): Boolean = data.compareAndSet(expected, newOne)
}