package test.samples

class KotlinBranchLoopDataFlowSample {
    fun branchLoopDataFlow(flag: Boolean) {
        val data = source()
        val processed = if (flag) data else data
        val result = loopProcess(processed)
        sink(result)
    }

    private fun loopProcess(input: String): String = input

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
