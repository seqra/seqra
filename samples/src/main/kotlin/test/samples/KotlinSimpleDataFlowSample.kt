package test.samples

class KotlinSimpleDataFlowSample {
    fun simpleDataFlow() {
        val data = source()
        val processed = process(data)
        sink(processed)
    }

    private fun process(input: String): String = input

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
