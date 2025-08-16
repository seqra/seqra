package org.opentaint.ir.api.storage.ers

import java.io.InputStream
import java.io.OutputStream

/**
 * EntityRelationshipStorage that can be dumped and loaded
 */
interface DumpableLoadableEntityRelationshipStorage : EntityRelationshipStorage {

    fun dump(output: OutputStream)

    fun load(input: InputStream)

    fun load(databaseId: String): DumpableLoadableEntityRelationshipStorage? = null
}