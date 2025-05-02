package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRMethodExtFeature
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.core.cfg.InstList
import org.opentaint.ir.impl.analysis.impl.StringConcatSimplifierTransformer

object StringConcatSimplifier : JIRInstExtFeature, JIRMethodExtFeature {

    override fun transformInstList(method: JIRMethod, list: InstList<JIRInst>): InstList<JIRInst> {
        return StringConcatSimplifierTransformer(method.enclosingClass.classpath, list).transform()
    }

}