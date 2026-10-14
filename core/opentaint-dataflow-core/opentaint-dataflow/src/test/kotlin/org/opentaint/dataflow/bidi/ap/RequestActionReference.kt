package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.bidi.store.CallerRef

/** Test-only facade for the scalar oracle; the runtime ApOps exposes only batched group cursors. */
fun ApOps.referenceRequestAction(i: InitialAp, kind: ReferenceRequestKind, a: Pattern,
                                 caller: CallerRef): ReferenceRequestAction =
    referenceRequestAction(manager, i, kind, a, caller)
