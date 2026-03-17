package org.opentaint.dataflow.ap.ifds.access.common.ndf2f

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.InitialApAccess
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class DefaultNDF2FSubStorageWithAp<IAP, FAP : Any>(
    methodEntryPoint: CommonInst,
) : DefaultNDF2FSubStorage<IAP, FAP>(), InitialApAccess<IAP> {
    private val apIdx = DefaultApInterner<IAP>(methodEntryPoint)
    private val ap = arrayListOf<InitialFactAp>()

    override fun initialApIdx(ap: InitialFactAp): Int = apIdx.getOrCreateIndex(ap.base, getInitialAccess(ap)) {
        this.ap.add(ap)
    }

    override fun getInitialApByIdx(idx: Int): InitialFactAp = ap[idx]
}
