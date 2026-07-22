# PLAN.md

Working plan for multi-step changes in `NongKanvelaAssistant`.

## Current-source rule

- This file contains historical plan addenda. They are not permission to restore an earlier approach.
- Before any edit, follow `HANDOFF.md` and inspect the current owner files. Preserve delivered behavior unless the user explicitly requests a change.

## Current repair (2026-07-22)

### Goal

- Prevent a fresh installation from closing immediately when Android has not yet granted location permission for the SOS foreground service.

### Files and verification

- Update `MainActivity.kt`, `EmergencyService.kt`, `ReminderBootReceiver.kt`, and `HANDOFF.md`.
- Build, install, launch on the connected phone, and confirm the app remains open; verify the package update timestamp.

### Rollback

- Revert only the location-permission guard around automatic emergency-service startup. No reminder or encrypted-storage formats change.

## Current repair (2026-07-22) — ACTION text leak

### Goal

- Never show or speak internal `[ACTION:…]` command markers to the user, including a marker the parser does not recognize.

### Files and verification

- Update `VoiceAssistantViewModel.kt`, focused unit tests, and `HANDOFF.md`.
- Run unit tests, build, install, and verify a response containing an unrecognized ACTION marker is presented without the marker.

### Rollback

- Revert only the final internal-marker sanitization; device-action parsing remains unchanged.

## Current repair (2026-07-22) — reminder truthfulness

### Goal

- Route reminder queries and commands through the on-device reminder store, never AI, so an answer cannot invent a reminder.

### Files and verification

- Update `VoiceAssistantViewModel.kt`, focused unit tests, and `HANDOFF.md`.
- Run unit tests, build, install, and verify the exact phrase `มีรายการเตือนอะไรบ้าง` takes the local path.

### Rollback

- Revert only the reminder-local routing guard. Stored reminder formats and scheduler behavior remain unchanged.

## Current repair (2026-07-22) — user-controlled microphone

### Goal

- Make voice capture one-shot and user-controlled: tapping the microphone starts listening; a result, silence, or an explicit tap stops it, with no automatic restart after speech or replies.

### Files and verification

- Update `VoiceAssistantViewModel.kt` and `HANDOFF.md`.
- Build, install, and on the phone verify the microphone does not restart after a no-match timeout or spoken reply.

### Rollback

- Restore the former continuous-session callbacks only; no stored data is affected.

## Current repair (2026-07-22) — remove memory feature

### Goal

- Remove the “remember/จำว่า” feature from voice handling, AI context, and the UI, leaving reminders as the only persistent user-created item.

### Files and verification

- Update `MainActivity.kt`, `VoiceAssistantViewModel.kt`, `LocalAssistantBot.kt`, `NongKanvelaRepository.kt`, and `HANDOFF.md`.
- Run unit tests, build, install, and verify the memory button is absent while reminder handling remains local.

### Rollback

- Restore the removed feature wiring only. Existing encrypted memory data is not erased by this change.

## Goal

- Make Nong Kanvela the user-selected default phone app, with an elderly-friendly in-call screen, direct speakerphone control, and access to the phone's existing contacts.

## Scope

- `CallSessionManager.kt`, `NongKanvelaInCallService.kt`, `CallActivity.kt`, `DialActivity.kt`, `MainActivity.kt`, `AndroidManifest.xml`

## Assumptions

- What should be treated as true for this change?

## Steps

1. Declare the dialer activity and in-call service required for the Android dialer role.
2. Provide a clear in-call screen with large, standard answer, end-call, mute, and speaker controls familiar to elderly users.
3. Route all in-call controls through an isolated call-session manager.
4. Add an explicit user-controlled request for the default dialer role.
5. Build, install, request the role on-device, and visually inspect the in-call UI without placing a real call.
6. Show the existing device contacts in the dial screen, with a large searchable list and one-tap selection.
7. Keep the contact list as a manual fallback only; the app icon must open the voice assistant so an elderly user can say “โทรหา <ชื่อ>”.
8. Replace the emoji microphone with the universal Material microphone icon.
9. For incoming calls only, announce the caller's saved contact name through text-to-speech; fall back to the phone number when no name is saved.
10. Read English caller names with an English TTS voice while keeping the surrounding announcement in Thai.
11. For incoming numbers not found in the device contacts, give one short Thai safety announcement and show a yellow warning while preserving standard green answer and red decline controls.
12. Lock the assistant, dial, and in-call screens to portrait orientation.

## Verification

- Build: assembleDebug
- Test: testDebugUnitTest
- Device check: confirm the phone sees the rebuilt package, can present the default-dialer permission screen, and can show the device contact list; do not place a real call.

