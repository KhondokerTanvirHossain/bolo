# ScreenPhoneNumber

The phone route of SignInButtons (US-8.1). It is one field on one screen.

- The country code **+880** is fixed and shown as a prefix, so the learner types only the 10 digits they know. Group the digits as they type (1712 345 678); accept a leading 0 and strip it.
- The numeric keyboard opens on arrival. **Send code** sits directly above it and is enabled once 10 digits are in.
- One reassurance line sits above the button: the number is used only for signing in.
- If the number is not a Bangladeshi mobile number, say so under the field in `ink`: "That doesn't look like a mobile number. Check the digits after +880." Never use red.
