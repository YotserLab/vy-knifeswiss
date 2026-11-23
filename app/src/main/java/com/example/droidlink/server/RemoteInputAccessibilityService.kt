package com.example.droidlink.server

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.droidlink.client.SystemAction

/**
 * AccessibilityService that enables remote control gesture injection and system key
 * execution (Back, Home, Recents, Notifications, Lock) without requiring Root.
 */
class RemoteInputAccessibilityService : AccessibilityService() { // DLK-R11

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "RemoteInputAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event inspection needed; service is purely for gesture/action dispatch
    }

    override fun onInterrupt() {
        Log.w(TAG, "RemoteInputAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "RemoteInputAccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "RemoteInputA11y"

        @Volatile
        var instance: RemoteInputAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        /**
         * Injects a tap at display coordinates (x, y).
         */
        fun tap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }

        /**
         * Injects a swipe from (startX, startY) to (endX, endY).
         */
        fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }

        /**
         * Injects a global system action.
         */
        fun executeSystemAction(action: SystemAction): Boolean {
            val service = instance ?: return false
            val globalAction = when (action) {
                SystemAction.BACK -> GLOBAL_ACTION_BACK
                SystemAction.HOME -> GLOBAL_ACTION_HOME
                SystemAction.RECENTS -> GLOBAL_ACTION_RECENTS
                SystemAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                SystemAction.LOCK_SCREEN -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        GLOBAL_ACTION_LOCK_SCREEN
                    } else {
                        GLOBAL_ACTION_BACK
                    }
                }
            }
            return service.performGlobalAction(globalAction)
        }
    }
}

