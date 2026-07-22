# NongKanvelaAssistant — Current Handoff

Update this file whenever a user-visible change is delivered. Every new AI must read it before editing.

## Authority and anti-regression rule

- This document's **Delivered state** is the authoritative current behavior. Inspect the present source before editing; do not recreate a design from old chat history, stale APKs, past patches, or old plan entries.
- Treat every delivered behavior below as protected. Do not remove, weaken, or replace it while fixing another issue unless the user explicitly asks to change that behavior.
- Historical verification timestamps are evidence only. The connected phone's latest package timestamp and the current source are the only valid delivery state.
- In particular, do **not** restore the removed direct-audio speech path; preserve deterministic app/map routing, neutral forms of address, encrypted local learning, and immediate-call SOS behavior.

## Delivered state (2026-07-20)

- **[FIX 2026-07-22] Fresh-install launch safety**: On a newly installed app where location permission has not yet been granted, the SOS foreground service no longer starts with the location type and crashes the process. The assistant opens normally; the automatic fall-detection service starts only after location permission is available. Boot/package-replacement startup is likewise skipped safely until that permission is granted.
- **[FIX 2026-07-22] Hidden action markers**: Internal response markers such as `[ACTION:…]` are now removed as a final safety step before the response is displayed, stored, sent to connected logs, or spoken. This also covers an unknown or malformed marker, so users never need to see or say this implementation text.
- **[FIX 2026-07-22] Reminder truthfulness**: Questions and commands about reminders, alarms, and appointments are now routed to the local reminder system before AI. Reminder lists therefore answer only from records stored on this phone; AI cannot invent, infer, or summarize a reminder that was not saved.
- **[CHANGE 2026-07-22] User-controlled microphone**: Voice capture is now one-shot. The user taps the microphone to start listening; it stops after a spoken result, silence, or another tap, and it never resumes automatically after a response. This removes background-like continuous listening while keeping the existing microphone permission flow.
- **[CHANGE 2026-07-22] Memory feature removed**: The brain button, memory screen, voice “จำว่า” handling, and memory context sent to AI are removed. User-created persistence is now limited to reminders; a request to remember something is directed to create a dated reminder instead. Existing encrypted memory data is left untouched but is no longer read.

