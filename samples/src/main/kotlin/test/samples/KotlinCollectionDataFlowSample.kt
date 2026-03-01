package test.samples

class KotlinCollectionDataFlowSample {
    fun listAddGetFlow() {
        val data = source()
        val list = mutableListOf<String>()
        list.add(data)
        val retrieved = list[0]
        sink(retrieved)
    }

    fun mapPutGetFlow() {
        val data = source()
        val map = mutableMapOf<String, String>()
        map["key"] = data
        val retrieved = map["key"]!!
        sink(retrieved)
    }

    fun iteratorFlow() {
        val data = source()
        val list = mutableListOf<String>()
        list.add(data)
        val it = list.iterator()
        val retrieved = it.next()
        sink(retrieved)
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
