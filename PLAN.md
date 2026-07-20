# PLAN.md

Working plan for multi-step changes in `NongKanvelaAssistant`.

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
