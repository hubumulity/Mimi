# Keep embedded pickers open during unrelated subtitle scrolling

On Wayland, the detected GDK backend selects embedded overlay controls. The
subtitle Timeline and control panel then share a document, with the panel
beside the timeline. An open Select previously dismissed on every document
scroll outside its own portaled menu. A newly arriving caption could therefore
close a language or application picker while the user was choosing an option.

Dismiss only when scrolling can move the trigger: an Element ancestor of the
trigger, document or window scrolling. Ignore unrelated Element scrolls,
including SVG targets, and preserve scrolling within the menu or its text.
Unknown non-Element targets retain conservative dismissal. Capture scroll at
window so both document and Window targets are handled without passing a Window
to Node.contains. Resize, blur, outside focus/pointer input and menu positioning
retain their existing behavior. Scrolling an actual ancestor still closes the
fixed popup rather than leaving it at an outdated anchor position.

The maintained regression mounts the real Select beside the real Timeline,
opens a language menu, then adds a caption. The real timeline requests scrolling;
jsdom supplies only the asynchronous non-bubbling scroll delivery that its
layout implementation lacks. The event target is the sibling timeline, which
contains neither trigger nor popup. The popup and focused combobox must survive,
then keyboard arrows/Enter must still choose the other language.

Additional cases cover HTML/SVG siblings, menu/text targets, the real trigger
ancestor, outside text, document/window, resize and blur. Against the old source,
four cases fail: the two unrelated Element targets, explicit Window scroll and
the caption integration. The quick frontend check passes typecheck, selected
test lint, provider-copy and diff checks plus 73 cases across Select, Timeline
scroll and embedded-control tests; production Select lint also passes.
These regressions establish event policy with synthetic
scroll geometry; they do not establish native Wayland transport or compositor
behavior. Actual browser scrolling and signed platform interaction remain
separate acceptance checks. No capture, provider or private configuration is used.
