package com.androidblunders.rakshak.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.androidblunders.rakshak.audio.CallTranscriber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process call-state watcher (API 31+ [TelephonyCallback]).
 *
 * Complements [PhoneCallReceiver]: while the app process is alive this is the
 * reliable path to auto-start the VOICE streaming service the instant a call
 * connects (starting a foreground service is permitted while the app is active).
 * The manifest receiver covers the "process not running" case.
 *
 * Registered once from [com.androidblunders.rakshak.RakshakApplication].
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val telephony by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }
    private var callback: TelephonyCallback? = null

    fun start() {
        if (callback != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_PHONE_STATE not granted — auto call-start disabled until granted.")
            return
        }
        val tm = telephony ?: return

        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onState(state)
        }
        runCatching { tm.registerTelephonyCallback(context.mainExecutor, cb) }
            .onSuccess {
                callback = cb
                Log.i(TAG, "Call-state monitor active.")
            }
            .onFailure { Log.e(TAG, "registerTelephonyCallback failed", it) }
    }

    fun stop() {
        callback?.let { telephony?.unregisterTelephonyCallback(it) }
        callback = null
    }

    private fun onState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.i(TAG, "Call connected → starting protection")
                CallActivationOverlay.update(context, CallActivationOverlay.Phase.ACTIVE)
                runCatching { CallTranscriber.startTranscription(context) }
                    .onFailure { Log.e(TAG, "Could not start streaming service", it) }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.i(TAG, "Call ended → stopping protection")
                CallActivationOverlay.update(context, CallActivationOverlay.Phase.IDLE)
                runCatching { CallTranscriber.stopTranscription(context) }
            }
            TelephonyManager.CALL_STATE_RINGING ->
                CallActivationOverlay.update(context, CallActivationOverlay.Phase.RINGING)
            else -> Unit
        }
    }

    private companion object { const val TAG = "CallStateMonitor" }
}
