package com.androidblunders.rakshak.reporting

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Creates a user-reviewed cyber-fraud report draft; it never sends automatically. */
object CyberPoliceReporter {
    private const val CYBER_POLICE_EMAIL = "report@cybercrime.gov.in"
    private const val GMAIL_PACKAGE = "com.google.android.gm"

    suspend fun draftEmail(
        context: Context,
        sender: String,
        messageBody: String,
        transactionDetails: TransactionDetails? = null,
    ) {
        val place = withContext(Dispatchers.IO) { bestAvailablePlace(context) }
        val details = transactionDetails?.copy(place = place)
        val subject = "Urgent suspected cyber-fraud transaction report"
        val body = buildString {
            appendLine("Dear Cyber Crime Cell,")
            appendLine()
            appendLine("I want to report a suspected fraudulent debit/phishing message.")
            appendLine()
            appendLine("--- TRANSACTION DETAILS ---")
            appendLine("Amount: ${details?.amount?.let { "Rs. $it" } ?: "Not extracted"}")
            appendLine("Account/UPI: ${details?.accountInfo ?: "Not extracted"}")
            appendLine("Reference number: ${details?.referenceNumber ?: "Not provided"}")
            appendLine("SMS received at: ${details?.dateTime ?: "Unknown"}")
            appendLine("Device place: ${details?.place ?: "Unavailable or permission not granted"}")
            appendLine()
            appendLine("--- ORIGINAL SMS ---")
            appendLine("Sender: $sender")
            appendLine(messageBody)
            appendLine()
            appendLine("Please investigate this incident. I understand this is a draft and will review it before sending.")
            appendLine()
            appendLine("Sincerely,")
            appendLine("[Your name and phone number]")
        }

        withContext(Dispatchers.Main) {
            val baseIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.Builder().scheme("mailto").opaquePart(CYBER_POLICE_EMAIL).build()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(CYBER_POLICE_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            val gmailIntent = Intent(baseIntent).setPackage(GMAIL_PACKAGE)
            val intent = if (gmailIntent.resolveActivity(context.packageManager) != null) {
                gmailIntent
            } else {
                baseIntent
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No email app is installed", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestAvailablePlace(context: Context): String? {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted && !fineGranted) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val location = manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null

        val coordinates = "${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}"
        if (!Geocoder.isPresent()) return coordinates
        return runCatching {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            listOfNotNull(address?.locality, address?.adminArea, address?.countryName)
                .distinct()
                .joinToString(", ")
                .ifBlank { coordinates }
        }.getOrDefault(coordinates)
    }
}
