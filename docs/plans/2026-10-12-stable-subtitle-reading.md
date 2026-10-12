# Stable translated subtitle reading

## Problem and evidence

The user cannot comfortably read Alibaba subtitles while an unfinished source
sentence keeps changing meaning. The current opening-length delay reduces tiny
first fragments, but the shared ASR + independent MT client still retranslates
the whole cumulative draft, including its incomplete tail. Atomic preview pairs
prevent partial HTTP output from mixing lanes; they do not prevent semantic
rewrites between complete HTTP responses.

The existing Stanford native recordings support this failure, but their Alibaba
and Gemini excerpts contain different speech and are not a controlled quality
comparison. Alibaba's recorded final gaps reach about 24 seconds; one Gemini
phase has no confirmation for 90 seconds. The older final-only replay measured
7.9 seconds median and 59.9 seconds maximum from first recorded draft to display,
not end-to-end speech latency. Geometry counters do not establish comfort.

## Options

| Default | Reading behavior | Latency and limitation |
| --- | --- | --- |
| Current cumulative previews | Early words, then repeated whole-draft MT | Fastest opening; unfinished meaning can reverse |
| Authoritative finals only | No intermediate semantic revisions | Long sentence/provider checkpoints can leave stale or empty display |
| Corroborated complete sentence prefixes | Withhold incomplete tails; translate a complete prefix retained by consecutive ASR observations | Local preview heuristic, not a final or accuracy guarantee |

Use the third option in the shared independent-MT path. A non-resetting six-second
rescue timer supplies a whole draft when no new complete prefix is available. This
bound is a proposed tradeoff requiring continuous-reading validation; rescue
previews may still revise. A new stable prefix takes precedence over rescue.
Track actual HTTP progress so an already translated prefix neither repeatedly
requests MT nor prevents rescue of its newly growing tail. After rescue, the
quiet timer cannot revert to a shorter old prefix.

## Implementation and invariants

Keep the whole latest raw draft in `ASRDraftCommitter` and only byte-offset
metadata for its corroborated complete prefix and actually started MT progress.
Use strong sentence punctuation;
exclude comma/colon fragments, decimal points, domains, initials, abbreviations
and ellipses. Prefix corrections invalidate the affected candidate. Do not cut
the translation separately or consume the raw tail. Each preview contains the
exact source sent to its MT request and that request's complete translation.

Both draft timers and the in-flight successor use the same candidate policy.
Preserve rescue admission across queued work without retaining another draft.
Finals and graceful Stop continue to translate the full source. Source IDs,
preview epochs, content revisions, final ordering, request budgets, cancellation,
Clear and reconnect retain their existing ownership boundaries. Original mode
and same-language passthrough retain realtime raw recognition.
An in-flight result whose exact source was contradicted is discarded without
refunding HTTP work; cosmetic changes keep the existing content fingerprint
rule. HTTP confirmation preserves the numeric ASR layout owner on raw final
lanes as well as the pair, including original-mode confirmation/time metadata.

The policy belongs only in `shared/mimi-runtime`; desktop and Android call it
through their existing shared controller/JNI routes. Integrated Gemini and
Volcano streams retain their provider boundaries. Other direct services require
their own evidence before changing their display policy. No switch, model,
prompt, wire protocol, VAD, recording default, version, tag or release change.

## Verification

1. Synthetic source sequences prove complete-prefix admission, withheld late
   negation/condition tails, correction invalidation, punctuation/Unicode and
   resets, without claiming linguistic certainty.
2. Local HTTP tests prove exact source/result pairing, stable successor admission,
   non-resetting rescue, final preemption, whole Stop tail and request ownership.
3. Shared fixtures run in direct Rust and actual host JNI; Android projection
   covers original, translation and bilingual modes. Run architecture checks,
   canonical checks and Debug/Release JVM tests.
4. The current authorization was used for bounded native attempts and a
   24.0267-second PC caption-only video of the official Stanford clip from
   180 seconds. Actual frames show source, translation and confirmed captions.
   Three Ego baseline attempts had silent sent PCM; Android reached
   MediaProjection but timed out before any scored playback phase. PC used
   Qwen-MT Lite with English recognition; Android used Flash with automatic
   recognition. These attempts do not establish a controlled or cross-device
   comparison. Microphone, session history and audio recording stayed off;
   evidence remains outside Git. Both development apps and preferences were
   restored, Mac output remained muted and formal/release bundles were preserved.
5. Keep PR #261 as a draft. The six-second tradeoff, continuous reading comfort,
   controlled baseline comparison and Android live video remain pending. The PC
   sample ended through a process watchdog, so graceful native Stop-tail
   acceptance also remains separate from the local HTTP regression. Further
   service/capture work uses the remaining current authorization or a new
   explicitly granted scope; confirm valid input and obtain comparable continuous
   reading feedback before accepting the default or merging.
