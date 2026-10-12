# Caption reading stability trial

The user reports that changing translations and newly arriving words pull their
attention back to text already read. Alibaba and Tencent are the first trial
routes; the Alibaba-recognition/DeepL combination is deferred. Keep the report
as feedback, not a measured ranking of provider accuracy.

Build this trial from main `b04e394b` with the existing shared complete-prefix
candidate (`ed345c26`). Do not import unrelated Volcano work or release changes.
The independent-MT policy reduces translation of incomplete tails; its bounded
six-second rescue remains a revisable preview, not a final or accuracy promise.
Tencent's integrated source/translation events retain their provider boundaries.
Changing Tencent's MID to a reliable pair would require preserving coalescing,
final barriers and Original mode, so it is outside this display trial.

For cross-language bilingual live reading, reserve fixed source and translation
slots from the selected viewport and typography before the second lane arrives.
Cap each current slot at three complete lines; small viewports still reduce this
budget. The reservation must leave a tall window available for actual history.
A short or absent lane does not lend its slot to the other. Anchor the original to
its slot bottom. A translation that fits starts at its fixed top edge, keeping
already-read lines in place as it grows; overflow follows complete tail lines
inside that slot. This also avoids separating two short phrases across a tall
window. Retain these slots
through the current confirmation and Stop. Reserve selected metadata before
confirmation, including the selected divider space in the desktop/native
minimum-height contract. Preserve current order, font sizes, colors, source identities and
full accessible text. A known identical-language pair may use one lane.

The first native user trial exposed an excessive-space failure even though the
short synthetic glyph bounds passed: uncapped current slots consumed nearly the
whole viewport, placed the confirmation time far above the original and pushed
older history out. Preserve that failed trial in the evidence boundary. The
three-line cap applies through live/confirmation/Stop; it is a compact current
reading area rather than an entire-window reservation. Long current text follows
its last three complete lines, with full text available in deliberate reading.

Keep normal activity visible in the existing language capsule, including
listening and recognition before a translation request starts. Reuse actual
request-pending translation feedback; do not label complete-prefix waiting as an
HTTP request in progress. Reserve the maximum localized width of the three
active labels so their changes do not move the language summary. Reuse existing
copy and styling, including when motion is disabled.

Single-language modes and deliberate history reading retain their existing
full-text behavior. New utterances, resize and explicit display changes remain
valid layout transitions. Do not freeze screen coordinates or hide corrections,
add a product switch, copy the reference application's continuation threshold,
or add animation to conceal semantic revisions. Platform UI stays native; ASR,
MT, final ordering and cancellation remain in the existing shared Rust pipeline.

Use the same synthetic cases for delayed translation, short/long lanes, short
pause continuation, correction, confirmation, next utterance and Clear. Separate
text replacements from glyph movement. The prior 700px/20px/144px component case
moved the unchanged original's last glyph upward 56px when translation arrived;
this is a mocked component observation, not native or provider evidence.

Verify focused layout cases, complete-prefix direct Rust/actual JNI contracts,
canonical checks and Android native/JVM checks. Inspect the stable signed Dev
without capture or provider requests. The reusable local component replay is
`scripts/fixtures/caption-reading.html`; it is not part of the production app.
Leave the candidate available for user trial after restoring ordinary idle mode;
retain independent credentials/signing and preserve the formal app. No version,
tag, release, paid test, audio capture or private-content retention is authorized
by this trial. Continuous live reading and actual semantic improvement remain
unverified until a comparable authorized session/user trial supplies evidence.
