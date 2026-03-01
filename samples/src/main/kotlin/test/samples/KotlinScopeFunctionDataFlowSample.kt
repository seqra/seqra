package test.samples

class KotlinScopeFunctionDataFlowSample {
    fun letFlow() {
        val data = source()
        val result = data.let { it.trim() }
        sink(result)
    }

    fun runFlow() {
        val data = source()
        val result = data.run { trim() }
        sink(result)
    }

    fun withFlow() {
        val data = source()
        val result = with(data) { trim() }
        sink(result)
    }

    fun alsoFlow() {
        val data = source()
        data.also { sink(it) }
    }

    fun applyFlow() {
        val data = source()
        val builder = StringBuilder()
        builder.apply { append(data) }
        sink(builder.toString())
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
