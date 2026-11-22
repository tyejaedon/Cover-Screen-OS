# `keyboard.voice` Package

The `keyboard.voice` package provides Cover-Screen-OS's **on-device speech-to-text dictation helper** across every cover keyboard surface (system IME, accessibility overlay keyboard, and app-drawer search).

---

## 1. What This Folder Does

Typing long replies on a 1.9"–3.6" cover screen with one thumb can take effort. This package lets users tap a Microphone button on the keyboard top bar (`CoverKeyboardTopBar`), speak naturally, and drop clean spaced text straight into the cursor position—without leaving sensitive fields exposed.

It handles three things cleanly:
1. **Main-Thread Lifecycle Safety (`CoverVoiceDictationController`)**: Wraps Android's `SpeechRecognizer`, dispatches calls on `Looper.getMainLooper()`, and destroys the recognizer binder in `DisposableEffect.onDispose`.
2. **Permission & Availability Checks**: Verifies `Manifest.permission.RECORD_AUDIO` and `SpeechRecognizer.isRecognitionAvailable(context)` (declared under manifest `<queries>`) before opening the mic.
3. **Smart Phrase Spacing (`formatDictatedPhrase`)**: Trims raw recognizer output and automatically prepends a space when the text before the cursor ends with a non-whitespace character.

---

## 2. File-by-File Guide

### `CoverVoiceDictationController.kt`
- **In plain terms**: The voice-typing bridge connecting the Microphone icon in `CoverKeyboardTopBar` to Android's speech recognizer.
- **Key types & state**:
  - `CoverVoiceDictationController`: Manages `SpeechRecognizer`, exposes `isListening`, translates speech errors into concise status messages, and cleans up in `destroy()`.
  - `VoiceDictationUiHandle`: Immutable Compose state bundle (`isListening`, `onToggle: (() -> Unit)?`); `onToggle` is `null` on password or numeric PIN fields so the Mic button disappears automatically.
  - `rememberVoiceDictationController`: Compose wrapper scoping `CoverVoiceDictationController` to the keyboard lifetime.
  - `formatDictatedPhrase`: Pure helper formatting spacing around recognized phrases.

---

## 3. Works Hand-in-Hand With

| Module | Relationship | Link |
| --- | --- | --- |
| `:keyboard-floris` | Displays the Mic button in `CoverKeyboardTopBar` using `isVoiceInputActive` and `onVoiceInputToggle` | [floris](../../../../../../../../../keyboard-floris/src/main/java/com/tyejaedon/coverscreenos/keyboard/floris/README.md) |
| `ime` | Enables dictation in `CoverT9InputMethodService` for non-sensitive fields | [ime](../../ime/README.md) |
| `overlay/input` | Inserts dictated phrases into `CoverInputSessionManager.commitDictatedText` | [overlay/input](../../overlay/input/README.md) |
| `ui/launcher` | Wires dictation into `SearchFlorisKeyboardHost` | [ui/launcher](../../ui/launcher/README.md) |
