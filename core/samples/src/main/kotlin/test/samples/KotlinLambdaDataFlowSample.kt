package test.samples

class KotlinLambdaDataFlowSample {
    fun lambdaIdentityFlow() {
        val data = source()
        val identity: (String) -> String = { it }
        val result = identity(data)
        sink(result)
    }

    fun lambdaTransformFlow() {
        val data = source()
        val transform: (String) -> String = { it.uppercase() }
        val result = transform(data)
        sink(result)
    }

    fun lambdaPassedToMethodFlow() {
        val data = source()
        val result = applyTransform(data) { it.trim() }
        sink(result)
    }

    private fun applyTransform(input: String, fn: (String) -> String): String {
        return fn(input)
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
