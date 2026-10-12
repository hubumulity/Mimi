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

1. Preserve optional START `start_time_ms`, including explicit zero. Do not infer
   a zero timestamp from an omitted field without schema evidence.
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
   text, and reject retired/replayed boundaries.
5. After publishing an older matched final, republish any current newer owned
   draft using the existing buffers, owner and content revision. The reliable
   final otherwise suppresses an earlier queued latest-value draft. Preserve
   current pending activity; do not modify generic queue policy.
6. Missing START/time keeps the compatible unowned path until a correlation is
   proved. An END whose known end precedes the current source's known START gets
   an independent final owner ordered by that end; do not fabricate its missing
   START or bind a translation START to its END. When the current source is
   anonymous and END cannot prove its relation, preserve that buffer but retain
   legacy final presentation: protection from an unowned late final is not
   guaranteed. A lane opened by an actual anonymous START is not cleared by an
   unproved END. A lane with no START at all retains the legacy END barrier:
   clear its draft, finish the implicit block, and begin the next draft fresh
   without assigning the final's clock or owner to that block. Empty or unmatched
   tails never create a final pair.
7. Bound source metadata and tombstones; Clear removes text and bindings and
   tombstones known old intervals/starts. Reconnect resets the owner epoch and
   metadata. Generation and content-revision guards remain authoritative.
8. RESPONSE carries no identity. After an old or retired START, suppress
   ambiguous RESPONSE fragments while retaining the current buffer until its
   known START resumes. This can delay a valid current fragment; claiming it
   belongs to the current owner would risk combining different source blocks.

## Verification

- Decoder: START timestamps, explicit zero, omitted time, malformed/duplicate
  fields, END timing and existing binary fixtures.
- Runtime: admitted and queued B before late A final; translation-first START;
  reordered finals; late END preserving B buffers; duplicate/retired START;
  missing START/time, including an end-only A before known B and an anonymous
  translation B; Clear/reconnect; bounded maps/text/order; incomplete Stop tail.
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

A bounded same-source native Volcano follow-up admitted 30 source boundaries and
14 identified final pairs, displayed captions, completed normal Stop and sealed
1,607 trace entries with no recorded loss or write failure. This demonstrates
the actual adapter path. The earlier native excerpt did not prove the late-final
counterexample occurred; neither run establishes frequency, semantic accuracy,
controlled quality comparison or reading-comfort acceptance. Android's live
three-mode video validates the independent-MT candidate at `ed345c26`; the new
Volcano rule has JNI/build coverage without a new Android provider session.
