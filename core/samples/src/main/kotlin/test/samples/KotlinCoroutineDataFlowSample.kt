package test.samples

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KotlinCoroutineDataFlowSample {
    fun runBlockingFlow() {
        val data = source()
        val result = runBlocking { data }
        sink(result)
    }

    fun launchFlow() {
        val data = source()
        runBlocking {
            launch {
                sink(data)
            }
        }
    }

    fun asyncAwaitFlow() {
        val data = source()
        val result = runBlocking {
            val deferred = async { data }
            deferred.await()
        }
        sink(result)
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}