## Rollback

- Revert the dialer-role manifest declarations and new call UI/service files; the existing system dialer remains available.

## Current repair addendum (2026-07-20)

### Deterministic app and map launch repair (2026-07-20)

**Goal:** Treat `เปิด` as a direct action verb wherever it appears in a spoken request, so a wake phrase or polite lead-in cannot send app opening to AI. Exact app names following it must open that exact app, without AI guessing or a needless clarification. Explicit directions such as `ไปเซ็นทรัลเวสต์เกต` must be handled locally and navigation must target Google Maps only.

**Files and verification:** Update `LocalAssistantBot.kt`, `VoiceAssistantViewModel.kt`, the local-command unit tests, and `HANDOFF.md`. Run unit tests, build and install; then test `เปิดไลน์`, `เปิดไลน์แมน`, spoken `เปิดเซเว่นอีเลฟเว่น`, and `ไปเซ็นทรัลเวสต์เกต` on the phone.

**Rollback:** Revert only this deterministic-routing addendum; no stored data or reminder format changes are involved.

### Continuous voice-session repair (2026-07-20)

**Goal:** A microphone tap starts an explicit continuous voice session. Silence/no-match restarts recognition after a short delay; `หยุดฟัง` or another microphone tap ends it. Recognition pauses while the assistant speaks, then resumes afterward, without creating an always-on background microphone. Launching an external app, map, settings page, or dialer must end the session before Android leaves this app.

**Files and verification:** Update `VoiceAssistantViewModel.kt`, focused unit tests where practical, and `HANDOFF.md`. Build, install, verify the install timestamp, then on device: tap the microphone, remain silent once, issue `เปิด LINE`, and say `หยุดฟัง`.

**Rollback:** Restore the former one-shot recognizer callbacks; this does not change stored data, contacts, or reminders.

### App voice-key repair (2026-07-20)

**Goal:** Let a caregiver or user save a short, easy-to-say key for any installed app, and resolve that key to the app package directly. Keys persist only on this device; they never depend on AI guessing.

**Files and verification:** Update `LocalAssistantBot.kt`, `VoiceAssistantViewModel.kt`, unit tests, and `HANDOFF.md`. Test `เรียกแอป <ชื่อแอป> ว่า <ชื่อสั้น>` followed by `เปิด <ชื่อสั้น>`.

### Local behavior-learning foundation (2026-07-20)

**Goal:** Persist recognized user phrases and usage frequency locally in encrypted storage, never raw microphone audio. This gives future command matching and caregiver-defined voice keys a private on-device history to adapt from.

**Files and verification:** Add `BehaviorLearningStorage.kt`, update `VoiceAssistantViewModel.kt` and `HANDOFF.md`, then build and install.

### Applied learning for app choices (2026-07-20)

**Goal:** When AI successfully identifies a spoken installed app, retain the spoken app name as an encrypted local voice key. The next request must launch the same package directly, without another AI guess. Migrate existing caregiver-created voice keys to the same encrypted storage.

**Files and verification:** Update `BehaviorLearningStorage.kt`, `VoiceAssistantViewModel.kt`, and `HANDOFF.md`; run unit tests, build, install, and verify the phone package update time.

**Rollback:** Remove only the automatic successful-match save and encrypted migration; the original app catalog and deterministic launcher behavior remain unchanged.

### Bitkub direct-launch repair (2026-07-20)

**Goal:** Bind Bitkub's installed package identity to English and Thai speech forms before general text corrections or AI routing, so `เปิดบิทคับ` always opens Bitkub.

**Files and verification:** Update `LocalAssistantBot.kt`, add an exact Thai phrase unit test, update `HANDOFF.md`, then build, install, and verify the package timestamp.

### Portable standard app catalog (2026-07-20)

**Goal:** Make common Thai app names work on every new installation without a caregiver first creating local aliases, while checking each target package against that specific phone's installed launcher apps before launch.

**Files and verification:** Update `LocalAssistantBot.kt`, add an installed-package regression test, update `HANDOFF.md`, then run unit tests, build, install, and verify the phone package timestamp.

### Neutral user-address repair (2026-07-20)

**Goal:** Remove age-assuming forms of address from local replies, memory context, defaults, and AI behavior guidance.

**Files and verification:** Update `LocalAssistantBot.kt`, `NongKanvelaRepository.kt`, `MemoryStorage.kt`, `VoiceAssistantViewModel.kt`, an exact conversational regression test, and `HANDOFF.md`; run unit tests, build, install, and verify the phone package timestamp.

### Strict AI command prompt (2026-07-20)

**Goal:** Instruct AI to treat named voice-command targets literally, allow actions only for installed app packages, return exactly one matching ACTION for device commands, and report uncertainty instead of substituting a target.

