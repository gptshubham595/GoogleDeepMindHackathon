package com.androidblunders.rakshak.call

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.androidblunders.rakshak.services.OverlayService

object CallActivationOverlay {
    enum class Phase { RINGING, ACTIVE, IDLE }

    fun update(context: Context, phase: Phase) {
        if (!Settings.canDrawOverlays(context)) return
        val intent = Intent(context, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_CALL_PHASE, phase.name)
        }
        runCatching { context.startService(intent) }
            .onFailure { Log.w(TAG, "Could not show call activation overlay", it) }
    }

    private const val TAG = "CallActivationOverlay"
}
