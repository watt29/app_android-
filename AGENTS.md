# AGENTS.md

README สำหรับ AI ของโปรเจกต์ `NongKanvelaAssistant`

## Tech Stack

- Android app, Kotlin, Jetpack Compose, Material 3
- Android Gradle Plugin 9.0.1, Kotlin 2.3.20
- State flow / ViewModel / AndroidViewModel
- Retrofit 2.11, OkHttp 4.12, Gson
- Security Crypto `EncryptedSharedPreferences`
- Play Services Location, Places SDK, ML Kit entity extraction
- CameraX, SpeechRecognizer, TextToSpeech
- Navigation3

## Build / Test / Lint

- Build: `.\gradlew.bat assembleDebug`
- Unit test: `.\gradlew.bat testDebugUnitTest`
- Lint: `.\gradlew.bat lintDebug`
- Install to phone: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- If Java is needed, use `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`

## Planning

- For multi-step or risky changes, write the plan in `PLAN.md` before editing code.
- Keep the plan short: goal, files to touch, verification, and rollback notes.
- Update the plan when scope changes.

## Architecture

- `MainActivity.kt` is the Compose entry point and screen host.
- `ui/VoiceAssistantViewModel.kt` owns UI state, speech flow, and AI orchestration.
- `data/NongKanvelaRepository.kt` owns Groq calls, proxy fallback, and response handling.
- `data/GroqApiService.kt` is the Groq Retrofit client and request models.
- `data/SecureSettingsStore.kt` stores Groq keys in encrypted shared prefs.
- `data/ReminderStorage.kt` and `data/ReminderScheduler.kt` own reminder persistence and alarms.
- `data/LocalAssistantBot.kt` contains local command fallback logic for device actions.
- Keep network / storage code out of composables.

## Coding Conventions

- Use Kotlin + Compose functions, not XML UI for new screens.
- Keep app logic in ViewModel or `data/`, not in composables.
- Prefer `StateFlow` for observable state.
- Keep UI text short, friendly, and Thai-first.
- Show, don't tell:

```kotlin
// Good
Card(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
)
```

```kotlin
// Bad
Card(
    modifier = Modifier.padding(4.dp),
)
```

- Preserve existing patterns:

```kotlin
val uiState by viewModel.uiState.collectAsState()
```

```kotlin
viewModel.setGroqKeys(keysCsv.trim())
```

```kotlin
val request = Request.Builder()
    .url("https://api.groq.com/openai/v1/chat/completions")
    .addHeader("Authorization", "Bearer $key")
    .post(body)
    .build()
```

- Use `rememberSaveable` for simple UI input state when the value should survive rotation.
- Keep API keys trimmed, never logged, and never echoed back to the user.

## Constraints & Boundaries

- Do not hardcode secrets in source files.
- Do not commit or print values from `local.properties`, `keystore.properties`, or API keys.
- Do not replace encrypted storage with plain `SharedPreferences`.
- Do not change reminder storage formats without a migration path.
- Do not move Groq request logic into the UI layer.
- Do not delete or rewrite unrelated user changes.
- Avoid destructive shell commands.

## Git Workflow

- If this project is in a git checkout, use branch-per-ask with short feature branches such as `feat/groq-ui`, `fix/reminder-parser`, `chore/docs`.
- Use Conventional Commits when committing:

```text
feat: add Groq key settings dialog
fix: rotate Groq keys after success
chore: update AGENTS guidance
```

- Prefer small commits or squash merge when the change is self-contained.
- Review before merge when possible; this workspace does not currently expose a git repo, so branch flow is a future convention rather than an active one here.

## Practical Notes

## AI Continuity (Required Before Any Edit)

- Read `HANDOFF.md` and `PLAN.md` before changing code. They describe the current delivered behavior and unfinished work.
- Treat the connected phone as the delivery target, not an old APK in the workspace. Before claiming a change is current, run `adb shell dumpsys package com.example.nongkanvelaassistant` and compare `lastUpdateTime` after installation.
- Inspect the existing owner files before editing. Do not recreate an older implementation from chat memory or overwrite current work with a stale version.
- After every material change, update `HANDOFF.md` with: the user-visible behavior, files changed, verification completed, and a concise next safe test.
- Preserve unrelated dirty-worktree changes. If the current state cannot be established, stop and report that uncertainty rather than guessing.

- Groq uses the OpenAI-compatible chat completions endpoint.
- The app already supports multiple Groq keys; rotate keys on success and retryable failure.
- If a task touches AI behavior, update or add tests for the exact user phrase that triggered the change.
- If a task touches phone behavior, verify the APK on device after rebuilding.
- This project is Android-first; if a future web UI is added, split the audit rules by platform instead of mixing them here.

## Verify Loop

- For UI work, do not stop at compile success.
- Build the screen, run it on device or emulator, capture a screenshot, and compare it to the reference image or intended design.
- Fix spacing, alignment, truncation, colors, and missing states in small iterations.
- If the design source is unclear, upscale or crop the relevant region before comparing.
- Repeat the screenshot-and-fix loop until the UI is visually close to the target.
- Aim for the same proportions and visual hierarchy, not just functional correctness.
- For detailed screenshot comparison work, use the `$ui-audit` skill.
