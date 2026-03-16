package org.opentaint.dataflow.jvm.ap.ifds.alias

data class ContextInfo(val context: IntArray): Comparable<ContextInfo> {
    val level: Int get() = context.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContextInfo) return false

        return context.contentEquals(other.context)
    }

    override fun hashCode(): Int = context.contentHashCode()

    override fun compareTo(other: ContextInfo): Int {
        val sizeCmp = context.size.compareTo(other.context.size)
        if (sizeCmp != 0) return sizeCmp

        for (i in 0 until context.size) {
            val elementCmp = context[i].compareTo(other.context[i])
            if (elementCmp != 0) return elementCmp
        }

        return 0
    }

    companion object {
        val rootContext = ContextInfo(IntArray(0))
    }
}