- **[NEW] Deterministic app and map routing**: A spoken request containing `เปิด`/`เล่น` and explicit route commands such as `ไปเซ็นทรัลเวสต์เกต` are resolved locally instead of being guessed by the AI, even when a wake phrase appears first. `เปิดไลน์` is bound to LINE's package identity even if its launcher label varies, while `เปิดไลน์แมน` opens LINE MAN. Spoken 7-Eleven names such as `เปิดเซเว่นอีเลฟเว่น` are bound to the installed 7-Eleven package. Destination search and navigation intents are explicitly restricted to Google Maps, so Grab, Zoom, and other installed apps cannot capture them. The app also reports when a selected package cannot actually be launched.
- **[NEW] Continuous voice session**: Tapping the microphone starts a foreground-only continuous voice session. A silence/no-match error restarts listening after a short delay. Recognition is paused while the assistant speaks and resumes after the reply. Saying `หยุดฟัง`, `เลิกฟัง`, or `ปิดไมค์`, or tapping the microphone again, ends the session. Launching any external app, Google Maps, settings page, SMS screen, or dialer also ends the session before leaving the assistant. This is not an always-on background microphone.
- **[NEW] App voice keys**: A caregiver can save an easy-to-say name for any installed app using `เรียกแอป <ชื่อแอป> ว่า <ชื่อสั้น>`. The mapping is stored locally on the device and `เปิด <ชื่อสั้น>` launches the saved package directly, without an AI guess.
- **[NEW] Local behavior learning**: Recognized user phrases and their usage frequency are retained in encrypted on-device storage as a private learning history. Raw microphone audio is never saved, and this history is not sent to an external service.
- **[NEW] Automatic app-key learning**: Each successful locally resolved app-opening phrase is automatically saved as a private voice key for that app package, so the same spoken variant becomes a direct match on later use.
- **[NEW] Complete app-catalog fallback**: Unknown spoken app names are no longer rejected by the strict local matcher. The assistant supplies the live launcher-app catalog to AI for phonetic matching, then only opens a package Android confirms is installed and launchable. Successful matches become local voice keys for later use.
- **[NEW] Direct Bitkub launch**: `เปิด Bitkub`, `เปิดบิทคับ`, and common Thai speech variants now resolve directly to the installed Bitkub package (`com.bitkub`). The general polite-word correction is applied only after the Bitkub recognition correction, so it cannot break the app name.
- **[NEW] Portable standard app catalog**: New installs receive baseline Thai voice names for common apps (Grab, Zoom, Facebook, Messenger, Shopee, Lazada, TikTok, YouTube, Chrome, Gmail, K PLUS, SCB EASY, TrueMoney, เป๋าตัง, and myAIS), alongside LINE, 7-Eleven, Krungthai NEXT, and Bitkub. A name opens only when that exact package exists in the current phone's live app list; otherwise it is not redirected to a different app.
- **[NEW] Neutral user address**: Local conversational replies and the AI instruction no longer assume a user's age or use elder-specific forms of address. The default saved user label is neutral, and memory context uses “ผู้ใช้”.
- **[NEW] Strict AI command prompt**: The AI instruction now makes the spoken command's named app, person, or destination the sole target. It may use only packages in the live installed-app list, must issue one matching ACTION for a device command, and must report uncertainty rather than substitute or claim success.
- **[NEW] Emergency prompt guardrail**: Explicit emergency requests such as `ช่วยด้วย`, `ฉุกเฉิน`, `หกล้ม`, `หายใจไม่ออก`, `เจ็บหน้าอก`, or `เรียกรถพยาบาล` are directed to the local SOS system immediately, without AI delay. The AI must not claim it can hear a call recipient or converse with them; SOS instead contacts the configured emergency contact, sends a location-bearing SMS when permitted, and places the emergency call.
- **[NEW] SOS vibration and call order**: SOS now gives three short vibration pulses as immediate tactile feedback and starts the emergency call at once, without waiting for the vibration or location lookup. The location lookup continues in parallel and sends the emergency SMS afterward when permission allows.
- **[NEW] Applied learning for app choices**: The assistant now turns every successful AI-selected app opening into an encrypted on-device voice key. This means an unclear pronunciation that was successfully matched once is used as a direct local match next time. Caregiver-defined keys were migrated from the former regular preference to encrypted storage on first use. The system retains text and success mappings only; it does not retain raw microphone recordings or send this learning history elsewhere.
- **[NEW] Spoken Thai rank expansion**: Before speech output, common police and military abbreviations are expanded to their full Thai rank names for clearer TTS, including incoming caller announcements.

- Installed package: `com.example.nongkanvelaassistant`.
- Latest verified phone install time: `2026-07-20 22:11:46` (this value changes after each later install; verify it again before work).
- The app is the current default phone app on the connected Android phone.
- Opening the app shows the voice assistant. The user taps the standard microphone icon and can say `โทรหา <ชื่อ>`; names are resolved from the device contacts.
- The manual contacts list remains only as a dial-screen fallback, not the launch screen.
- In-call controls use large standard icons: answer, end call, microphone mute, and speaker.
- Phone-related screens are locked to portrait orientation.
- Incoming saved contacts are spoken by name. English names use an English TTS voice when installed on the device.
- Incoming numbers not in device contacts are announced once: `สายจากหมายเลข … ไม่อยู่ในรายชื่อค่ะ หากไม่แน่ใจ ไม่ต้องรับสาย` and the in-call screen displays a yellow warning. Green answer and red decline remain unchanged.
- **[NEW] Memory System**: The assistant can remember user-provided facts (e.g. "จำไว้ว่ายาอยู่บนโต๊ะ"). Memories are stored securely via `EncryptedSharedPreferences`, categorized automatically, and contextually injected into Groq's System Prompt for smart recall.
- Users can view and delete memories via the Brain (🧠) icon in the top UI bar.
- **[NEW] Voice command execution repair**: `TAKE_PHOTO` opens the app's CameraX capture activity. Voice actions for maps/navigation, web/image/YouTube searches, timers, device-settings screens, Bluetooth settings, and SMS composition now have matching Android intent handlers. The AI prompt defaults to one concise, action-focused sentence.
- **[NEW] Reminder labels**: Reminder summaries no longer expose internal category labels such as `(ทั่วไป)`; kind data remains stored for scheduling and bot logic.
- **[NEW] Elder voice and memory**: Speech recognition requests a 12-second minimum listening window and longer silence thresholds for slower speakers. Memory commands accept `จำว่า...`, `จำไว้ว่า...`, `อยากให้จำว่า...`, and similar phrases, then confirm the exact saved fact.
- **[NEW] Alarm ownership and cancellation**: Voice-created alarms now use Nong Kanvela's scheduler rather than the external Clock app, so “ปิดนาฬิกาปลุกทุกเวลา” cancels the same alarms it creates. Previously created alarms in another Clock app remain outside the app's control.
- **[NEW] Control-verb foundation**: `เปิด`, `ปิด`, `ยกเลิก`, `ลบ`, and `หยุด` are routed before setup parsers. Cancellation never becomes a creation command; flashlights, Bluetooth, Wi-Fi, and scheduled items follow this priority. A unit test covers `ปิดนาฬิกาปลุกทุกเวลา`.
- **[ROLLED BACK] Noise-reduced voice input**: Direct PCM injection made the connected phone's recognizer remain on “กำลังฟัง”; it has been removed in favor of the stable platform microphone path. Do not re-enable without an on-device recognition test.
- **[NEW] Neutral list wording**: Reminder and memory queries now use neutral wording; they do not address the user as “คุณตาคุณยาย/คุณยาย” or instruct them unnecessarily.
- **[NEW] AI-first conversation**: When a Groq key is configured, AI now interprets ordinary speech before local keyword fallback. Emergency and explicit memory actions remain local for reliability.
- **[NEW] Duplicate contact results**: Explicit contact searches use a local, quiet result list for one or more matches: up to five numbered name-and-number entries, without slash-separated clutter or TTS.
- **[NEW] Phone-number lookup safety**: Requests to find or show a number are always local display-only lookups. Calls require the explicit word `โทร`.
- **[NEW] Launcher icon**: Replaced the generic purple microphone with a teal caregiver face, sound waves, and heart icon across Android launcher densities. The Android 8+ adaptive-icon XML now points to the new bitmap rather than the old microphone vector.

