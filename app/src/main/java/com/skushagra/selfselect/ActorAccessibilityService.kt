package com.skushagra.selfselect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class ActorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this use case
    }

    override fun onInterrupt() {
        // Handle service interruption if needed
    }

    fun pullDownNotificationBar() {
        val path = Path().apply {
            moveTo(500f, 0f)     // Adjust coordinates for your screen resolution
            lineTo(500f, 1000f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    companion object {
        var instance: ActorAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

