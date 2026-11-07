# CodeInput

Six cells for the SMS code (US-8.1, US-8.3).

**Needs:** the digits so far, and an optional note line under the cells.

**Use**
- Build it as one numeric text field (number keyboard, one-time-code autofill) drawn as six cells, so paste, autofill and TalkBack all work as one field. TalkBack reads it as "6-digit code, 3 entered".
- Filled cells get an `ink` outline, the next cell the `focus-ring` outline and caret, empty cells `line-strong`.
- The note line carries status in `ink-secondary`: auto-reading, "That code didn't match. Try again.", or the 15-minute pause. Never red, never a shake.

**Behaviour:** each digit drops in with a 120 ms fade. On the sixth digit, check at once; on a miss, clear all cells together and put the caret back in the first.
