# Optional Alibaba realtime experiment

Keep the existing Alibaba `qwen-audio-3.0-asr-flash-streaming` → `qwen-mt-lite`
Turbo route as the default. This experiment adds explicit profile choices for
`qwen3.8-livetranslate-flash-realtime` and `qwen3.8-omni-flash-realtime` without
restoring the old translation-mode picker or changing existing profiles.

The shared runtime owns both protocols. The first iteration is audio-only,
16 kHz mono PCM16, text-only output, Beijing workspaces, automatic source
recognition, and original/Chinese/English targets. Independent MT settings stay
saved and return when the user selects ASR + MT again. New models require an
existing Workspace ID and a matching saved Alibaba key; missing IDs fail before
network access. No credentials are copied when changing the mode.

LiveTranslate 3.8 accumulates source `delta`; Omni source drafts replace
`text + stash`. Both accumulate translated `response.text.delta`. Text completion
is a preview until `response.done` confirms completed status. Source/translation
identities must remain linked before accepting a final pair. LiveTranslate ends
with `session.finish` → `session.finished`. Omni uses automatic VAD turns and a
bounded drain after silence; that local drain is not a server session-finished
acknowledgement. The Omni prompt instructs translation only, including commands
and questions in the source, and forbids answers, commentary, and speech.

The macOS manual probe reuses the production facade, the existing public-fixture
manifest validator and PCM pacing. A test-only read-only credential loader
prevents migration, normalization writes, and permission repair. It defaults to
one baseline arm; each new-model arm requires explicit selection and a current
budget gate. It sends at most one 20-second clip plus the same two-second silence
tail. Audio3 idle PCM is disabled for this comparison. No capture or native app
launch occurs. Provider acceptance, wire events, semantic quality and actual
visible subtitles remain separate evidence boundaries.

Current official references:
[LiveTranslate](https://help.aliyun.com/zh/model-studio/qwen3-5-livetranslate-flash-realtime),
[LiveTranslate server events](https://www.alibabacloud.com/help/en/model-studio/live-translator-server-events),
[Omni](https://www.alibabacloud.com/help/en/model-studio/realtime),
[Omni client events](https://www.alibabacloud.com/help/en/model-studio/client-events),
[Omni server events](https://www.alibabacloud.com/help/en/model-studio/server-events).

The draft does not establish better subtitle quality, long-session stability,
Android selection UI, or Windows/Linux native acceptance. It does not authorize
merging, releasing, recording private audio, or additional paid batches.