**Files and verification:** Update `NongKanvelaRepository.kt` and `HANDOFF.md`; run unit tests, build, install, and verify the phone package timestamp.

### Emergency prompt guardrail (2026-07-20)

**Goal:** Route explicit spoken emergency requests to the existing local SOS flow without AI delay, make the response concise, and forbid claims that the AI can hear or converse with the call recipient.

**Files and verification:** Update `NongKanvelaRepository.kt`, add an emergency-phrase local regression test, update `HANDOFF.md`, then run unit tests, build, install, and verify the phone package timestamp.

### SOS vibration and immediate-call repair (2026-07-20)

**Goal:** Give three short vibration pulses when SOS begins while placing the emergency call immediately, without waiting for the vibration or location lookup. Continue location lookup in parallel for the SMS.

**Files and verification:** Update `EmergencyService.kt` and `HANDOFF.md`; run unit tests, build, install, and verify the phone package timestamp. Do not trigger SOS on the connected phone without the user's explicit direction because it can place a real call and send SMS.

### Goal

- Restore voice-triggered photo capture and make assistant replies concise and action-oriented.

### Files to touch

- `ui/VoiceAssistantViewModel.kt`, `data/NongKanvelaRepository.kt`, `HANDOFF.md`

### Verification and rollback

- Build the debug APK, install it on the connected phone, confirm its package update time, then say “ถ่ายรูป” once.
- Rollback is limited to removing the `TAKE_PHOTO` action branch and the concise-response prompt rule.

## Reminder label repair (2026-07-20)

### Goal

- Hide internal reminder kinds such as `(ทั่วไป)` from spoken and displayed reminder summaries while retaining them for bot logic, storage, and scheduling.

### Files and verification

- Touch `ui/VoiceAssistantViewModel.kt` and `HANDOFF.md`; build, install, and ask “มีรายการเตือนอะไรบ้าง”.

## Elder voice and memory repair (2026-07-20)

### Goal

- Give slow speakers more silence time before recognition completes, and accept clear natural phrases for saving a memory with an explicit confirmation of what was saved.

### Files and verification

- Touch `ui/VoiceAssistantViewModel.kt`, `data/LocalAssistantBot.kt`, and `HANDOFF.md`; build, install, then test a deliberate pause while saying “จำว่า กุญแจอยู่ในลิ้นชัก”.

## Alarm-off intent repair (2026-07-20)

### Goal

- Recognize “ปิดนาฬิกาปลุกทุกเวลา” as cancellation and remove all alarms/reminders owned by this app, rather than asking for a new alarm time.

### Files and verification

- Touch `data/LocalAssistantBot.kt`, `ui/VoiceAssistantViewModel.kt`, and `HANDOFF.md`; build, install, then issue the exact Thai phrase.

## Control-verb foundation (2026-07-20)

### Goal

- Make `เปิด`, `ปิด`, and `ยกเลิก` deterministic, high-priority control verbs so a cancellation can never fall into a create/set flow.

### Files and verification

- Touch `data/LocalAssistantBot.kt`, `data/NongKanvelaRepository.kt`, an exact-phrase unit test, and `HANDOFF.md`; run unit tests, build, and install.

## Alarm ownership correction (2026-07-20)

### Goal

- Store alarms set by voice in Nong Kanvela's own scheduler, so the same assistant can truthfully create and cancel them.

### Files and verification

- Touch `data/LocalAssistantBot.kt`, its unit tests, and `HANDOFF.md`; test alarm creation and cancellation action strings, then build and install.

## Noise-reduced speech input (2026-07-20)

### Goal

- Capture microphone audio through Android's voice-communication path, with supported noise suppression and echo cancellation, before recognition.

### Files and verification

- Touch `ui/VoiceAssistantViewModel.kt` and `HANDOFF.md`; build, install, and test spoken commands with fan/TV noise present.

## Neutral reminder and memory wording (2026-07-20)

### Goal

- Remove “คุณตาคุณยาย/คุณยาย” from reminder and memory list responses, leaving only concise information or a neutral clarification.

## AI-first conversation routing (2026-07-20)

### Goal

- Use Groq to interpret ordinary Thai speech before falling back to keyword rules; retain local handling only for emergency and explicit memory operations.

## Duplicate contact results (2026-07-20)

### Goal

- Display duplicate contact matches as a quiet, numbered list instead of a spoken slash-separated sentence.

## Phone-number lookup safety (2026-07-20)

### Goal

- Never place a call for “ขอเบอร์/หาเบอร์/เบอร์ของ”; calls require the explicit Thai verb “โทร”.
