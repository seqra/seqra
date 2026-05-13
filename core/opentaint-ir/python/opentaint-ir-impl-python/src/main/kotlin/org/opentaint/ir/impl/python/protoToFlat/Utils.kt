package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.proto.MypyImportFromStmtProto
import org.opentaint.ir.impl.python.proto.MypyImportStmtProto

internal fun recordImports(importManager: ImportManager, stmt: MypyImportStmtProto) {
    for (id in stmt.idsList) importManager.recordImport(id.module, id.alias)
}

internal fun recordImportsFrom(importManager: ImportManager, stmt: MypyImportFromStmtProto) {
    for (name in stmt.namesList) importManager.recordImportFrom(stmt.module, name.name, name.alias)
}