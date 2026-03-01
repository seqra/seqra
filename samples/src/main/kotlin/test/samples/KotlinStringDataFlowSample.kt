package test.samples

class KotlinStringDataFlowSample {
    fun substringFlow() {
        val data = source()
        val sub = data.substring(1)
        sink(sub)
    }

    fun lowercaseFlow() {
        val data = source()
        val lower = data.lowercase()
        sink(lower)
    }

    fun trimFlow() {
        val data = source()
        val trimmed = data.trim()
        sink(trimmed)
    }

    fun plusFlow() {
        val data = source()
        val result = data + " suffix"
        sink(result)
    }

    fun replaceFlow() {
        val data = source()
        val replaced = data.replace("a", "b")
        sink(replaced)
    }

    fun stringTemplateFlow() {
        val data = source()
        val result = "prefix_${data}_suffix"
        sink(result)
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
