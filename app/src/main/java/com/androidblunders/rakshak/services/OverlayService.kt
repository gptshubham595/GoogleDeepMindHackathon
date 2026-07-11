package com.androidblunders.rakshak.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.androidblunders.rakshak.core.model.ThreatLevel
import com.androidblunders.rakshak.presentation.ActiveThreatInterceptor
import com.androidblunders.rakshak.presentation.CallActivationNotice
import com.androidblunders.rakshak.presentation.GentleGuidanceMode
import com.androidblunders.rakshak.ui.theme.RakshakTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    // Lifecycle components needed for Compose in a Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val handler = Handler(Looper.getMainLooper())
    private var overlayMode = OverlayMode.NONE

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_CALL_PHASE)?.let { rawPhase ->
            val phase = runCatching {
                com.androidblunders.rakshak.call.CallActivationOverlay.Phase.valueOf(rawPhase)
            }.getOrNull() ?: return START_NOT_STICKY
            when (phase) {
                com.androidblunders.rakshak.call.CallActivationOverlay.Phase.RINGING ->
                    showCallActivation(callConnected = false)
                com.androidblunders.rakshak.call.CallActivationOverlay.Phase.ACTIVE -> {
                    showCallActivation(callConnected = true)
                    handler.postDelayed({
                        if (overlayMode == OverlayMode.CALL_ACTIVATION) {
                            removeOverlay()
                            stopSelf()
                        }
                    }, CALL_ACTIVE_NOTICE_MS)
                }
                com.androidblunders.rakshak.call.CallActivationOverlay.Phase.IDLE -> {
                    if (overlayMode == OverlayMode.CALL_ACTIVATION) removeOverlay()
                    if (overlayMode != OverlayMode.THREAT) stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        val threatLevelName = intent?.getStringExtra(EXTRA_THREAT_LEVEL) ?: return START_NOT_STICKY
        val threatLevel = try {
            ThreatLevel.valueOf(threatLevelName)
        } catch (e: IllegalArgumentException) {
            return START_NOT_STICKY
        }

        if (threatLevel == ThreatLevel.IDLE || threatLevel == ThreatLevel.LOW || threatLevel == ThreatLevel.MEDIUM) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(threatLevel)
        return START_STICKY
    }
    
    private fun showOverlay(threatLevel: ThreatLevel) {
        removeOverlay()
        overlayMode = OverlayMode.THREAT
        composeView = newComposeView()

        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

        windowManager.addView(composeView, params)

        // Update the Compose content based on the threat level
        composeView?.setContent {
            RakshakTheme {
                when (threatLevel) {
                    ThreatLevel.ACTIVE_THREAT, ThreatLevel.EMERGENCY -> {
                        ActiveThreatInterceptor(
                            onHangUp = {
                                removeOverlay()
                                stopSelf()
                            }
                        )
                    }
                    ThreatLevel.GENTLE_GUIDANCE -> {
                        GentleGuidanceMode(
                            onHangUp = {
                                removeOverlay()
                                stopSelf()
                            }
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showCallActivation(callConnected: Boolean) {
        if (overlayMode == OverlayMode.THREAT) return
        removeOverlay()
        overlayMode = OverlayMode.CALL_ACTIVATION
        composeView = newComposeView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        windowManager.addView(composeView, params)
        composeView?.setContent {
            RakshakTheme { CallActivationNotice(callConnected = callConnected) }
        }
    }

    private fun newComposeView(): ComposeView = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
    }

    private fun removeOverlay() {
        composeView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            composeView = null
        }
        overlayMode = OverlayMode.NONE
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceScope.cancel()
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_THREAT_LEVEL = "extra_threat_level"
        const val EXTRA_CALL_PHASE = "extra_call_phase"
        private const val CALL_ACTIVE_NOTICE_MS = 3_000L
    }

    private enum class OverlayMode { NONE, CALL_ACTIVATION, THREAT }
}
