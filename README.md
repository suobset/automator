![Temp Banner](https://github.com/user-attachments/assets/e01f27cd-c633-4c77-9908-aeb4dd2c8a84)

# SelfSelect: an LLM-based Android scripting/automating platform

## Short Description
Automator is an innovative Android application that empowers you to automate tasks on your device using natural language commands. It leverages the power of Google's Gemini Large Language Model (LLM) to understand your intentions and translate them into executable YAML scripts. The app's "Actor" component then uses Android's Accessibility Service to perform these actions, offering a hands-free way to control your phone.

> So, what's the long term goal here?

Currently, the app uses a Gemini backend with some minor prompt engineering and RAG. You will need a Gemini API key to use the app, [learn how to get one here](https://ai.google.dev/gemini-api/docs/api-key).

During the first run, the App will prompt you the API key as a pop-up message (one-time only).

The long term goal for the app is to democratize computation for people coming from regional areas with little no no knowledge in English, or using a device.
This was specifically inspired by my own experiences helping people with limited reading/writing abilities to use their devices.

If you're curious, I have already written a [blog post](https://www.skushagra.com/2025/05/zero-android-experience-to-working.html) a while ago demonstrating how I learnt Android dev from scratch, and how AI supplemented the process to an extent here.
The post also contains details on what this app does, and how we achieve it.

### Things to note

Most importantly, SelfSelect/Automator is currently a very early-stage product. While I have been able to harness most of Android's accessibility features (including chaining of different YAML commands, and LLM prompt-engineering + RAG for YAML references), not all actions are going to be possible. Specifically, the current challenge (as of June 05) is the invocation of any action within a different app.

For example, "Open Wikipedia and navigate to the search bar" works perfectly fine. This is an end goal that can be invoked from the app itself.

However, "Take a picture from my front camera" is currently not possible, as it requires the camera to already be in the foreground.

Right now, the "Actor" performs actions. The next stage is for it to perceive the result. After tapping "Login," how does it know it's on the dashboard screen? This involves screen-reading, OCR, and understanding the "state" of the app. This is the next logical step for me to build into this project.

Parallel to this project, I am also working on a fine-tuned LLM that communicates in regional languages that do not follow a latin script.
I hope to share more details regarding this in the future [on my blog](https://skushagra.com). Model weights will be open.

The first non-Latin regional languages I am targeting are Hindi and Arabic.

## Features
*   **Natural Language Control:** Interact with your device by typing commands in a chat interface.
*   **LLM-Powered:** Utilizes the Gemini API to understand complex requests and generate automation scripts.
*   **YAML-Based Actions:** Your commands are translated into human-readable YAML scripts.
*   **Accessibility Service Integration:** Performs actions on your behalf by interacting with the UI like a real user.
*   **Secure API Key Management:** Prompts for API key entry via a dialog if not available and stores it securely using EncryptedSharedPreferences.
*   **Actor Interface:** View, edit, and test YAML scripts directly within the app.
*   **Extensible Action Set:** Supports a variety of actions, including:
    *   Opening applications.
    *   Navigating the system (home, back, notifications).
    *   Performing UI interactions (typing text, clicking elements, scrolling).
    *   Launching web URLs.
    *   Introducing delays/waits in scripts.
    *   Experimental support for sending text messages.

## Screenshots/Workflows

<img src="https://github.com/user-attachments/assets/b6962bf7-ffe3-4ab2-b7e3-c9c4f5b1ef83" width="30%">

(More incoming, including editing YAML, customizations, chained actions, etc.)

## Requirements / Prerequisites
*   **Android Studio:** Latest stable version recommended (e.g., Hedgehog or newer).
*   **Android Device or Emulator:**
    *   Recommended: Android 7.0 (API 24) or higher for full functionality.
    *   Minimum: Android 5.0 (API 21) for base functionality.
*   **Gemini API Key:** You'll need a valid API key from Google AI Studio to use the LLM features.
*   **Git:** For cloning the repository.

> Note: Feel free to peruse the releases section for pre-built APKs too (coming soon)

## Project Setup

1.  **Clone the Repository:**
    ```bash
    git clone <repository-url>
    cd <repository-directory>
    ```

2.  **Obtain a Gemini API Key:**
    *   Visit [Google AI Studio](https://makersuite.google.com/app/apikey) (or your relevant Google Cloud project console).
    *   Follow the instructions to create a new API key if you don't have one.

3.  **Configure the API Key in the App:**
    *   Open the cloned project in Android Studio.
    *   Build and run the application on your Android device or emulator.
    *   On the first launch (or if no valid API key is stored), a dialog will appear prompting you to enter your Gemini API key.
    *   Paste your generated API key into the text field and tap "OK". The key will be stored securely for future use.

4.  **Build and Run:**
    *   Use Android Studio to build the project and run it on your selected Android device or emulator.

5.  **Enable the Accessibility Service:**
    *   For the Automator's "Actor" component to perform on-device actions, its Accessibility Service must be enabled.
    *   The application may attempt to redirect you to the Accessibility settings page if the service is inactive when an action is triggered.
    *   Alternatively, you can manually enable it:
        1.  Go to your device's **Settings**.
        2.  Navigate to **Accessibility**.
        3.  Look for a section like **Installed apps**, **Downloaded services**, or similar.
        4.  Find **"Automator"** (or the specific name defined for the service, e.g., "Automator Accessibility Service") in the list.
        5.  Tap on it and toggle the service **ON**. You might need to grant specific permissions.
    *   **Important Note:** Accessibility services require significant permissions. Understand what these permissions allow before enabling the service.

## How to Use

1.  **Automator Chat:**
    *   Launch the Automator app. The main screen is a chat interface.
    *   Type your commands in natural language (e.g., "Open Calculator", "Go to youtube.com and search for 'Android development tutorials'", "Scroll down the page").
    *   The Gemini LLM will process your command and generate a corresponding YAML script, which will be displayed in the chat.

2.  **YAML Scripts:**
    *   These scripts, generated by the LLM, define a sequence of actions for the "Actor" component to execute.
    *   They are structured in YAML format, making them relatively human-readable.

3.  **Actor Screen:**
    *   This screen (if implemented as a distinct section) allows you to:
        *   View YAML scripts that were generated.
        *   Manually write or paste your own YAML scripts.
        *   Edit existing scripts.
        *   Execute scripts to test specific automation sequences or run custom tasks.
    *   It's a helpful tool for debugging action definitions or for users who prefer to work directly with the YAML scripts.

## Supported YAML Actions (Action Reference, so far)
The application executes automation tasks based on YAML scripts. Each script contains a list of `actions`.

**Basic Structure:**
```yaml
actions:
  - action_type: "ACTION_TYPE_NAME"
    description: "Optional description of what this action does." # Optional field
    parameters:  # Included only if the action takes parameters
      param_key1: "value1"
      param_key2: "value2"
  # ... more actions can follow in sequence
```

Key action_types and their parameters:

OPEN_APP: Opens a specified application.

app_name (String): The user-visible name of the app (e.g., "Messages", "Chrome"). (Required if package_name is not provided)
package_name (String): The technical package name (e.g., "com.google.android.apps.messaging", "com.android.chrome"). (Required if app_name is not provided)
LAUNCH_URL: Opens a given URL in a web browser.

url (String, Required): The complete URL, including the scheme (e.g., "https://www.example.com").
TYPE_TEXT: Types the specified text.

text_to_type (String, Required): The text to be entered.
element_resource_id (String, Optional): The resource ID of the target input field (e.g., "com.example.app:id/username_field").
element_text_to_click (String, Optional): The visible text or label of the target input field (used if resource ID is unknown).
CLICK_ELEMENT: Clicks on a UI element. (At least one identifier parameter is Required)

element_text_to_click (String): Text visible on or labeling the element (e.g., "Submit", "Next Page").
element_resource_id (String): The resource ID of the element.
element_content_description (String): The accessibility content description of the element.
SCROLL_VIEW: Scrolls a view on the screen.

scroll_direction (String, Required): Direction of the scroll. Accepted values: "UP", "DOWN", "LEFT", "RIGHT", "FORWARD" (typically similar to DOWN or RIGHT), "BACKWARD" (typically similar to UP or LEFT).
scroll_target_resource_id (String, Optional): Resource ID of a specific scrollable view.
scroll_target_text (String, Optional): Text within or labeling a specific scrollable view. (If no target is specified, a general scroll attempt is made).
NAVIGATE_HOME: Navigates to the device's home screen. (No parameters)

NAVIGATE_BACK: Simulates pressing the system's back button. (No parameters)

PULL_DOWN_NOTIFICATION_BAR: Opens the notification shade. (No parameters)

WAIT: Pauses the script execution for a set duration.

wait_duration_ms (String, Required): The duration to wait, in milliseconds (e.g., "3000" for 3 seconds).
SEND_TEXT_MESSAGE (Experimental): Attempts to send a text message.

recipient_number (String, Recommended): The phone number of the recipient.
recipient_name (String, Optional): The name of the contact (used if number is not provided; less reliable).
message_body (String, Required): The content of the text message.
Note: This action's success is highly dependent on the UI structure of the default messaging app and may not work reliably across all devices/apps.
(For more details on action parameters and LLM guidance, refer to the implicitPrompts.kt file within the project.)

Troubleshooting
Automator is not responding or shows errors in chat:
Ensure your Gemini API Key is correctly entered in the app via the settings/dialog.
Verify that your device has an active internet connection.
Actions are not being performed on the device:
Confirm that the "Automator" Accessibility Service is enabled in your device's Settings > Accessibility > Installed apps.
Make sure the service has been granted the necessary permissions during enablement.
Errors when running YAML scripts from the Actor screen:
Double-check the YAML syntax, action types, and parameter names against the Action Reference section. Ensure all parameter values are strings.

## License

Copyright 2025, [Kushagra Srivastava](https://skushagra.com) (Licensed under GNU AGPLv3)

> Note: The project is under AGPLv3 during development. Subsequent releases may be licensed under separate conditions and licenses.
