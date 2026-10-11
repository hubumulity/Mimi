# Android settings and live caption controls

The user reported missing live display-mode controls, fixed expanded placement,
poor settings organization, missing provider artwork and an oversized immersive
exit. They explicitly requested width adjustment and a mobile settings redesign.

## Native UI

Use three short settings tabs: Services, Captions and General. Keep service
selection/configuration and its ongoing session lock in Services; put the sample
preview and everyday appearance controls together in Captions; keep interface
language and diagnostics in General. Preserve existing neutral tokens and brand
artwork. Add provider marks to Home and the expanded overlay as well as keeping
those in service editors and pickers.

One native appearance component owns display mode, width, font size, color,
background opacity and caption opacity. Both Settings and the floating panel use
it. Preferences update existing service views immediately, with sanitized failure
feedback and no session restart. Original/translation/bilingual are presentation
choices; they never change recognition, provider requests, shared subtitle state
or retained paired history. Recognition configured without translation continues
to display original text. Existing Android bilingual presentation remains the
default.

Expanded captions have a dedicated drag handle, separate from scrolling and
buttons. Compact and expanded positions are saved separately in dp; safe-frame
clamps never overwrite saved placement. Width is adjustable in settings and the
floating controls, with a direct resize handle in the expanded panel. Keep usable
minimum widths and fit long controls by stacking them. The immersive exit uses a
small visual icon inside a 48dp touch target and remains adjacent to captions.

While a session is running, show the service-edit restriction beside a visible
Stop action. Opening settings never silently stops playback/capture. Appearance
continues to be editable. Update the guard when the session ends.

## Verification and delivery

Add native interaction acceptance for display filtering, saved width/live view
geometry, expanded dragging/resizing, compact anchor preservation, service lock
and stop, and immersive exit. Review all eight locales, large fonts, narrow and
landscape bounds, light/dark themes, Home, settings and editors. Synthetic native
checks do not replace real capture/provider evidence.

Use a separate stable Android debug app ID for development acceptance, preserving
the installed release app and its encrypted credentials. CI packages the debug
app and instrumentation APK together; shared Rust/JNI and APK verification remain
mandatory. Run the canonical repository check and Android CI before presenting a
reviewable PR. This mobile redesign needs user confirmation before merging main;
it does not replace already-published v1.5.23 assets.
