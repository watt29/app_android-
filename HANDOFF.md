# NongKanvelaAssistant — Current Handoff

Update this file whenever a user-visible change is delivered. Every new AI must read it before editing.

## Delivered state (2026-07-20)

- Installed package: `com.example.nongkanvelaassistant`.
- Latest verified phone install time: `2026-07-20 16:05:53` (this value changes after each later install; verify it again before work).
- The app is the current default phone app on the connected Android phone.
- Opening the app shows the voice assistant. The user taps the standard microphone icon and can say `โทรหา <ชื่อ>`; names are resolved from the device contacts.
- The manual contacts list remains only as a dial-screen fallback, not the launch screen.
- In-call controls use large standard icons: answer, end call, microphone mute, and speaker.
- Phone-related screens are locked to portrait orientation.
- Incoming saved contacts are spoken by name. English names use an English TTS voice when installed on the device.
- Incoming numbers not in device contacts are announced once: `สายจากหมายเลข … ไม่อยู่ในรายชื่อค่ะ หากไม่แน่ใจ ไม่ต้องรับสาย` and the in-call screen displays a yellow warning. Green answer and red decline remain unchanged.

## Key implementation files

- `app/src/main/java/com/example/nongkanvelaassistant/MainActivity.kt` — voice-first launch screen and microphone.
- `app/src/main/java/com/example/nongkanvelaassistant/ui/VoiceAssistantViewModel.kt` — voice command parsing and direct calls.
- `app/src/main/java/com/example/nongkanvelaassistant/ui/DialActivity.kt` — manual dial/contact fallback.
- `app/src/main/java/com/example/nongkanvelaassistant/ui/CallActivity.kt` — in-call interface.
- `app/src/main/java/com/example/nongkanvelaassistant/service/NongKanvelaInCallService.kt` — incoming caller announcement and contact/unknown warning lookup.
- `app/src/main/java/com/example/nongkanvelaassistant/service/CallSessionManager.kt` — in-call state and controls.
- `app/src/main/AndroidManifest.xml` — dialer role, in-call service, and portrait activity declarations.

## Required verification before handoff

1. Build: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` then `gradlew.bat assembleDebug`.
2. Install: `C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`.
3. Confirm the installed package timestamp with `adb shell dumpsys package com.example.nongkanvelaassistant`.
4. For phone behavior, also confirm the dialer role with `adb shell cmd role get-role-holders android.app.role.DIALER`.

## Safe next test

- Ask the user to place one real incoming test call from an unsaved number to confirm the spoken warning and yellow in-call warning. Do not initiate an external call without the user's direction.
