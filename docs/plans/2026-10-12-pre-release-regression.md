# Mimi 1.5.24 pre-release regression plan

**Goal:** Recheck merged main across Android screen changes and desktop use,
repair evidenced regressions, and prepare the next patch without publishing.

**Architecture:** Keep the production shared Rust core/runtime and native
adapters. Extend existing native acceptance checks where fixed-window sampling
misses live geometry changes; do not introduce a parallel subtitle reducer.
Preserve independent credentials, chosen appearance, recording defaults and
stable app identities. Private screenshots, clips and diagnostics stay outside
Git.

**Tech stack:** Rust/core/JNI, Android API 35 native instrumentation, Tauri/React,
signed macOS Dev, and Windows/Linux CI.

## 1. Establish the baseline

- Start from remote main `09d12d03`, in the existing dedicated worktree. Record
  source revision, dirty state, installed app identities and stopped capture.
- Public latest is v1.5.23. Prepare 1.5.24 only after regression results; increase
  Android versionCode and synchronize package/lockfile, Cargo and Tauri versions.
- Branch/main CI is permitted. Tags, GitHub draft/public Releases, release
  workflow dispatches and public updater changes are excluded by the request.

## 2. Android native regression

Files: `android/app/src/androidTest/java/app/yuxino/mimi/android/CaptionStreamingChecks.kt`,
`CaptionLiveReadingChecks.kt`, `CaptionPreviewParityChecks.kt`,
`OverlayInteractionChecks.kt`, and `UiSmokeInstrumentation.kt` as needed.

- Run the existing streaming, preview and actual gesture checks at normal and
  narrow widths, light/dark appearance and larger system font scale. Cover all
  three modes, eight writing-system fixtures, height endpoints and rotation.
- Extend live sampling with an explicit screen-change mode: wait for native
  geometry after each intentional transition, then check complete visible glyphs,
  native tail following, confirmed/preview handoff, actual AUDIO and continuing
  caption draws. Do not count intended resize or transition frames as unexpected
  content-driven height changes; assert restored saved geometry separately.
- During the same public video, repeat portrait → landscape → immersion → normal
  → portrait in every display mode; resize and return to the chosen height.
  Inspect actual fullscreen entry/exit separately. Verify edits and rotation do
  not persist accidental dimensions or alter unrelated color/opacity choices.
- Exercise quick-editor open/close during playback, no-audio waiting, normal
  Stop and fresh-consent restart. Restore on-device preferences byte for byte
  and remove the temporary acceptance package after all checks.
- Use authorized saved Ali/Gemini/Volcano/Baidu/Tencent services as useful;
  OpenAI remains excluded. Keep Mac output muted.

## 3. Desktop regression of merged main

Files: `scripts/dev-app.sh`, `scripts/verify-settings-layout.js`, existing overlay
fixtures and settings/profile regressions; product files only for an evidenced bug.

- Run `./scripts/check.sh`, then serialize the canonical Dev build and inspect
  Settings, native tray, overlay and detached controls. UI-only mode remains
  credential/network/capture free.
- Check Chinese/English/Japanese, normal/minimum widths, long content, empty,
  error, paused, collapsed, translating and long-caption states. Verify profile
  use/edit/copy/delete, cancel and repeated actions in safe fixtures.
- In real system-audio playback, cover all three display modes, native resize,
  pause/resume, clear and Stop/restart. Keep microphone and persistence off;
  restore the original service and appearance after test completion.
- Keep Windows/Linux automation and package smoke evidence distinct from manual
  native macOS acceptance.

## 4. Repair, rerun and prepare

- A new failure must retain its sample, locate the owning boundary, receive a
  focused regression and pass the same case again before broader retesting.
- Run `python3 -B scripts/check-shared-runtime.py`, direct Rust and actual JNI
  suites for shared changes; preserve all ABI/build failures as real failures.
- Record revision-specific results/limits in `docs/development/integration-runs.md`.
  Promote only a new verified reusable lesson to its owning guide.
- Prepare concise user-facing English/Chinese notes in `docs/releases/v1.5.24.md`.
  Build/verify local artifacts with existing stable identities; never replace the
  formal installed app. Commit reviewed source before signed public-shaped builds.
- Review for keys/media/private paths, push task-owned commits, verify exact-head
  CI and merge per the user's existing authorization. Leave no version tag or
  Release, and retain only necessary evidence, current candidate artifacts and
  reusable caches.
