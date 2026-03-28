package org.opentaint.dataflow.go

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * No-op method context serializer for Go MVP.
 */
object DummyMethodContextSerializer : MethodContextSerializer {
    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        // No-op
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        return EmptyMethodContext
    }
}
