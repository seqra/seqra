package org.opentaint.ir.impl.storage.ers.decorators

import org.opentaint.ir.api.jvm.storage.ers.Transaction

fun Transaction.withAllDecorators(): Transaction =
    recomputeEntityIterableOnEachUse().withChecks()
