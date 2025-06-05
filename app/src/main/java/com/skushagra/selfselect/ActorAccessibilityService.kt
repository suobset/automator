package com.skushagra.selfselect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class ActorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ActorAccessibilitySvc" // Consistent TAG
        var instance: ActorAccessibilityService? = null
            private set // Ensure instance is only set internally
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected.")
        // Optional: Configure the service further if needed
        // val serviceInfo = AccessibilityServiceInfo()
        // serviceInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        // serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        // serviceInfo.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        // setServiceInfo(serviceInfo)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted.")
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service destroyed.")
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Log.d(TAG, "onAccessibilityEvent: ${event?.toString()}")
    }

    // --- Helper Functions ---
    private fun findNodeByText(text: String, exactMatch: Boolean = true, clickable: Boolean? = null, focusable: Boolean? = null, editable: Boolean? = null): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun collectNodes(node: AccessibilityNodeInfo) {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val matchesText = if (exactMatch) nodeText.equals(text, ignoreCase = true) else nodeText.contains(text, ignoreCase = true)

            if (matchesText) {
                var criteriaMet = true
                if (clickable != null && node.isClickable != clickable) criteriaMet = false
                if (focusable != null && node.isFocusable != focusable) criteriaMet = false
                if (editable != null && node.isEditable != editable) criteriaMet = false
                if (criteriaMet) nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collectNodes(it) }
            }
        }
        collectNodes(root)
        return nodes.firstOrNull() // Return the first match, consider returning a list or more specific selection
    }

    private fun findNodeByResourceId(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        // Ensure the ID is fully qualified; sometimes it might be passed without package
        // val qualifiedId = if (id.contains(":id/")) id else "${applicationContext.packageName}:id/$id"
        // Log.d(TAG, "Searching for resource ID: $id")
        val nodes = root.findAccessibilityNodeInfosByViewId(id) // No need to qualify if it's already full
        return nodes?.firstOrNull { it.isVisibleToUser } // Prioritize visible nodes
    }

    private fun findNodeByContentDescription(desc: String, exactMatch: Boolean = true, clickable: Boolean? = null): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun collectNodes(node: AccessibilityNodeInfo) {
            val nodeDesc = node.contentDescription?.toString() ?: ""
            val matchesDesc = if (exactMatch) nodeDesc.equals(desc, ignoreCase = true) else nodeDesc.contains(desc, ignoreCase = true)
            if (matchesDesc && (clickable == null || node.isClickable == clickable)) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collectNodes(it) }
            }
        }
        collectNodes(root)
        return nodes.firstOrNull()
    }

    private fun performActionOnNode(node: AccessibilityNodeInfo?, action: Int, args: Bundle? = null): Boolean {
        if (node == null) {
            Log.w(TAG, "Cannot perform action $action, node is null.")
            return false
        }
        val success = node.performAction(action, args)
        Log.d(TAG, "Action $action on node $node was ${if (success) "successful" else "unsuccessful"}")
        return success
    }

    private fun getScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isScrollable) return current
            current = current.parent
        }
        return null
    }

    // Recursive search for any scrollable node from root if no specific target
    private fun findFirstScrollableNode(node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findFirstScrollableNode(child)?.let { return it }
            }
        }
        return null
    }

    // --- Public Action Methods ---
    fun pullDownNotificationBar() {
        Log.d(TAG, "Attempting to pull down notification bar.")
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun navigateHome() {
        Log.d(TAG, "Attempting to navigate home.")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun navigateBack() {
        Log.d(TAG, "Attempting to navigate back.")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun openApp(appName: String) {
        Log.d(TAG, "Attempting to open app by name: $appName")
        try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                if (pm.getApplicationLabel(app).toString().equals(appName, ignoreCase = true)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        Log.i(TAG, "Successfully launched app: $appName (Package: ${app.packageName})")
                        return
                    }
                }
            }
            Log.w(TAG, "App not found by name: $appName")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app $appName: ${e.message}", e)
        }
    }

    fun openAppByPackageName(packageName: String) {
        Log.d(TAG, "Attempting to open app by package: $packageName")
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.i(TAG, "Successfully launched app by package: $packageName")
            } else {
                Log.w(TAG, "App not found for package: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app by package $packageName: ${e.message}", e)
        }
    }

    fun launchUrl(url: String) {
        Log.d(TAG, "Attempting to launch URL: $url")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.i(TAG, "Successfully launched URL: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching URL $url: ${e.message}", e)
        }
    }

    fun typeText(text: String, targetResId: String?, targetElementText: String?) {
        Log.d(TAG, "Attempting to type text: '$text'. TargetResId: $targetResId, TargetElementText: $targetElementText")
        var targetNode: AccessibilityNodeInfo? = null

        if (!targetResId.isNullOrBlank()) {
            targetNode = findNodeByResourceId(targetResId)
            Log.d(TAG, "Found node by ResId $targetResId: $targetNode")
        }
        if (targetNode == null && !targetElementText.isNullOrBlank()) {
            targetNode = findNodeByText(targetElementText, exactMatch = false, editable = true)
            Log.d(TAG, "Found node by ElementText $targetElementText (editable): $targetNode")
        }
        // Fallback to currently focused node if it's editable
        if (targetNode == null) {
            val root = rootInActiveWindow ?: return
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            Log.d(TAG, "Found node by FOCUS_INPUT: $targetNode, isEditable: ${targetNode?.isEditable}")
            if (targetNode != null && !targetNode.isEditable) {
                targetNode = null // only use if editable
            }
        }

        if (targetNode == null) {
            Log.w(TAG, "TYPE_TEXT: No suitable target field found to type text into.")
            return
        }

        if (!targetNode.isFocusable) {
            Log.w(TAG, "TYPE_TEXT: Target node is not focusable.")
            // Try to find a focusable parent or child if makes sense, or fail.
        } else if (!targetNode.isFocused) {
            performActionOnNode(targetNode, AccessibilityNodeInfo.ACTION_FOCUS, null)
        }

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        if (!performActionOnNode(targetNode, AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Log.e(TAG, "TYPE_TEXT: Failed to set text on node: $targetNode")
        } else {
            Log.i(TAG, "TYPE_TEXT: Successfully set text '$text' on node.")
        }
    }

    fun clickElement(elementText: String?, resourceId: String?, contentDescription: String?) {
        Log.d(TAG, "Attempting to click element. Text: '$elementText', ResId: '$resourceId', Desc: '$contentDescription'")
        var nodeToClick: AccessibilityNodeInfo? = null

        if (!resourceId.isNullOrBlank()) {
            nodeToClick = findNodeByResourceId(resourceId)
            if (nodeToClick != null) Log.d(TAG, "Found node by ResId: $resourceId")
        }
        if (nodeToClick == null && !elementText.isNullOrBlank()) {
            nodeToClick = findNodeByText(elementText, exactMatch = false, clickable = true)
            if (nodeToClick != null) Log.d(TAG, "Found node by Text: $elementText (clickable)")
        }
        if (nodeToClick == null && !contentDescription.isNullOrBlank()) {
            nodeToClick = findNodeByContentDescription(contentDescription, exactMatch = false, clickable = true)
            if (nodeToClick != null) Log.d(TAG, "Found node by ContentDescription: $contentDescription (clickable)")
        }

        if (nodeToClick == null) {
            Log.w(TAG, "CLICK_ELEMENT: No clickable element found for the given criteria.")
            return
        }

        var clickTarget = nodeToClick
        while(clickTarget != null && !clickTarget.isClickable) { // Traverse up to find a clickable parent
            clickTarget = clickTarget.parent
        }

        if (clickTarget == null || !clickTarget.isClickable) {
            Log.w(TAG, "CLICK_ELEMENT: Identified node or its parents are not clickable: Original: $nodeToClick")
            return
        }

        if (!performActionOnNode(clickTarget, AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.e(TAG, "CLICK_ELEMENT: Failed to click node: $clickTarget")
        } else {
            Log.i(TAG, "CLICK_ELEMENT: Successfully clicked node: $clickTarget")
        }
    }

    fun scrollView(direction: String, targetResId: String?, targetElementText: String?) {
        Log.d(TAG, "Attempting to scroll $direction. TargetResId: $targetResId, TargetElementText: $targetElementText")
        var scrollableNode: AccessibilityNodeInfo? = null

        if (!targetResId.isNullOrBlank()) {
            scrollableNode = getScrollableNode(findNodeByResourceId(targetResId))
        }
        if (scrollableNode == null && !targetElementText.isNullOrBlank()) {
            scrollableNode = getScrollableNode(findNodeByText(targetElementText, exactMatch = false))
        }
        if (scrollableNode == null) {
            scrollableNode = findFirstScrollableNode(rootInActiveWindow) // Fallback: find any scrollable
        }

        if (scrollableNode == null) {
            Log.w(TAG, "SCROLL_VIEW: No scrollable view found.")
            return
        }

        val action: Int = when (direction.uppercase(Locale.ROOT)) {
            "UP" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "LEFT" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "DOWN" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "RIGHT" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "FORWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "BACKWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> {
                Log.w(TAG, "SCROLL_VIEW: Unknown scroll direction '$direction'. Defaulting to ACTION_SCROLL_FORWARD.")
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
        }

        Log.d(TAG, "Performing scroll action: $action for direction $direction on node $scrollableNode")
        // Assuming performActionOnNode is defined elsewhere
        if (!performActionOnNode(scrollableNode, action)) {
            Log.e(TAG, "SCROLL_VIEW: Failed to scroll $direction on $scrollableNode")
        } else {
            Log.i(TAG, "SCROLL_VIEW: Successfully scrolled $direction on $scrollableNode")
        }
    }

    // --- More Complex / Placeholder Actions ---
    fun sendTextMessage(recipientName: String?, recipientNumber: String?, messageBody: String) {
        Log.i(TAG, "SEND_TEXT_MESSAGE called. RecipientName: $recipientName, RecipientNumber: $recipientNumber, Message: '$messageBody'")
        Log.w(TAG, "SEND_TEXT_MESSAGE: This action is highly dependent on the messaging app's UI and is not fully implemented robustly. Attempting generic steps.")

        // 1. Open SMS app (Example: trying a generic intent, might not always work or open the right app)
        var smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${recipientNumber ?: ""}"))
        smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageManager.resolveActivity(smsIntent, 0) == null) {
            // Fallback if no specific handler for smsto: try main action send
            smsIntent = Intent(Intent.ACTION_MAIN)
            smsIntent.addCategory(Intent.CATEGORY_APP_MESSAGING)
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            Log.d(TAG, "Attempting to launch SMS app with intent: $smsIntent")
            startActivity(smsIntent)
            Thread.sleep(2000) // Wait for app to open - BAD PRACTICE, use event listening or timeouts

            // The following steps are highly unreliable and illustrative
            if (!recipientNumber.isNullOrBlank()) {
                typeText(recipientNumber, null, "recipient") // Try to type number if a field "recipient" exists
            } else if(!recipientName.isNullOrBlank()){
                typeText(recipientName, null, "recipient") // Try to type name
            }
            Thread.sleep(500)
            typeText(messageBody, null, "message") // Try to type message if a field "message" exists
            Thread.sleep(500)
            clickElement("Send", null, "Send message") // Try to click send

            Log.i(TAG, "SEND_TEXT_MESSAGE: Attempted generic steps. Check device for actual outcome.")

        } catch (e: Exception) {
            Log.e(TAG, "SEND_TEXT_MESSAGE: Error during execution: ${e.message}", e)
        }
    }

    fun takeScreenshot() {
        Log.w(TAG, "TAKE_SCREENSHOT action called. This functionality requires special permissions or APIs (like MediaProjection) not directly available to Accessibility Services for security reasons. Full implementation is outside the current scope.")
        // To implement fully: would need to trigger MediaProjection from an Activity, get result in service.
    }

    fun toggleWifi(enable: Boolean) {
        Log.w(TAG, "TOGGLE_WIFI action called to set state to ${if(enable) "ON" else "OFF"}. Requires CHANGE_WIFI_STATE permission and WifiManager. Implementation deferred.")
        // Actual implementation would involve:
        // val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { /* Use Settings Panel */ } else { wifiManager.isWifiEnabled = enable }
    }

    fun toggleBluetooth(enable: Boolean) {
        Log.w(TAG, "TOGGLE_BLUETOOTH action called to set state to ${if(enable) "ON" else "OFF"}. Requires BLUETOOTH_ADMIN (and BLUETOOTH_CONNECT for API 31+) permissions. Implementation deferred.")
        // Actual implementation:
        // val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // if (enable) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
    }

    fun setVolume(volumeLevelString: String) {
        Log.w(TAG, "SET_VOLUME action called with level '$volumeLevelString'. Requires AudioManager. Implementation deferred.")
        // Actual implementation:
        // val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Parse volumeLevelString to float (0.0-1.0) or specific commands like MUTE/UP/DOWN
        // audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }
}
