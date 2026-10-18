# Isolate embedded controls from subtitle-only parent renders

The native embedded-control path renders `OverlayControlWindow` inside
`OverlayWindow`. The parent subscribes to the complete session snapshot, so
every replacement draft rendered its child again even when all of the child's
primitive session selectors, settings and native mode remained unchanged.
Each render rebuilt the control model; an open panel also recreated its
provider/language options and guidance. Separately mounted control windows did
not have this parent-driven work, so the existing sibling-window regression
did not exercise the affected path.

Wrap only `OverlayControlWindow` in React's ordinary memo boundary. The sole
prop is the stable `embedded` boolean. Keep its existing store, native-mode,
shortcut, measurement and local-state subscriptions as the owners of their
updates. Do not memoize the overlay, change subtitle stabilization, cache
provider policy or alter popup observation. Add the same explicit effective
UI-language subscription already used by memoized subtitle lanes: this window
must still refresh localized copy when an App render cannot cross the memo
boundary. Selecting System reads the current navigator language; this does
not introduce an operating-system language-change notification contract.

The formal regression mounts the actual OverlayWindow, control window, panel,
capsule and Timeline with synthetic native IPC. Pass-through spies count real
parent chrome and control-model computations. In hidden, island and panel
modes, fifty source/translation/latency snapshots each render the parent fifty
times. The old implementation additionally computes the control model at
least fifty times in every mode; the memoized implementation adds zero calls.
The existing subtitle stabilizer still publishes the latest draft, and surface,
panel, picker and open portal identities survive the update burst.

Independent-update checks cover all eight UI languages without settings
replacement, selecting System with a different navigator language, static
closed-panel copy, target-language and pulse-style settings, paused/stopping
session signals, disabled controls, native mode transitions, late shortcut
discovery, island ResizeObserver reports and native listener cleanup. Native
IPC and DOM geometry are fixtures; these tests establish work counts and
React behavior, not native Wayland hit testing, production CPU, frame rate or
end-to-end latency. The separate body-wide popup MutationObserver candidate
remains unchanged pending geometry evidence and ancestor/portal controls.

The affected quick frontend check passes provider-copy verification,
typecheck, selected lint and diff checks, plus 199 related regressions in
thirteen files. Independent read-only review found no remaining blockers.
No browser preview, Vite build, native build or full suite ran in this worktree;
the integrating revision still needs its own acceptance.
