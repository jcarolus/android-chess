# Android Play dictation probe

These commands apply to the debug probe at commit `39bdf7d3`. Current builds expose text move entry in Accessibility preferences.

Use a disposable local game in a debug APK. The probe logs the field contents to logcat, so dictate only chess coordinates while testing.

```sh
./gradlew :app:installFossDebug
adb shell am start -n jwtc.android.chess/.play.PlayActivity --es dictation_probe observe
adb logcat -v time -s PlayDictationProbe:D
```

Start with `observe` to see what the selected Android keyboard sends. Use the keyboard microphone and say “e two e four.” Repeat with corrections and pauses. Record Android version, device, keyboard and version, TalkBack version, the IME event sequence, and what the keyboard displays as dictation ends.

Repeat with `--es dictation_probe apply` in a fresh Play Activity to let the experimental trigger make a move. Use a fresh Activity when changing modes. The trigger requires one successful IME commit whose payload matches the entire field and parses as a move. Composing updates and finish-composing events are logged. The probe accepts ASCII file letters, digit or English word ranks, case differences, whitespace between squares, and an optional English promotion piece. Compare manual typing, pasting and dictating the same move. Use `--es dictation_probe review` to test the confirmation dialog. Dismiss one review and verify that Play waits for fresh input.

- Dictate a legal move in a fresh game. Check that only one move appears and Play gives its usual feedback. Repeat with an illegal move and with a duplicate final commit. Check whether a new input connection was created between commits.
- Load a position with a pawn ready to promote. Pause between “e seven e eight” and “knight.” Check that the prefix leaves the pawn in place and record whether the keyboard commits the whole phrase or only the suffix.
- Change position, navigate history, rotate, or background Play while the keyboard still shows interim text. Record any late keyboard events and whether a move was applied.
- Play against the engine with TalkBack on. Observe focus and keyboard visibility when Play opens and when the engine returns the turn, with the keyboard initially open and initially closed. Compare the spoken focus with the logged input focus, accessibility focus events, and IME insets.

A whole-field commit is a hypothesis for this experiment. The Android API commits text to the editor and gives the IME control over composing regions. A keyboard can commit in chunks or use the same commit for typing, paste, or speech. Play clears the field and invalidates the old input connection when the position or turn eligibility changes. A keyboard could still deliver a late commit as fresh input over the new connection. Record those sequences before agreeing a completion rule for #274. [Android InputConnection reference](https://developer.android.com/reference/android/view/inputmethod/InputConnection)

The probe exercises the normal local game service and its move feedback. Record device findings and the agreed completion, normalization, and focus behavior in #273 before using the trigger in a production flow. If the keyboard traces offer no reliable completion signal, agree on a revised interaction with a human tester before implementing #274.

## Emulator smoke test

On the API 36 Google Play emulator, Play acquired input focus. With Gboard selected, `adb shell input text e2%se4` produced five single-character commits and no whole-field candidate. Accessibility services were disabled during this run. Real speech, TalkBack focus and keyboard visibility, and late or duplicate dictation delivery still need validation with a tester's preferred keyboard.
