package xxxxx

class XX {
    fun foo() {
        bar(
            exec = {
                connection.prepareStatement(sql).use { statement ->
                    val prepareStartedAtNs = System.nanoTime()
                    val prepareMs = elapsedMs(prepareStartedAtNs)
                }
            }
        )
    }
}
