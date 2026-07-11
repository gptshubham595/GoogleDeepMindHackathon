package com.androidblunders.rakshak.reporting

/**
 * Data class representing extracted financial transaction details from an SMS.
 */
data class TransactionDetails(
    val amount: String? = null,
    val accountInfo: String? = null,
    val referenceNumber: String? = null,
    val dateTime: String? = null,
    val place: String? = null,
)

/**
 * Utility to parse raw SMS text and extract key financial indicators using Regex.
 */
object TransactionDetailsExtractor {

    private val AMOUNT_REGEX = Regex("""(?i)(?:rs\.?|inr)\s*([\d,]+\.?\d{0,2})""")
    private val ACCOUNT_REGEX = Regex(
        """(?i)(?:a/c|acct|account)(?:\s*no\.?)?\s*([*xX\d-]{3,})""",
    )
    private val UPI_REGEX = Regex("""[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}""")
    private val REF_NUM_REGEX = Regex("""(?i)(?:ref|txn|transaction|utr)(?:\s*no\.?|\s*id)?\s*[:\-]?\s*([a-zA-Z0-9]{6,16})""")
    
    // Broad regex to determine if a message contains transaction/debit intent
    private val TRANSACTION_KEYWORDS = Regex("""(?i)(debited|credited|deducted|transaction|payment|paid|sent|received)""")
    private val DEBIT_KEYWORDS = Regex("""(?i)(debited|deducted|withdrawn|spent)""")
    private val PHISHING_ACTION = Regex(
        """(?i)(if\s+(?:this\s+)?(?:wasn'?t|was not)\s+you|click\s+(?:the\s+)?link|cancel\s+(?:the\s+)?transaction|verify\s+(?:the\s+)?transaction)""",
    )

    /**
     * Checks if the given SMS text likely describes a financial transaction.
     */
    fun isTransactionSms(messageBody: String): Boolean {
        return TRANSACTION_KEYWORDS.containsMatchIn(messageBody) || 
               AMOUNT_REGEX.containsMatchIn(messageBody)
    }

    /** Deterministic high-risk bank-alert lure; does not depend on an LLM response. */
    fun isSuspiciousDebitSms(messageBody: String): Boolean =
        DEBIT_KEYWORDS.containsMatchIn(messageBody) &&
            (PHISHING_ACTION.containsMatchIn(messageBody) ||
                Regex("""(?i)https?://|\bwww\.""").containsMatchIn(messageBody))

    /** Hard safety floor used before any model result is allowed to reach the UI. */
    fun deterministicThreatFloor(messageBody: String): Float =
        if (isSuspiciousDebitSms(messageBody)) 0.95f else 0f

    /**
     * Extracts structured transaction details from the given SMS text.
     */
    fun extractDetails(messageBody: String, receivedAtMillis: Long = System.currentTimeMillis()): TransactionDetails {
        val amount = AMOUNT_REGEX.find(messageBody)?.groupValues?.get(1)
        val account = ACCOUNT_REGEX.find(messageBody)?.groupValues?.get(1)
            ?: UPI_REGEX.find(messageBody)?.value
        val refNum = REF_NUM_REGEX.find(messageBody)?.groupValues?.get(1)

        return TransactionDetails(
            amount = amount,
            accountInfo = account,
            referenceNumber = refNum,
            dateTime = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(receivedAtMillis)),
        )
    }
}
