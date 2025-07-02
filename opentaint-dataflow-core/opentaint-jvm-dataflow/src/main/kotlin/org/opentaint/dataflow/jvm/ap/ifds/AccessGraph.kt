//package org.opentaint.dataflow.jvm.ap.ifds
//
//class AccessGraph {
//    sealed interface AccessGraphNode {
//        class Normal(
//            val isAbstract: Boolean,
//            val hasFinalSuccessor: Boolean,
//            val elementSuccessor: AccessGraphNode?,
//            val fields: Array<FieldAccessor>?,
//            val fieldSuccessors: Array<AccessGraphNode>?
//        ) : AccessGraphNode
//
//        class LoopRef(val node: Normal) : AccessGraphNode
//
//        fun mergeAdd(other: AccessGraphNode): AccessGraphNode {
//            if (this === other) return this
//
//
//        }
//    }
//}
