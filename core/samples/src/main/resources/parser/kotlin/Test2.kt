class XXXX {
    fun foo() {

        val text = """
            |✅ Order $orderStatus
            |Env: ${if (isStaging) "STAGING" else "PROD"}
            |Plan: ${escapeHtml(planName)}
            |Client: ${escapeHtml(clientId.toString())}
            |Paid: ${escapeHtml(paidStr)}${escapeHtml(paidFx)}
            |Discount: ${escapeHtml(discountStr)}
            |$formattedJsonBlock
        """.trimMargin()

    }
}
