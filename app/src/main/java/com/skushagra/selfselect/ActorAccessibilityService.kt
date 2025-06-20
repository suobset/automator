package com.skushagra.selfselect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.text
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ActorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ActorAccessibilitySvc"
        var instance: ActorAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected.")
        // Example: Configure service to retrieve window content and report view IDs
        // val serviceInfo = AccessibilityServiceInfo()
        // serviceInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        // serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        // serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
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
    private fun findNodeByText(
        text: String,
        exactMatch: Boolean = true,
        clickable: Boolean? = null,
        focusable: Boolean? = null,
        editable: Boolean? = null,
        visibleToUser: Boolean? = true
    ): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun collectNodes(node: AccessibilityNodeInfo) {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val matchesText = if (exactMatch) nodeText.equals(text, ignoreCase = true)
            else nodeText.contains(text, ignoreCase = true)

            if (matchesText) {
                var criteriaMet = true
                if (clickable != null && node.isClickable != clickable) criteriaMet = false
                if (focusable != null && node.isFocusable != focusable) criteriaMet = false
                if (editable != null && node.isEditable != editable) criteriaMet = false
                if (visibleToUser != null && node.isVisibleToUser != visibleToUser) criteriaMet = false

                if (criteriaMet) {
                    nodes.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectNodes(child)
                    // child.recycle() // Be cautious with manual recycling
                }
            }
        }
        collectNodes(root)
        // root.recycle() // Be cautious
        return nodes.firstOrNull { it.isVisibleToUser && it.isEnabled } ?: nodes.firstOrNull()
    }

    private fun findNodeByResourceId(id: String, visibleToUser: Boolean = true): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        // root.recycle() // Be cautious

        val resultNode = if (visibleToUser) {
            nodes?.firstOrNull { it.isVisibleToUser && it.isEnabled }
        } else {
            nodes?.firstOrNull { it.isEnabled } ?: nodes?.firstOrNull()
        }
        // nodes?.forEach { if (it != resultNode) it.recycle() } // Recycle non-returned nodes
        return resultNode
    }

    private fun findNodeByContentDescription(
        desc: String,
        exactMatch: Boolean = true,
        clickable: Boolean? = null,
        visibleToUser: Boolean = true
    ): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun collectNodes(node: AccessibilityNodeInfo) {
            val nodeDesc = node.contentDescription?.toString() ?: ""
            val matchesDesc = if (exactMatch) nodeDesc.equals(desc, ignoreCase = true)
            else nodeDesc.contains(desc, ignoreCase = true)

            if (matchesDesc) {
                var criteriaMet = true
                if (clickable != null && node.isClickable != clickable) criteriaMet = false
                if (node.isVisibleToUser != visibleToUser) criteriaMet = false
                if (criteriaMet) {
                    nodes.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectNodes(child)
                    // child.recycle()
                }
            }
        }
        collectNodes(root)
        // root.recycle()
        return nodes.firstOrNull { it.isVisibleToUser && it.isEnabled } ?: nodes.firstOrNull()
    }


    private fun performActionOnNode(node: AccessibilityNodeInfo?, action: Int, args: Bundle? = null): Boolean {
        if (node == null) {
            Log.w(TAG, "Cannot perform action $action, node is null.")
            return false
        }
        if (!node.isVisibleToUser) {
            Log.w(TAG, "Cannot perform action $action, node is not visible to user: $node")
            // node.recycle()
            return false
        }
        if (!node.isEnabled) {
            Log.w(TAG, "Cannot perform action $action, node is not enabled: $node")
            // node.recycle()
            return false
        }
        val success = node.performAction(action, args)
        Log.d(TAG, "Action $action on node (text: '${node.text}', contentDesc: '${node.contentDescription}', resId: '${node.viewIdResourceName}') was ${if (success) "successful" else "unsuccessful"}")
        // node.recycle() // Generally, don't recycle node after performing action, system manages it.
        return success
    }

    private fun getScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        // var tempNodeToRecycle: AccessibilityNodeInfo?
        while (current != null) {
            if (current.isScrollable && current.isVisibleToUser) return current
            // tempNodeToRecycle = current // Don't recycle the original input 'node' if it's not the one returned
            current = current.parent
            // if (tempNodeToRecycle != node && tempNodeToRecycle != current) tempNodeToRecycle?.recycle()
        }
        return null
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val scrollableChild = findFirstScrollableNode(child)
                if (scrollableChild != null) {
                    // if (child != scrollableChild) child.recycle()
                    return scrollableChild
                }
                // child.recycle()
            }
        }
        return null
    }

    // --- Public Action Methods ---
    fun pullDownNotificationBar(): Boolean {
        Log.d(TAG, "Attempting to pull down notification bar.")
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun navigateHome(): Boolean {
        Log.d(TAG, "Attempting to navigate home.")
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun navigateBack(): Boolean {
        Log.d(TAG, "Attempting to navigate back.")
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun openApp(appName: String): Boolean {
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
                        return true
                    } else {
                        Log.w(TAG, "Could not get launch intent for $appName (Package: ${app.packageName})")
                    }
                }
            }
            Log.w(TAG, "App not found by name: $appName")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app $appName: ${e.message}", e)
            return false
        }
    }

    fun openAppByPackageName(packageName: String): Boolean {
        Log.d(TAG, "Attempting to open app by package: $packageName")
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.i(TAG, "Successfully launched app by package: $packageName")
                return true
            } else {
                Log.w(TAG, "App not found for package: $packageName, or no launch intent.")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app by package $packageName: ${e.message}", e)
            return false
        }
    }

    fun launchUrl(url: String): Boolean {
        Log.d(TAG, "Attempting to launch URL: $url")
        if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            Log.e(TAG, "Invalid URL format: $url")
            return false
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.i(TAG, "Successfully launched URL: $url")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching URL $url: ${e.message}", e)
            return false
        }
    }

    fun typeText(textToType: String, targetResId: String?, targetElementText: String?): Boolean {
        Log.d(TAG, "Attempting to type text: '$textToType'. TargetResId: $targetResId, TargetElementText: $targetElementText")
        var targetNode: AccessibilityNodeInfo? = null
        var foundBy: String? = null

        if (!targetResId.isNullOrBlank()) {
            targetNode = findNodeByResourceId(targetResId, visibleToUser = true)
            if (targetNode != null) foundBy = "ResId $targetResId"
        }
        if (targetNode == null && !targetElementText.isNullOrBlank()) {
            targetNode = findNodeByText(targetElementText, exactMatch = false, editable = true, visibleToUser = true)
            if (targetNode == null) {
                targetNode = findNodeByText(targetElementText, exactMatch = false, focusable = true, visibleToUser = true)
                if(targetNode != null && !targetNode.isEditable) {
                    var editableChild: AccessibilityNodeInfo? = null
                    for(i in 0 until targetNode.childCount) {
                        val child = targetNode.getChild(i)
                        if (child != null && child.isEditable && child.isVisibleToUser) {
                            editableChild = child
                            break
                        }
                        // child?.recycle()
                    }
                    targetNode = editableChild ?: targetNode
                }
            }
            if (targetNode != null && foundBy == null) foundBy = "ElementText $targetElementText"
        }

        if (targetNode == null) {
            val root = rootInActiveWindow ?: return false
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            // root.recycle()
            if (targetNode != null) {
                if (!targetNode.isEditable || !targetNode.isVisibleToUser) {
                    // targetNode.recycle()
                    targetNode = null
                } else {
                    foundBy = "FOCUS_INPUT"
                }
            }
        }

        if (targetNode == null) {
            Log.w(TAG, "TYPE_TEXT: No suitable, visible, editable target field found.")
            return false
        }
        Log.d(TAG, "TYPE_TEXT: Target node found by $foundBy: $targetNode, isEditable: ${targetNode.isEditable}")

        if (!targetNode.isEditable) {
            Log.w(TAG, "TYPE_TEXT: Target node is not editable: $targetNode. Trying to click it first.")
            if (!performActionOnNode(targetNode, AccessibilityNodeInfo.ACTION_CLICK)) {
                // targetNode.recycle()
                return false
            }
            try { Thread.sleep(300) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); /* targetNode.recycle();*/ return false }
            val newFocusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (newFocusedNode != null && newFocusedNode.isEditable && newFocusedNode.isVisibleToUser) {
                // if (targetNode != newFocusedNode) targetNode.recycle()
                targetNode = newFocusedNode
                Log.d(TAG, "TYPE_TEXT: Switched to newly focused editable node: $targetNode")
            } else {
                // newFocusedNode?.recycle()
                Log.w(TAG, "TYPE_TEXT: Clicked non-editable node, but no new editable field focused.")
                // targetNode.recycle()
                return false
            }
        }

        if (!targetNode.isFocused) {
            val focusSuccess = performActionOnNode(targetNode, AccessibilityNodeInfo.ACTION_FOCUS)
            if (!focusSuccess) {
                Log.d(TAG, "ACTION_FOCUS failed, trying ACTION_CLICK to focus.")
                if (!performActionOnNode(targetNode, AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.w(TAG, "TYPE_TEXT: Failed to focus the target node via ACTION_FOCUS or ACTION_CLICK: $targetNode")
                    // targetNode.recycle()
                    return false
                }
            }
        }
        try { Thread.sleep(150) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); /* targetNode.recycle();*/ return false }

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        if (!performActionOnNode(targetNode, AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Log.e(TAG, "TYPE_TEXT: Failed to set text on node: $targetNode")
            // targetNode.recycle()
            return false
        }
        Log.i(TAG, "TYPE_TEXT: Successfully set text '$textToType' on node.")
        // targetNode.recycle()
        return true
    }

    fun clickElement(elementText: String?, resourceId: String?, contentDescription: String?): Boolean {
        Log.d(TAG, "Attempting to click element. Text: '$elementText', ResId: '$resourceId', Desc: '$contentDescription'")
        var nodeToClick: AccessibilityNodeInfo? = null

        if (!resourceId.isNullOrBlank()) {
            nodeToClick = findNodeByResourceId(resourceId, visibleToUser = true)
            if (nodeToClick != null) Log.d(TAG, "Found node by ResId: $resourceId, visible: ${nodeToClick.isVisibleToUser}, clickable: ${nodeToClick.isClickable}")
        }
        if (nodeToClick == null && !elementText.isNullOrBlank()) {
            nodeToClick = findNodeByText(elementText, exactMatch = false, clickable = null, visibleToUser = true)
            if (nodeToClick != null) Log.d(TAG, "Found node by Text: $elementText, visible: ${nodeToClick.isVisibleToUser}, clickable: ${nodeToClick.isClickable}")
        }
        if (nodeToClick == null && !contentDescription.isNullOrBlank()) {
            nodeToClick = findNodeByContentDescription(contentDescription, exactMatch = false, clickable = null, visibleToUser = true)
            if (nodeToClick != null) Log.d(TAG, "Found node by ContentDescription: $contentDescription, visible: ${nodeToClick.isVisibleToUser}, clickable: ${nodeToClick.isClickable}")
        }

        if (nodeToClick == null) {
            Log.w(TAG, "CLICK_ELEMENT: No visible element found for the given criteria.")
            return false
        }

        var clickTarget = nodeToClick
        // var tempNodeToRecycle: AccessibilityNodeInfo?
        while (clickTarget != null && !clickTarget.isClickable) {
            // tempNodeToRecycle = clickTarget
            clickTarget = clickTarget.parent
            // if (tempNodeToRecycle != nodeToClick && tempNodeToRecycle != clickTarget) tempNodeToRecycle?.recycle()
        }

        if (clickTarget == null || !clickTarget.isClickable || !clickTarget.isVisibleToUser) {
            Log.w(TAG, "CLICK_ELEMENT: Identified node or its ancestor is not clickable or not visible. Original: $nodeToClick, Target: $clickTarget")
            // nodeToClick.recycle()
            // clickTarget?.recycle()
            return false
        }

        if (!performActionOnNode(clickTarget, AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.e(TAG, "CLICK_ELEMENT: ACTION_CLICK failed on node: $clickTarget")
            // if (nodeToClick != clickTarget) nodeToClick.recycle()
            // clickTarget.recycle()
            return false
        }
        Log.i(TAG, "CLICK_ELEMENT: Successfully performed ACTION_CLICK on node: $clickTarget")
        // if (nodeToClick != clickTarget) nodeToClick.recycle()
        // clickTarget.recycle()
        return true
    }

    fun scrollView(direction: String, targetResId: String?, targetText: String?): Boolean {
        Log.d(TAG, "Attempting to scroll. Direction: $direction, TargetResId: $targetResId, TargetText: $targetText")
        var scrollableNode: AccessibilityNodeInfo? = null
        var foundNodeForScroll: AccessibilityNodeInfo? = null

        if (!targetResId.isNullOrBlank()) {
            foundNodeForScroll = findNodeByResourceId(targetResId, true)
            if (foundNodeForScroll != null) {
                scrollableNode = if (foundNodeForScroll.isScrollable) foundNodeForScroll else getScrollableNode(foundNodeForScroll)
            }
        }
        if (scrollableNode == null && !targetText.isNullOrBlank()) {
            val tempNode = findNodeByText(targetText, exactMatch = false, visibleToUser = true)
            if (tempNode != null) {
                scrollableNode = if (tempNode.isScrollable) tempNode else getScrollableNode(tempNode)
                // if (foundNodeForScroll != scrollableNode && foundNodeForScroll != tempNode) foundNodeForScroll?.recycle()
                foundNodeForScroll = tempNode
            }
        }
        if (scrollableNode == null) {
            // if (foundNodeForScroll != null) foundNodeForScroll.recycle()
            scrollableNode = findFirstScrollableNode(rootInActiveWindow)
            foundNodeForScroll = scrollableNode
        }

        if (scrollableNode == null || !scrollableNode.isScrollable) {
            Log.w(TAG, "SCROLL_VIEW: No suitable scrollable view found. Trying generic swipe.")
            // if (foundNodeForScroll != null && foundNodeForScroll != scrollableNode) foundNodeForScroll.recycle()
            // scrollableNode?.recycle()
            return performGenericSwipe(direction)
        }

        val action = when (direction.uppercase(Locale.ROOT)) {
            "UP" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "DOWN" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            //"LEFT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_SCROLL_LEFT else -1
            //"RIGHT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_SCROLL_RIGHT else -1
            "FORWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "BACKWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> {
                Log.w(TAG, "SCROLL_VIEW: Invalid scroll direction '$direction'.")
                // if (foundNodeForScroll != scrollableNode) foundNodeForScroll?.recycle()
                // scrollableNode.recycle()
                return false
            }
        }

        if (action == -1) {
            Log.w(TAG, "SCROLL_VIEW: Horizontal scroll actions require API 23+. Trying generic swipe for $direction.")
            // if (foundNodeForScroll != scrollableNode) foundNodeForScroll?.recycle()
            // scrollableNode.recycle()
            return performGenericSwipe(direction)
        }

        if (!scrollableNode.actionList.any { it.id == action }) {
            Log.w(TAG, "SCROLL_VIEW: Action $action ($direction) not supported by node: $scrollableNode. Trying generic swipe. Supported: ${scrollableNode.actionList.joinToString { it.label?.toString() ?: it.id.toString() }}")
            // if (foundNodeForScroll != scrollableNode) foundNodeForScroll?.recycle()
            // scrollableNode.recycle()
            return performGenericSwipe(direction)
        }

        if (!performActionOnNode(scrollableNode, action)) {
            Log.e(TAG, "SCROLL_VIEW: Failed to perform scroll action $action on node $scrollableNode. Trying generic swipe.")
            val genericSwipeSuccess = performGenericSwipe(direction)
            // if (foundNodeForScroll != scrollableNode) foundNodeForScroll?.recycle()
            // scrollableNode.recycle()
            return genericSwipeSuccess
        }
        Log.i(TAG, "SCROLL_VIEW: Successfully performed scroll action $action ($direction) on node.")
        // if (foundNodeForScroll != scrollableNode) foundNodeForScroll?.recycle()
        // scrollableNode.recycle()
        return true
    }

    private fun performGenericSwipe(direction: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val path = Path()
        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        val swipeDistanceVertical = height / 2.5f
        val swipeDistanceHorizontal = width / 2.5f
        val midX = width / 2f
        val midY = height / 2f

        when (direction.uppercase(Locale.ROOT)) {
            "UP" -> {
                startX = midX
                startY = midY + swipeDistanceVertical / 2
                endX = midX
                endY = midY - swipeDistanceVertical / 2
            }
            "DOWN" -> {
                startX = midX
                startY = midY - swipeDistanceVertical / 2
                endX = midX
                endY = midY + swipeDistanceVertical / 2
            }
            "LEFT" -> {
                startX = midX + swipeDistanceHorizontal / 2
                startY = midY
                endX = midX - swipeDistanceHorizontal / 2
                endY = midY
            }
            "RIGHT" -> {
                startX = midX - swipeDistanceHorizontal / 2
                startY = midY
                endX = midX + swipeDistanceHorizontal / 2
                endY = midY
            }
            else -> {
                Log.w(TAG, "performGenericSwipe: Invalid direction $direction")
                return false
            }
        }
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 50, 200)).build()
        val dispatched = dispatchGesture(gesture, null, null)
        Log.d(TAG, "performGenericSwipe $direction dispatched: $dispatched")
        if (dispatched) {
            try { Thread.sleep(300) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        return dispatched
    }

    fun sendTextMessage(recipientName: String?, recipientNumber: String?, messageBody: String): Boolean {
        Log.d(TAG, "Attempting to send text. RecipientName: $recipientName, RecipientNumber: $recipientNumber, Message: '$messageBody'")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${recipientNumber ?: ""}")
            putExtra("sms_body", messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(packageManager) != null) {
            try {
                applicationContext.startActivity(intent)
                Log.i(TAG, "Launched SMS app with intent. Further UI interaction for sending is likely needed and NOT automated by this simplified function.")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error trying to send text message via intent: ${e.message}", e)
                return false
            }
        } else {
            Log.w(TAG, "No SMS app found to handle SENDTO intent.")
            return false
        }
    }

    fun getTextFromElement(resId: String?, textToFind: String?, contentDesc: String?): String? {
        Log.d(TAG, "Attempting to get text. ResId: $resId, TextToFind: $textToFind, ContentDesc: $contentDesc")
        val node = findElementNow(resId, textToFind, contentDesc)

        if (node == null) {
            Log.w(TAG, "GET_TEXT_FROM_ELEMENT: Element not found.")
            return null
        }

        val text = node.text?.toString() ?: node.contentDescription?.toString()
        // node.recycle()
        if (text != null) {
            Log.i(TAG, "GET_TEXT_FROM_ELEMENT: Text found: \"$text\"")
            return text
        } else {
            Log.w(TAG, "GET_TEXT_FROM_ELEMENT: Element found, but it has no text or content description.")
            return null
        }
    }

    fun waitForElement(resId: String?, textToFind: String?, contentDesc: String?, timeoutMs: Long): Boolean {
        Log.d(TAG, "Waiting for element. ResId: $resId, Text: $textToFind, Desc: $contentDesc, Timeout: $timeoutMs ms")
        if (timeoutMs <= 0) {
            Log.w(TAG, "waitForElement: timeoutMs must be positive.")
            val foundNode = findElementNow(resId, textToFind, contentDesc)
            val wasFound = foundNode != null
            // foundNode?.recycle() // Recycle if found and not used further
            return wasFound
        }

        return runBlocking(Dispatchers.Default) {
            try {
                withTimeout(timeoutMs) {
                    var foundNode: AccessibilityNodeInfo? = null
                    while (foundNode == null) {
                        foundNode = findElementNow(resId, textToFind, contentDesc)
                        if (foundNode != null) {
                            Log.i(TAG, "waitForElement: Element found: $foundNode")
                            // foundNode.recycle() // Recycle after confirming found
                            return@withTimeout true
                        }
                        try {
                            Thread.sleep(250)
                        } catch (e: InterruptedException) {
                            Log.w(TAG, "waitForElement sleep interrupted")
                            Thread.currentThread().interrupt()
                            return@withTimeout false
                        }
                    }
                    false // Should ideally not be reached
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "waitForElement: Timeout. Element not found within $timeoutMs ms.")
                false
            }
        }
    }

    private fun findElementNow(resId: String?, textToFind: String?, contentDesc: String?): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = null
        if (!resId.isNullOrBlank()) {
            node = findNodeByResourceId(resId, visibleToUser = true)
        }
        if (node == null && !textToFind.isNullOrBlank()) {
            node = findNodeByText(textToFind, exactMatch = false, visibleToUser = true)
        }
        if (node == null && !contentDesc.isNullOrBlank()) {
            node = findNodeByContentDescription(contentDesc, exactMatch = false, visibleToUser = true)
        }
        return node // Caller is responsible for recycling if needed
    }

    fun performAccessibilityActionOnElement(
        actionName: String, // Takes the String name
        resId: String?,
        textToIdentify: String?,
        contentDesc: String?
    ): Boolean {
        Log.d(TAG, "Performing generic action '$actionName'. ResId: $resId, Text: $textToIdentify, Desc: $contentDesc")
        val node = findElementNow(resId, textToIdentify, contentDesc) // Assumes findElementNow exists and works

        if (node == null) {
            Log.w(TAG, "PERFORM_ACCESSIBILITY_ACTION: Element not found.")
            return false
        }

        val actionInt = mapActionNameToActionId(actionName) // Internal mapping
        if (actionInt == -1) {
            Log.w(TAG, "PERFORM_ACCESSIBILITY_ACTION: Unknown or unsupported action name '$actionName'.")
            // node.recycle() // Recycle if you manually manage nodes, otherwise system does.
            return false
        }

        // Check if the node actually supports this action
        if (!node.actionList.any { it.id == actionInt }) {
            val supportedActions = node.actionList.joinToString { it.label?.toString() ?: it.id.toString() }
            Log.w(TAG, "PERFORM_ACCESSIBILITY_ACTION: Action '$actionName' (ID: $actionInt) is not supported by the node. Supported: [$supportedActions]")
            // node.recycle()
            return false
        }

        val success = performActionOnNode(node, actionInt) // performActionOnNode takes the integer ID
        // node.recycle()
        return success
    }

    private fun mapActionNameToActionId(actionName: String): Int {
        return when (actionName.uppercase(Locale.ROOT)) {
            // Standard Actions from AccessibilityNodeInfo
            "ACTION_FOCUS" -> AccessibilityNodeInfo.ACTION_FOCUS
            "ACTION_CLEAR_FOCUS" -> AccessibilityNodeInfo.ACTION_CLEAR_FOCUS
            "ACTION_SELECT" -> AccessibilityNodeInfo.ACTION_SELECT
            "ACTION_CLEAR_SELECTION" -> AccessibilityNodeInfo.ACTION_CLEAR_SELECTION
            "ACTION_CLICK" -> AccessibilityNodeInfo.ACTION_CLICK
            "ACTION_LONG_CLICK" -> AccessibilityNodeInfo.ACTION_LONG_CLICK
            "ACTION_ACCESSIBILITY_FOCUS" -> AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            "ACTION_CLEAR_ACCESSIBILITY_FOCUS" -> AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
            "ACTION_NEXT_AT_MOVEMENT_GRANULARITY" -> AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY" -> AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
            "ACTION_NEXT_HTML_ELEMENT" -> AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT
            "ACTION_PREVIOUS_HTML_ELEMENT" -> AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT
            "ACTION_SCROLL_FORWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "ACTION_SCROLL_BACKWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "ACTION_COPY" -> AccessibilityNodeInfo.ACTION_COPY
            "ACTION_PASTE" -> AccessibilityNodeInfo.ACTION_PASTE
            "ACTION_CUT" -> AccessibilityNodeInfo.ACTION_CUT
            "ACTION_SET_SELECTION" -> AccessibilityNodeInfo.ACTION_SET_SELECTION
            "ACTION_EXPAND" -> AccessibilityNodeInfo.ACTION_EXPAND
            "ACTION_COLLAPSE" -> AccessibilityNodeInfo.ACTION_COLLAPSE
            "ACTION_DISMISS" -> AccessibilityNodeInfo.ACTION_DISMISS // API 21
            "ACTION_SET_TEXT" -> AccessibilityNodeInfo.ACTION_SET_TEXT // API 21
            // API 23+
//            "ACTION_CONTEXT_CLICK" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_CONTEXT_CLICK else -1
//            "ACTION_SCROLL_UP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_SCROLL_UP else -1 // Or map to SCROLL_BACKWARD if you prefer
//            "ACTION_SCROLL_DOWN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_SCROLL_DOWN else -1 // Or map to SCROLL_FORWARD
//            "ACTION_SCROLL_LEFT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_SCROLL_LEFT else -1
//            "ACTION_SCROLL_RIGHT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.ACTION_SCROLL_RIGHT else -1
//            // API 24+
//            "ACTION_SHOW_ON_SCREEN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) AccessibilityNodeInfo.ACTION_SHOW_ON_SCREEN else -1
//            // API 26+
//            "ACTION_MOVE_WINDOW" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AccessibilityNodeInfo.ACTION_MOVE_WINDOW else -1
//            // API 28+
//            "ACTION_SHOW_TOOLTIP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) AccessibilityNodeInfo.ACTION_SHOW_TOOLTIP else -1
//            "ACTION_HIDE_TOOLTIP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) AccessibilityNodeInfo.ACTION_HIDE_TOOLTIP else -1
//            // API 29+
//            "ACTION_PAGE_UP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AccessibilityNodeInfo.ACTION_PAGE_UP else -1
//            "ACTION_PAGE_DOWN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AccessibilityNodeInfo.ACTION_PAGE_DOWN else -1
//            "ACTION_PAGE_LEFT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AccessibilityNodeInfo.ACTION_PAGE_LEFT else -1
//            "ACTION_PAGE_RIGHT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AccessibilityNodeInfo.ACTION_PAGE_RIGHT else -1
//            // API 30+
//            "ACTION_PRESS_AND_HOLD" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) AccessibilityNodeInfo.ACTION_PRESS_AND_HOLD else -1
//            "ACTION_IME_ENTER" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) AccessibilityNodeInfo.ACTION_IME_ENTER else -1
            else -> {
                Log.w(TAG, "Unknown action name: $actionName")
                -1 // Return an invalid ID for unknown actions
            }
        }
    }
}
