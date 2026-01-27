package org.opentaint.dataflow.sarif

import org.opentaint.ir.api.common.cfg.CommonInst
import java.nio.file.Path

interface SourceFileResolver<in Statement : CommonInst> {
    fun resolveByName(inst: Statement, pkg: String, name: String): Path?

    fun resolveByInst(inst: Statement): Path?

    fun relativeToRoot(path: Path): String
}
