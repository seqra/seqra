package org.opentaint.jvm.sast.sarif

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.util.SarifTraits

class LocalNameResolver(
    private val traits: SarifTraits<CommonMethod, CommonInst>,
) {
    private val methodCache = hashMapOf<CommonMethod, HashMap<Int, String>>()

    fun isRegister(name: String): Boolean {
        return name[0] == '%'
    }

    private fun loadLocalNames(md: CommonMethod) {
        if (methodCache.contains(md)) return
        val mdLocals = hashMapOf<Int, String>()
        md.flowGraph().instructions.forEach { insn ->
            traits.getAssign(insn)?.let { assign ->
                traits.getLocals(assign.lhv).filterNot { isRegister(it.name) }.forEach { localInfo ->
                    mdLocals[localInfo.idx] = localInfo.name
                }
            }
        }
        methodCache[md] = mdLocals
    }

    fun getLocalName(md: CommonMethod, index: Int): String? {
        if (!methodCache.contains(md)) {
            loadLocalNames(md)
        }
        return methodCache[md]?.get(index)
    }
}
