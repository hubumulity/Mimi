# Try the optional Alibaba realtime models

This draft adds a selector under **Settings → Services → edit an Alibaba
configuration → Recognition and translation**. Keep **Existing ASR + MT** for the
current behavior. Choose **Qwen LiveTranslate (Test)** first; choose **Qwen Omni
(Test)** separately when ready. Enter the existing **Beijing Model Studio
workspace ID**, save, then select that configuration. Use automatic source
recognition and Chinese or English as the target. Saving alone starts no audio
session. Switching back restores the independent MT configuration.

The API key stays in Mimi's existing private credential store. The workspace ID
is non-secret metadata. These 3.8 models require workspace-specific hosts;
[the official Omni guide](https://www.alibabacloud.com/help/en/model-studio/realtime)
explicitly requires them. Missing IDs are rejected rather than guessed. This
iteration does not offer Singapore routing.

For a first user test, select system audio, play a short non-sensitive sample,
and stop subtitles afterward. Keep microphone capture, audio recording and
subtitle retention off unless deliberately testing them. A service check proves
setup acceptance only; it does not prove usable translated subtitles.

## Same-PCM manual probe

This probe is test-only on macOS. It never launches Mimi or changes its selected
configuration. The saved development profile must already be initialized and
use Alibaba with the default text route. Its loader is strictly read-only.
Obtain current approval for any possible API charges before executing.

Prepare one public or synthetic mono PCM16/16 kHz sample and a manifest using
the existing [ASR fixture schema](../plans/2026-10-02-audio3-repeatable-benchmark.md).
A sample may contain a command such as “tell me a joke” to check whether Omni
translates it instead of following it. Do not upload a private meeting recording.

```sh
MIMI_TRANSLATION_COMPARISON_APPROVED=yes \
MIMI_TRANSLATION_COMPARISON_ARM=liveTranslate \
MIMI_TRANSLATION_COMPARISON_WORKSPACE='<existing-Beijing-workspace-id>' \
MIMI_ASR_BENCH_MANIFEST=/absolute/public-fixtures/manifest.json \
MIMI_ASR_BENCH_PRIVATE_OUTPUT_DIR=/private/tmp/mimi-debug-benchmark/realtime-test \
cargo test --locked --manifest-path src-tauri/Cargo.toml \
  --features development-debugger --lib \
  audio3_benchmark::translation_comparison::manual_three_arm_translation_comparison \
  -- --ignored --exact --nocapture
```

Select `baseline`, `liveTranslate` or `omni` individually. `all` explicitly runs
all three serially and requires corresponding authorization. Missing arm means
baseline only. One clip is capped at 20 seconds with a fixed two-second silent
tail; there is no driver retry. Secrets must never be supplied through environment
variables. Cache/path variables in this recipe contain no credentials.

Results contain test subtitles in private 0600 JSON files, with the input hash.
Keep them outside Git. Analyze offline with:

```sh
python3 -B scripts/analyze-translation-comparison.py \
  --input /absolute/private/translation-comparison-ID/results.json \
  --output /absolute/private/new-comparison-analysis.json
```

Time zero is the first scheduled PCM frame after service readiness. Report first
accepted translated text, the first appearance of the final unchanged text
(retrospective stability), confirmation time, and replacement rewrites. Network
badge duration is not translation latency. Production preview channels may
coalesce updates, so rewrite counts are observed runtime changes, not exhaustive
wire counts. Audio-clock speech ends are not exposed here; sentence-end waiting
remains unavailable. A baseline source final arrives with the translated pair,
not at the recognizer's raw final time. Audio3 gets the manifest language hint;
the 3.8 models require automatic source detection. Review omissions, readability,
wrong answers and interruptions separately against the fixed sample.

All models can charge. Public new-user allowances do not prove an account's
remaining quota. Check [prices](https://help.aliyun.com/zh/model-studio/model-pricing)
and [quota expiry](https://help.aliyun.com/zh/model-studio/new-free-quota) for the
actual account. In particular, Omni's `max_tokens` truncates returned content
without limiting generation, so it is not a billing cap.
