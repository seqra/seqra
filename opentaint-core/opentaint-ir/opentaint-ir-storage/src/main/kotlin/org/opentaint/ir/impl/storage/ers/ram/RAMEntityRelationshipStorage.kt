package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ers.Binding
import org.opentaint.ir.api.storage.ers.DumpableLoadableEntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.impl.RamErsSettings
import org.opentaint.ir.impl.storage.ers.decorators.withAllDecorators
import org.opentaint.ir.impl.storage.ers.getBinding
import org.opentaint.ir.util.io.inputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.outputStream

internal class RAMEntityRelationshipStorage(
    private val settings: RamErsSettings,
    dataContainer: RAMDataContainer = RAMDataContainerMutable()
) : DumpableLoadableEntityRelationshipStorage {

    private val data: AtomicReference<RAMDataContainer> = AtomicReference(dataContainer)

    override fun dump(output: OutputStream) {
        val dc = dataContainer
        check(dc is RAMDataContainerImmutable) {
            "Only immutable RAMEntityRelationshipStorage can be dumped"
        }
        dc.dump(output)
    }

    override fun load(input: InputStream) {
        dataContainer = input.readRAMDataContainerImmutable()
    }

    override fun load(databaseId: String): DumpableLoadableEntityRelationshipStorage? {
        return if (dataContainer is RAMDataContainerImmutable) {
            this
        } else {
            dumpFile(databaseId)?.let {
                if (it.exists()) {
                    it.inputStream().use { dumpStream ->
                        load(dumpStream)
                        this
                    }
                } else {
                    null
                }
            }
        }
    }

    override val isInRam: Boolean get() = true

    override fun beginTransaction(readonly: Boolean) = RAMTransaction(this).withAllDecorators()

    override fun asImmutable(databaseId: String): EntityRelationshipStorage {
        return if (dataContainer is RAMDataContainerImmutable) {
            this
        } else {
            load(databaseId)?.let {
                return it
            }
            RAMEntityRelationshipStorage(
                settings = settings,
                dataContainer.toImmutable().also { container ->
                    container as RAMDataContainerImmutable
                    dumpFile(databaseId)?.let {
                        it.outputStream(StandardOpenOption.CREATE_NEW).use { outputStream ->
                            container.dump(outputStream)
                        }
                    }
                })
        }
    }

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

    private fun dumpFile(databaseId: String): Path? =
        settings.immutableDumpsPath?.let {
            File(it).mkdirs()
            Path(it, databaseId)
        }
}