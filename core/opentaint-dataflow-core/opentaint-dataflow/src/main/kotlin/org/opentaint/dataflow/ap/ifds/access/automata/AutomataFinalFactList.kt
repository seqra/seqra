package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.access.common.CommonFinalFactList

class AutomataFinalFactList: CommonFinalFactList<AccessGraph>(), AutomataFinalApAccess {
    override val storage: AccessStorage<AccessGraph> = Default()
}