## Key implementation files

- `app/src/main/java/com/example/nongkanvelaassistant/MainActivity.kt` — voice-first launch screen and microphone.
- `app/src/main/java/com/example/nongkanvelaassistant/ui/VoiceAssistantViewModel.kt` — voice command parsing and direct calls.
- `app/src/main/java/com/example/nongkanvelaassistant/data/LocalAssistantBot.kt` — offline command fallback and memory command routing.
- `app/src/main/java/com/example/nongkanvelaassistant/data/MemoryStorage.kt` — encrypted storage and contextual ranking for memories.
- `app/src/main/java/com/example/nongkanvelaassistant/ui/MemoryScreen.kt` — UI overlay to list and manage memories.
- `app/src/main/java/com/example/nongkanvelaassistant/ui/CameraCaptureActivity.kt` — CameraX photo capture and MediaStore save.
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

## Latest verification (2026-07-20)

- Fresh-install launch repair: `assembleDebug` completed successfully. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-22 07:33:18`. With both fine and coarse location permissions denied, `MainActivity` launched successfully and remained the top resumed activity; the crash buffer contained no new app crash.
- Hidden action-marker repair: `testDebugUnitTest` and `assembleDebug` completed successfully, including tests for unknown and unclosed markers. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-22 07:56:26`, and `MainActivity` launched successfully.
- Reminder truthfulness repair: `testDebugUnitTest` and `assembleDebug` completed successfully, including `มีรายการเตือนอะไรบ้าง` returning only the supplied local reminder summary. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-22 08:14:26`, and `MainActivity` launched successfully.
- User-controlled microphone change: `testDebugUnitTest` and `assembleDebug` completed successfully. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-22 08:18:30`, and `MainActivity` launched successfully. Next safe test: tap the microphone, say one phrase, then confirm the microphone is off after the answer; tap again for the next phrase.
- Memory feature removal: `assembleDebug` completed successfully. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-22 08:21:35`, and `MainActivity` launched successfully.

- `testDebugUnitTest` completed successfully after the deterministic app/map routing repair, including regressions for `เปิดไลน์` with LINE MAN installed and `ไปเซ็นทรัลเวสต์เกต`.
- `assembleDebug` and `testDebugUnitTest` completed successfully. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 20:39:14`. The device has both LINE (`jp.naver.line.android`) and LINE MAN installed; `เปิด LINE` is explicitly bound to LINE's package. Google Maps package `com.google.android.apps.maps` is installed.
- Continuous voice-session build: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 20:46:19`. The phone confirms `com.example.nongkanvelaassistant` remains the default dialer.
- On-device continuous-listening check: after a microphone tap and a silence/no-match cycle, the screen showed `ยังฟังอยู่นะคะ พูดได้เลยค่ะ` and the microphone state remained `กำลังฟัง`, confirming the intended automatic restart. The microphone can be tapped again to end the session.
- External-action voice-session update: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 20:51:13`. Any external activity action now stops the continuous voice session before launch.
- App-launch repair for wake phrases: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 20:57:58`. `อุ่นใจ เปิด 7-Eleven` is covered by a regression test and resolves directly to the installed 7-Eleven package (`asuk.com.android.app`), which was verified launchable on the phone.
- Spoken 7-Eleven recognition repair: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 20:59:17`. The regression test now uses the speech-like phrase `อุ่นใจ เปิดเซเว่นอีเลฟเว่น`; Thai recognition variants resolve to the 7-Eleven package.
- App voice-key repair: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 21:02:12`. A regression test confirms a saved key opens its assigned app package.
- Local behavior-learning foundation: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 21:04:22`. Recognized phrases are now stored locally in encrypted storage with frequency counts; raw audio is not retained.
- Automatic app-key learning: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 21:11:11`. Every app-opening phrase resolved locally is persisted as a private key for the selected package.
- Krungthai NEXT repair: `testDebugUnitTest` and `assembleDebug` passed. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 21:19:50`. `next`, `เน็กซ์`, `KTB`, and `กรุงไทย` now resolve directly to the installed Krungthai NEXT package (`ktbcs.netbank`).
- Complete app-catalog fallback: build and unit tests passed; the debug APK was installed on the connected phone after reconnection. Package `lastUpdateTime` is `2026-07-20 21:24:26`.
- Applied app-choice learning and encrypted voice-key migration: `testDebugUnitTest` and `assembleDebug` completed successfully. The debug APK was installed on the connected phone; package `lastUpdateTime` is `2026-07-20 21:46:55`.
- Thai police/military-rank speech expansion: `assembleDebug` completed successfully and the debug APK was installed; package `lastUpdateTime` was `2026-07-20 21:35:23`.
- Bitkub direct-launch repair: `testDebugUnitTest` and `assembleDebug` completed successfully. The exact voice-like phrase `เปิดบิทคับ` resolves to `com.bitkub`; the APK was installed on the connected phone with package `lastUpdateTime` `2026-07-20 21:55:10`.
- Portable standard app-catalog update: `testDebugUnitTest` and `assembleDebug` completed successfully. A test confirms `เปิดกสิกร` opens K PLUS only when its standard package is present. The debug APK was installed on the connected phone with package `lastUpdateTime` `2026-07-20 21:57:52`.
- Neutral user-address repair: `testDebugUnitTest` and `assembleDebug` completed successfully, including a conversation regression test. The debug APK was installed on the connected phone with package `lastUpdateTime` `2026-07-20 22:01:34`.
- Strict AI command prompt: `testDebugUnitTest` and `assembleDebug` completed successfully. The debug APK was installed on the connected phone with package `lastUpdateTime` `2026-07-20 22:03:07`.
- Emergency prompt guardrail: `testDebugUnitTest` and `assembleDebug` completed successfully, including an explicit `ช่วยด้วย` local-SOS regression test. The debug APK was installed on the connected phone with package `lastUpdateTime` `2026-07-20 22:07:06`.
- SOS vibration/call-order repair: `testDebugUnitTest` and `assembleDebug` completed successfully. The debug APK was installed on the connected phone with package `lastUpdateTime` `2026-07-20 22:11:46`. No SOS trigger test was initiated to avoid placing a real call or sending SMS.
- `testDebugUnitTest` completed successfully, including creation and cancellation tests for voice alarms.
- `assembleDebug` completed successfully.
- Debug APK installed on the connected phone; package `lastUpdateTime` is `2026-07-20 20:22:11`.
- Next safe test: say `ถ่ายรูป`, then confirm a new photo appears in Pictures/NongKanvelaAssistant. Also try `ค้นหารูปดอกไม้` and `นำทางไปโรงพยาบาล`.

## Safe next test

- Ask the user to place one real incoming test call from an unsaved number to confirm the spoken warning and yellow in-call warning. Do not initiate an external call without the user's direction.
