package org.opentaint.dataflow.sarif

import org.opentaint.ir.api.common.cfg.CommonInst
import java.nio.file.Path

interface SourceFileResolver<in Statement : CommonInst, Loc: Any> {
    fun resolveByName(inst: Statement, pkg: String, name: String): Loc?

    fun resolveByInst(inst: Statement): Loc?

    fun relativeToRoot(path: Path): String
}
