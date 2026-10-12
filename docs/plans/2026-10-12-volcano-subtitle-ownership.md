# Volcano subtitle ownership implementation plan

**Goal:** Preserve the current source block when an older Volcano translation
finishes, while retaining every correctly matched final.

**Architecture:** Keep ownership in the shared Volcano decoder/client. Retain
official START timestamps and correlate translation START with the exact source
block. Use existing identified subtitle events and the shared reducer's owner
guards; no platform implementation or new display setting.

**Tech stack:** Rust, existing protobuf decoder, bounded provider event queues,
shared subtitle reducer and actual JNI projection contracts.

## Evidence and scope

The official [simultaneous interpretation API documentation](https://docs.volcengine.com/docs/DoubaoVoice/SimultaneousInterpretation20APIAccessDocumentation?lang=zh)
provides `start_time` on source/translation START and the same start plus
`end_time` on END. Before this change, the decoder discarded START timing. Correctly matched
END intervals became an unidentified final pair, which could overwrite a
newer source draft. The focused accepted-wire probe demonstrates this state;
native frequency and visible reading impact remain separate verification.

The official document's [Protobuf archive](https://p9-arcosite.byteimg.com/tos-cn-i-goo7wpa0wc/6493ecf3887246e3a99968961f716499~tplv-goo7wpa0wc-image.image)
contains `protos/products/understanding/ast/ast_service.proto`: line 1 specifies
`proto3`, and lines 40–41 declare `int32 start_time = 5` and `int32 end_time = 6`
without `optional`. Its SHA-256 is
`1e30c61ef9c91d139afd3000f96beeb6ee02f27944f957227ab44c9e986ef576`.
The [official Protobuf presence rules](https://protobuf.dev/programming-guides/field_presence/)
define an omitted implicit numeric scalar as zero. START and END therefore use
zero for omitted time fields; retaining them as unknown would lose valid first
block correlation and hide its paired preview. RESPONSE remains an unowned
fragment unless an actual START already established its lane's identity.

The owning implementation is `shared/mimi-runtime/src/core/protocols/volcano_engine.rs`
and `shared/mimi-runtime/src/clients/volcano_engine_client.rs`. A minimal shared
`SourceBoundary` event also registers explicit source order reliably, because
draft coalescing can remove an owner before its final reaches the reducer.
Its core model/reducer, runtime model/controller and content-free diagnostic
adapters use the same event; actual JNI exercises the new tagged metadata shape
through `shared/realtime-display-contracts.json`.
Other providers retain their policies. Do not apply the independent-MT sentence
prefix heuristic to Volcano.

## Ownership rules

1. Preserve START/END `start_time_ms` and END `end_time_ms`, applying the official
   proto3 scalar default zero when omitted. Preserve explicit values and reject
   duplicate, malformed, negative and out-of-range fields. Do not give RESPONSE
   a zero block identity just because its time fields are omitted.
2. Allocate a local source block owner at a new START or first draft. Keep only
   bounded ordinal/time metadata, current draft buffers and the existing bounded
   pending final text. Translation drafts acquire an owner only through an exact
   known START time; never through whichever source happens to be current.
3. Publish a content-free source boundary at actual START/first draft. Its
   positive order combines a known input-clock start and a checked block sequence
   tie-break. Keep the bounded core registry sorted by that explicit order.
   Registration does not change text, its owner, or its final status. Publish
   actual source END with its identity before its paired translation. Pair
   finals by the existing official interval rules and retain the source owner in
   `SubtitleIdentifiedFinalPair`.
4. Resolve END ownership before clearing a lane. A delayed A END must preserve
   active B text and activity. Ignore duplicate active START without erasing its
   text, and reject retired/replayed boundaries. After an empty END, the same
   current owner and clock may resume if no source final is pending or durable;
   reuse its identity. This does not reopen an older or retired owner.
5. After publishing an older matched final, republish any current newer owned
   draft using the existing buffers, owner and content revision. The reliable
   final otherwise suppresses an earlier queued latest-value draft. Preserve
   current pending activity; do not modify generic queue policy.
6. A missing START event keeps the compatible unowned path until a correlation
   is proved. Internal synthetic unknown-time events remain supported; they do
   not represent scalar absence in a valid wire START/END. An internal END whose
   known end precedes the current source's known START gets
   an independent final owner ordered by that end; do not fabricate its missing
   START or bind a translation START to its END. When the current source is
   anonymous and END cannot prove its relation, preserve that buffer but retain
   legacy final presentation: protection from an unowned late final is not
   guaranteed. A lane opened by an actual anonymous START is not cleared by an
   unproved END. A lane with no START at all retains the legacy END barrier:
   clear its draft, finish the implicit block, and begin the next draft fresh
   without assigning the final's clock or owner to that block. Empty or unmatched
   tails never create a final pair. When source has a proved owner but translation
   has no START/owner, its paired preview is withheld until a matching final;
   attaching that translation to the current source would guess identity.
7. Bound source metadata and tombstones; Clear removes text and bindings and
   tombstones known old intervals/starts. Reconnect resets the owner epoch and
   metadata. Generation and content-revision guards remain authoritative.
8. RESPONSE carries no identity. After an old or retired START, suppress
   ambiguous RESPONSE fragments while retaining the current buffer until its
   known START resumes. This can delay a valid current fragment; claiming it
   belongs to the current owner would risk combining different source blocks.

## Verification

- Decoder: START/END omitted-zero equivalence, explicit values, malformed/duplicate
  fields, END timing and existing binary fixtures.
- Runtime: admitted and queued B before late A final; translation-first START;
  reordered finals; late END preserving B buffers; duplicate/retired START;
  source/translation-first omitted zero paired preview; late zero A before known
  B; internal synthetic unknown timing; Clear/reconnect; bounded maps/text/order;
  incomplete Stop tail. Empty zero-duration END cannot confirm text or prevent
  the same still-unconfirmed clock from resuming; pending/confirmed/older owners
  remain closed.
  Include content-free ordering admission during Stop and successive complete
  source/translation blocks whose wire stream omits every START.
- Shared projection: Original follows B, Bilingual never combines different
  owners, Translation keeps correctly owned work, and A remains durable without
  rewinding B. Use existing identified-event shapes through direct Rust and
  actual JNI rather than adding a second native rule.
- Run focused offline Rust tests with the existing shared Cargo target, runtime
  formatting and diff checks. Coordinate actual JNI checks with the Android
  agent. Root owns canonical/native verification, commit and publication.

Verification completed for product commit `f9201391`: 52 focused Volcano tests;
shared runtime 746 passed/1 ignored; core 85 unit and 4 contract tests; complete
canonical checks including 2,043 frontend tests. Android actual JNI focused
12/12 and Debug/Release JVM 203/203 each passed. Four optimized Android ABI
release builds, package alignment/license checks and lint completed (0 errors,
54 warnings). Frozen inputs were checked against the commit; original build
metadata remains `57ef8921` dirty rather than being relabeled a clean build.

The schema-correct zero-default follow-up is a later candidate and is not covered
by those `f9201391` build/native receipts. Its focused wire/controller regressions
and 23 shared timing cases require fresh direct Rust, actual JNI and canonical
verification before replacing those receipts.

A bounded same-source native Volcano follow-up admitted 30 source boundaries and
14 identified final pairs, displayed captions, completed normal Stop and sealed
1,607 trace entries with no recorded loss or write failure. This demonstrates
the actual adapter path. The earlier native excerpt did not prove the late-final
counterexample occurred; neither run establishes frequency, semantic accuracy,
controlled quality comparison or reading-comfort acceptance. Android's live
three-mode video validates the independent-MT candidate at `ed345c26`; the new
Volcano rule has JNI/build coverage without a new Android provider session.


The proto3 correction at `1ae919cf` passed 56 focused Volcano cases and the new
complete canonical checks (runtime 750 passed/1 ignored; core 85+4; desktop
570 passed/1 ignored; frontend 2,043). The 23 shared binary timing fixtures also
cross the actual JNI decoder: focused JVM 31/31, full Debug and Release 205/205
each, plus optimized four-ABI release builds and lint. The sealed 327-input
manifest matches that product commit; original Android metadata remains
`6b2bf5e8 dirty`, and the APK has no embedded Git revision claim.

A fresh signed macOS live run on that product source displayed same-owner
realtime source/translation before its first complete pair, admitted 15
identified finals, and completed normal Stop with 1,603/1,603 sealed trace
entries. Its 60-second silent effect clip has actual native caption pixels.
A preceding near-silent attempt is excluded. Native time-field presence is not
observable in this evidence schema, so omitted/explicit-zero equivalence is
proved by deterministic wire and actual JNI regressions, not inferred from the
native owner's suffix. Android's service clip remains the independent-MT run;
no new Android Volcano service, native frequency or comfort acceptance is claimed.


The equivalent JNI hex iterator correction `6c7193cd` addresses the newer
Rust 1.99 Clippy rule without allowing or weakening a check. It passed actual
Rust 1.88 JNI compilation, the full canonical pipeline, focused actual JNI 31
and rerun Debug/Release JVM 205 each, optimized four-ABI release and lint.
The new 327-input manifest matches that correction. Desktop runtime/core and
rendering files are unchanged, so the native clip retains its `1ae919cf` label.
