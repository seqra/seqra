package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.proto.MypyModuleProto

/**
 * Public entry point of the proto-to-flat pipeline.
 *
 * Lowers a single mypy [MypyModuleProto] to a [FlatModuleIR]. The Flat IR is
 * proto-independent; downstream consumers (FlatToPirConverter, transforms,
 * analyzers) depend only on [FlatModuleIR] and the rest of the `flat` package.
 */
object ProtoToFlat {
    fun lowerModule(astModule: MypyModuleProto): FlatModuleIR =
        ModuleLowering.lower(astModule)
}
