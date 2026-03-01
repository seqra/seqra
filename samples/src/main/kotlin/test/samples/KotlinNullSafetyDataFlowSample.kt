package test.samples

class KotlinNullSafetyDataFlowSample {
    fun elvisFlow() {
        val data: String? = nullableSource()
        val result = data ?: "default"
        sink(result)
    }

    fun safeCallFlow() {
        val data: String? = nullableSource()
        val result = data?.trim()
        if (result != null) {
            sink(result)
        }
    }

    fun notNullAssertionFlow() {
        val data: String? = nullableSource()
        val result = data!!
        sink(result)
    }

    fun safeCallChainFlow() {
        val data: String? = nullableSource()
        val result = data?.trim()?.lowercase()
        if (result != null) {
            sink(result)
        }
    }

    fun nullableSource(): String? = "tainted"
    fun source(): String = "tainted"
    fun sink(data: String) {}
}
