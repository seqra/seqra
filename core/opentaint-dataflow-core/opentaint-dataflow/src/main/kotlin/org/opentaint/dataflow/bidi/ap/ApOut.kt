package org.opentaint.dataflow.bidi.ap

/** Results of one operation. Requests refer to the input edge's premise. */
interface ApOut {
    fun result(f: Facts)
    fun markRequest(mark: TaintMark)
    fun positionRequest(position: PathNode)
}
