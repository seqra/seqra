package test.samples

class KotlinInterproceduralDataFlowSample {
    fun interproceduralDataFlow() {
        val data = source()
        val validated = validate(data)
        val formatted = format(validated)
        sink(formatted)
    }

    private fun validate(input: String): String = input

    private fun format(input: String): String = input

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
