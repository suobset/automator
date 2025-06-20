package com.skushagra.selfselect

import com.fasterxml.jackson.annotation.JsonProperty

// Main class for a script containing a list of actions
// The LLM should be instructed to generate YAML that maps to this structure.
// Example YAML:
// actions:
//   - action_type: OPEN_APP
//     description: "Open the Calculator app"
//     parameters:
//       app_name: "Calculator"
//   - action_type: TYPE_TEXT
//     description: "Type 123 into the focused field"
//     parameters:
//       text_to_type: "123"
//   - action_type: WAIT
//     description: "Wait for 2 seconds"
//     parameters:
//       wait_duration_ms: "2000"
//   - action_type: GET_TEXT_FROM_ELEMENT
//     description: "Get text from the element with ID 'com.example:id/textView'"
//     parameters:
//       element_resource_id: "com.example:id/textView"
//   - action_type: WAIT_FOR_ELEMENT
//     description: "Wait for an element with text 'Submit' to appear for 5 seconds"
//     parameters:
//       element_text_to_click: "Submit"
//       timeout_ms: "5000"
//   - action_type: PERFORM_ACCESSIBILITY_ACTION
//     description: "Perform ACTION_EXPAND on the element with content description 'Show more'"
//     parameters:
//       action_to_perform: "ACTION_EXPAND"
//       element_content_description: "Show more"

data class ActorScript(
    @JsonProperty("actions") val actions: List<ActorAction>
)

// Represents a single action within a script
data class ActorAction(
    @JsonProperty("action_type") val actionType: String,
    @JsonProperty("description") val description: String? = null, // Optional human-readable description for the action
    @JsonProperty("parameters") val parameters: Map<String, String>? = null // Key-value pairs for action-specific parameters
)

// Defines standardized string constants for action types.
// These should be used by the LLM when generating `action_type` fields
// and by ActorViewModel when interpreting them.
object ActionTypes {
    const val OPEN_APP = "OPEN_APP" // Params: APP_NAME or PACKAGE_NAME
    const val LAUNCH_URL = "LAUNCH_URL" // Params: URL
    const val SEND_TEXT_MESSAGE = "SEND_TEXT_MESSAGE" // Params: RECIPIENT_NUMBER or RECIPIENT_NAME, MESSAGE_BODY
    const val TYPE_TEXT = "TYPE_TEXT" // Params: TEXT_TO_TYPE, optional: ELEMENT_RESOURCE_ID, ELEMENT_TEXT_TO_CLICK (to focus field first)
    const val CLICK_ELEMENT = "CLICK_ELEMENT" // Params: ELEMENT_TEXT_TO_CLICK or ELEMENT_RESOURCE_ID or ELEMENT_CONTENT_DESCRIPTION
    const val SCROLL_VIEW = "SCROLL_VIEW" // Params: SCROLL_DIRECTION, optional: SCROLL_TARGET_RESOURCE_ID or SCROLL_TARGET_TEXT
    const val NAVIGATE_HOME = "NAVIGATE_HOME" // No params
    const val NAVIGATE_BACK = "NAVIGATE_BACK" // No params
    const val PULL_DOWN_NOTIFICATION_BAR = "PULL_DOWN_NOTIFICATION_BAR" // No params
    const val TAKE_SCREENSHOT = "TAKE_SCREENSHOT" // No params (consider how/where it's saved if implemented)
    const val WAIT = "WAIT" // Params: WAIT_DURATION_MS (milliseconds)

    // New Actions
    const val GET_TEXT_FROM_ELEMENT = "GET_TEXT_FROM_ELEMENT" // Params: ELEMENT_RESOURCE_ID or ELEMENT_TEXT_TO_CLICK or ELEMENT_CONTENT_DESCRIPTION
    const val WAIT_FOR_ELEMENT = "WAIT_FOR_ELEMENT" // Params: TIMEOUT_MS, and one of ELEMENT_RESOURCE_ID, ELEMENT_TEXT_TO_CLICK, ELEMENT_CONTENT_DESCRIPTION
    const val PERFORM_ACCESSIBILITY_ACTION = "PERFORM_ACCESSIBILITY_ACTION" // Params: ACTION_TO_PERFORM, and one of ELEMENT_RESOURCE_ID, ELEMENT_TEXT_TO_CLICK, ELEMENT_CONTENT_DESCRIPTION

    // Actions that are still less emphasized for LLM generation or more complex:
    const val TOGGLE_WIFI = "TOGGLE_WIFI" // Params: WIFI_STATE ("ON", "OFF")
    const val TOGGLE_BLUETOOTH = "TOGGLE_BLUETOOTH" // Params: BLUETOOTH_STATE ("ON", "OFF")
    const val SET_VOLUME = "SET_VOLUME" // Params: VOLUME_LEVEL (e.g., "0.0" to "1.0", or "MUTE", "UNMUTE")
    // const val FIND_CALENDAR_EVENTS = "FIND_CALENDAR_EVENTS" // Params: DATE, TIME_START, TIME_END (Advanced: complex, needs return)
}

// Defines standardized string constants for parameter keys to be used in the `parameters` map.
object ParameterKeys {
    const val APP_NAME = "app_name" // e.g., "Calculator", "Messages"
    const val PACKAGE_NAME = "package_name" // e.g., "com.android.calculator2"
    const val URL = "url" // e.g., "https://www.google.com"
    const val RECIPIENT_NAME = "recipient_name" // e.g., "John Doe" (requires contact lookup)
    const val RECIPIENT_NUMBER = "recipient_number" // e.g., "1234567890"
    const val MESSAGE_BODY = "message_body" // The text message content
    const val TEXT_TO_TYPE = "text_to_type" // The string of characters to type
    const val ELEMENT_TEXT_TO_CLICK = "element_text_to_click" // Text on a button or view (also used for identifying elements for GET_TEXT, WAIT_FOR_ELEMENT)
    const val ELEMENT_RESOURCE_ID = "element_resource_id" // View's R.id.name, e.g., "com.example:id/submit_button"
    const val ELEMENT_CONTENT_DESCRIPTION = "element_content_description" // Accessibility content description
    const val SCROLL_DIRECTION = "scroll_direction" // "UP", "DOWN", "LEFT", "RIGHT", "FORWARD", "BACKWARD"
    const val SCROLL_TARGET_TEXT = "scroll_target_text" // Text of a scrollable element or child
    const val SCROLL_TARGET_RESOURCE_ID = "scroll_target_resource_id" // Resource ID of a scrollable element
    const val WIFI_STATE = "wifi_state" // "ON", "OFF"
    const val BLUETOOTH_STATE = "bluetooth_state" // "ON", "OFF"
    const val VOLUME_LEVEL = "volume_level" // String: "0.0" to "1.0", or "MUTE", "UNMUTE", "UP", "DOWN"
    const val WAIT_DURATION_MS = "wait_duration_ms" // String: duration in milliseconds, e.g., "2000" for 2 seconds

    // New Parameter Keys
    const val TIMEOUT_MS = "timeout_ms" // String: duration in milliseconds for waiting, e.g., "5000"
    const val ACTION_TO_PERFORM = "action_to_perform" // String: The name of the AccessibilityNodeInfo action, e.g., "ACTION_EXPAND", "ACTION_COLLAPSE", "ACTION_COPY"

    // Parameter keys for more complex/future actions (commented out if not fully supported for generation)
    // const val DATE = "date"
    // const val TIME_START = "time_start"
    // const val TIME_END = "time_end"
    // const val SEARCH_QUERY = "search_query"
}