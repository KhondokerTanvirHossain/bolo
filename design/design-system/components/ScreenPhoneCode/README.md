# ScreenPhoneCode

CodeInput on its own screen (US-8.1, US-8.3). There is no submit button: the code checks itself when the sixth digit lands.

- Use Android's SMS Retriever / User Consent API so the code fills itself where possible; the note "Reading the code from your messages…" shows while it waits. The learner can always type over it.
- **Resend code** is disabled for 30 s with a live countdown, then becomes a quiet `sun-ink` link. After 2 resends, add a secondary **Try Google instead**.
- **Code didn't match:** clear the cells, keep the keyboard up, and show "That code didn't match. Try again." in `ink` under the cells. No red, no shake.
- **After 5 tries:** replace the cells with "Let's pause for 15 minutes" and a countdown, plus **Try Google instead**.
- On success, go straight on: ScreenRestore if the account already has data, otherwise the next step of the flow.
