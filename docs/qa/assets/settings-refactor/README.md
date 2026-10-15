# Settings refactor review screenshots

Actual Chromium renders of `scripts/fixtures/settings-layout.html`, at 760 ×
1000 CSS pixels. All profiles, workspace IDs, credential-presence flags and
provider responses are synthetic; saved keys remain masked. These illustrate
layout and flow, not a successful real provider request or native audio capture.

- `subtitles-light.png`: flat first-page controls and a separate subtitle preview.
- `audio-languages-light.png`: audio sources, the active configuration and one language editor.
- `livetranslate-light.png`: model setup, workspace, stable connection inputs and presets.
- `credentials-dark.png`: the same field/action rhythm with a saved integrated-service key.

The associated review checks cover eight locales, both themes and widths 520,
760 and 952. Interactive checks exercise cancellation, navigation with a draft,
failed saves, separate connection checks, custom model-only saves, incomplete
workspace setup and paused-session guards. No real key reads, provider requests,
capture, recording or permissions changes were made.
