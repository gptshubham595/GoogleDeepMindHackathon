package com.androidblunders.rakshak

import com.androidblunders.rakshak.reporting.TransactionDetailsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDetailsExtractorTest {
    private val sample =
        "Rs. 50,000 debited from a/c no XXXXX345. If this wasn't you, " +
            "click the link to cancel the transaction."

    @Test
    fun debitReversalLinkIsAlwaysSuspicious() {
        assertTrue(TransactionDetailsExtractor.isTransactionSms(sample))
        assertTrue(TransactionDetailsExtractor.isSuspiciousDebitSms(sample))
        assertEquals(0.95f, TransactionDetailsExtractor.deterministicThreatFloor(sample), 0f)
    }

    @Test
    fun extractsAmountAndMaskedAccount() {
        val details = TransactionDetailsExtractor.extractDetails(sample, receivedAtMillis = 0L)
        assertEquals("50,000", details.amount)
        assertEquals("XXXXX345", details.accountInfo)
    }
}
