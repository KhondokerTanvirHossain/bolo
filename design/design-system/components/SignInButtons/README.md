# SignInButtons

The two ways to save progress (ACC-1): **Continue with Google** and **Use phone number**, with a quiet **Not now** under them when skipping is allowed.

**Needs:** nothing but the context; the labels are fixed.

**Use**
- The Google button follows Google's sign-in branding: the full-colour G, the label "Continue with Google", and Google's own fill, outline and label colours (`provider-fill`, `provider-line`, `provider-ink`, light by Day and dark by Night). The capsule shape is allowed by Google's rules and matches Bolo's buttons. Never recolour it mustard.
- Because of that, a screen with SignInButtons has no `sun` button. Google leads by position, phone follows as a `secondary` button, and Not now is `quiet-neutral`.
- On tap, Google opens Android's Credential Manager sheet; phone goes to ScreenPhoneNumber.

**Behaviour:** press scales to 0.97 like every button. While Google's sheet is open, the screen stays still behind it.
